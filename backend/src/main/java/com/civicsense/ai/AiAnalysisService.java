package com.civicsense.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.complaint.StatusTransitionPolicy;
import com.civicsense.complaint.StatusTransitionPolicy.Actor;
import com.civicsense.domain.AiAnalysis;
import com.civicsense.domain.AiAnalysisRun;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.Complaint;
import com.civicsense.domain.ComplaintImage;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.ComplaintStatusHistory;
import com.civicsense.domain.UploadStatus;
import com.civicsense.repository.AiAnalysisRepository;
import com.civicsense.repository.AiAnalysisRunRepository;
import com.civicsense.repository.ComplaintImageRepository;
import com.civicsense.repository.ComplaintRepository;
import com.civicsense.repository.ComplaintStatusHistoryRepository;
import com.civicsense.routing.RoutingRequestedEvent;
import com.civicsense.storage.ObjectStorage;
import com.civicsense.storage.PresignedUrl;

/**
 * Orchestrates analysis only after the complaint transaction has committed.
 * Provider failure can never roll back or delete the complaint.
 */
@Service
public class AiAnalysisService {

    private final ComplaintRepository complaints;
    private final AiAnalysisRepository analyses;
    private final AiAnalysisRunRepository runs;
    private final ComplaintImageRepository images;
    private final ComplaintStatusHistoryRepository histories;
    private final AiVisionClient client;
    private final AiResponseValidator validator;
    private final ObjectStorage storage;
    private final StatusTransitionPolicy transitions;
    private final ApplicationEventPublisher events;
    private final String promptVersion;
    private final int maxAttempts;
    private final double confidenceThreshold;
    private final Duration imageUrlTtl;

    public AiAnalysisService(
            ComplaintRepository complaints,
            AiAnalysisRepository analyses,
            AiAnalysisRunRepository runs,
            ComplaintImageRepository images,
            ComplaintStatusHistoryRepository histories,
            AiVisionClient client,
            AiResponseValidator validator,
            ObjectStorage storage,
            StatusTransitionPolicy transitions,
            ApplicationEventPublisher events,
            @Value("${civicsense.ai.prompt-version:civic-v1}") String promptVersion,
            @Value("${civicsense.ai.max-attempts:3}") int maxAttempts,
            @Value("${civicsense.ai.confidence-threshold:0.75}") double confidenceThreshold,
            @Value("${civicsense.upload.view-ttl-seconds:300}") long imageUrlTtlSeconds) {
        this.complaints = complaints;
        this.analyses = analyses;
        this.runs = runs;
        this.images = images;
        this.histories = histories;
        this.client = client;
        this.validator = validator;
        this.storage = storage;
        this.transitions = transitions;
        this.events = events;
        this.promptVersion = promptVersion;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.confidenceThreshold = confidenceThreshold;
        this.imageUrlTtl = Duration.ofSeconds(imageUrlTtlSeconds);
    }

    /** Creates the PENDING analysis row inside the complaint transaction. */
    public AiAnalysis createPending(Complaint complaint) {
        AiAnalysis analysis = new AiAnalysis();
        analysis.setComplaint(complaint);
        analysis.setStatus(AiAnalysisStatus.PENDING);
        analysis.setSource(com.civicsense.domain.AiAnalysisSource.AI);
        analysis.setProvider(client.providerName());
        analysis.setPromptVersion(promptVersion);
        analysis.setRequiresHumanReview(false);
        analysis.setAttemptNumber(0);
        analysis.setCreatedAt(Instant.now());
        return analyses.save(analysis);
    }

    /** Called asynchronously after the complaint transaction commits. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID complaintId) {
        Complaint complaint = complaints.findById(complaintId).orElse(null);
        if (complaint == null) {
            return;
        }
        AiAnalysis analysis = analyses
                .findFirstByComplaintIdAndStatusInOrderByCreatedAtDesc(complaintId,
                        List.of(AiAnalysisStatus.PENDING))
                .orElse(null);
        if (analysis == null || !acquireLock(analysis)) {
            return;
        }

        analysis.setStatus(AiAnalysisStatus.PROCESSING);
        analyses.save(analysis);

        try {
            AiAnalysisRequest request = buildRequest(complaint);
            AiProviderResponse raw = callWithRetries(request, analysis);
            // Raw output is retained even when schema validation fails.
            analysis.setRawResponse(truncate(raw.rawJson(), 20000));
            analyses.save(analysis);
            AiAnalysisResult result = validator.validate(raw, analysis.getAttemptNumber(),
                    confidenceThreshold);
            persistSuccess(analysis, complaint, result);
        } catch (AiProviderException exception) {
            persistFailure(analysis, complaint,
                    exception.isTimeout() ? AiAnalysisStatus.TIMEOUT : AiAnalysisStatus.FAILED,
                    exception.getMessage());
        } catch (InvalidAiResponseException exception) {
            persistFailure(analysis, complaint, AiAnalysisStatus.INVALID_SCHEMA,
                    exception.getMessage());
        } catch (RuntimeException exception) {
            persistFailure(analysis, complaint, AiAnalysisStatus.FAILED,
                    "AI analysis failed: " + exception.getMessage());
        }
    }

    private boolean acquireLock(AiAnalysis analysis) {
        UUID complaintId = analysis.getComplaint().getId();
        if (runs.existsById(complaintId)) {
            return false;
        }
        try {
            AiAnalysisRun run = new AiAnalysisRun();
            run.setComplaintId(complaintId);
            run.setAnalysis(analysis);
            runs.saveAndFlush(run);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private AiAnalysisRequest buildRequest(Complaint complaint) {
        List<ComplaintImage> verified = images
                .findAllByComplaintIdAndUploadStatusOrderByCreatedAtAsc(
                        complaint.getId(), UploadStatus.VERIFIED);
        List<AiAnalysisRequest.ImageReference> references = verified.stream()
                .map(image -> {
                    PresignedUrl url = storage.presignGet(image.getStorageKey(), imageUrlTtl);
                    return new AiAnalysisRequest.ImageReference(image.getId().toString(), url.url());
                })
                .toList();
        String addressHint = complaint.getAddressLine() == null ? complaint.getCity()
                : complaint.getAddressLine() + ", " + (complaint.getCity() == null ? "" : complaint.getCity());
        return new AiAnalysisRequest(references, complaint.getDescription(), addressHint, promptVersion);
    }

    private AiProviderResponse callWithRetries(AiAnalysisRequest request, AiAnalysis analysis)
            throws AiProviderException {
        AiProviderException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            analysis.setAttemptNumber(attempt);
            try {
                return client.analyze(request);
            } catch (AiProviderException exception) {
                last = exception;
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AiProviderException("AI analysis interrupted", true);
                    }
                }
            }
        }
        throw last;
    }

    private void persistSuccess(AiAnalysis analysis, Complaint complaint, AiAnalysisResult result) {
        analysis.setStatus(AiAnalysisStatus.SUCCESS);
        analysis.setProvider(client.providerName());
        analysis.setRawResponse(result.rawJson());
        analysis.setParsedResult(result.rawJson());
        analysis.setIsCivicIssue(result.civicIssue());
        analysis.setCategoryCode(result.categoryCode());
        analysis.setSubcategoryCode(result.subcategoryCode());
        analysis.setSeverity(result.severity());
        analysis.setDescription(result.description());
        analysis.setVisibleDamage(result.visibleDamage());
        analysis.setSafetyRisk(result.safetyRisk());
        analysis.setIsBlurry(result.blurry());
        analysis.setRequiresAdditionalInfo(result.requiresAdditionalInfo());
        analysis.setConfidence(result.confidence());
        // Advisory hint only — never used by the routing engine (Phase 6).
        analysis.setRecommendedDepartmentHint(result.recommendedDepartmentHint());
        analysis.setRequiresHumanReview(result.requiresHumanReview());
        analysis.setLatencyMs(result.latencyMs());
        analysis.setAttemptNumber(result.attemptNumber());
        analysis.setErrorMessage(null);
        analyses.save(analysis);

        complaint.setAiConfidence(result.confidence());
        complaint.setAiRequiresReview(result.requiresHumanReview());
        changeStatus(complaint, ComplaintStatus.AI_ANALYSIS_COMPLETED);
        // Routing runs only after this REQUIRES_NEW transaction commits.
        events.publishEvent(new RoutingRequestedEvent(complaint.getId()));
    }

    private void persistFailure(AiAnalysis analysis, Complaint complaint,
                                AiAnalysisStatus status, String error) {
        analysis.setStatus(status);
        analysis.setErrorMessage(truncate(error, 2000));
        analyses.save(analysis);
        changeStatus(complaint, ComplaintStatus.AI_ANALYSIS_FAILED);
        events.publishEvent(new RoutingRequestedEvent(complaint.getId()));
    }

    private void changeStatus(Complaint complaint, ComplaintStatus target) {
        transitions.validate(complaint.getStatus(), target, Actor.SYSTEM);
        ComplaintStatus from = complaint.getStatus();
        complaint.setStatus(target);
        complaints.save(complaint);

        ComplaintStatusHistory history = new ComplaintStatusHistory();
        history.setComplaint(complaint);
        history.setFromStatus(from);
        history.setToStatus(target);
        history.setActorRole("SYSTEM");
        history.setCreatedAt(Instant.now());
        histories.save(history);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), max));
    }
}
