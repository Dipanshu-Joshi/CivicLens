package com.civicsense.complaint;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.ai.AiAnalysisService;
import com.civicsense.ai.ComplaintSubmittedEvent;
import com.civicsense.audit.Audited;
import com.civicsense.complaint.dto.ComplaintDtos.ActivityEntry;
import com.civicsense.complaint.dto.ComplaintDtos.AiAnalysisResponse;
import com.civicsense.complaint.dto.ComplaintDtos.AssignRequest;
import com.civicsense.complaint.dto.ComplaintDtos.AssignmentEntry;
import com.civicsense.complaint.dto.ComplaintDtos.AnalyticsResponse;
import com.civicsense.complaint.dto.ComplaintDtos.CommentRequest;
import com.civicsense.complaint.dto.ComplaintDtos.CategoryCount;
import com.civicsense.complaint.dto.ComplaintDtos.CommentResponse;
import com.civicsense.complaint.dto.ComplaintDtos.ComplaintDetail;
import com.civicsense.complaint.dto.ComplaintDtos.ComplaintImageResponse;
import com.civicsense.complaint.dto.ComplaintDtos.ComplaintSummary;
import com.civicsense.complaint.dto.ComplaintDtos.FeedbackRequest;
import com.civicsense.complaint.dto.ComplaintDtos.FeedbackResponse;
import com.civicsense.complaint.dto.ComplaintDtos.AiAnalysisHistoryEntry;
import com.civicsense.complaint.dto.ComplaintDtos.AiAnalysisHistoryResponse;
import com.civicsense.complaint.dto.ComplaintDtos.NoteRequest;
import com.civicsense.complaint.dto.ComplaintDtos.NoteResponse;
import com.civicsense.complaint.dto.ComplaintDtos.ReassignDecisionRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReassignRequestRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReassignRequestResponse;
import com.civicsense.complaint.dto.ComplaintDtos.ReopenRequest;
import com.civicsense.complaint.dto.ComplaintDtos.StatusEntry;
import com.civicsense.complaint.dto.ComplaintDtos.EvidenceRequest;
import com.civicsense.complaint.dto.ComplaintDtos.PagedResponse;
import com.civicsense.complaint.dto.ComplaintDtos.ResolveRequest;
import com.civicsense.complaint.dto.ComplaintDtos.StatusUpdateRequest;
import com.civicsense.complaint.dto.ComplaintDtos.SubmitComplaintRequest;
import com.civicsense.complaint.dto.ComplaintDtos.TrendPoint;
import com.civicsense.complaint.dto.ComplaintDtos.WardCount;
import com.civicsense.complaint.exception.ComplaintExceptions.CategoryNotFoundException;
import com.civicsense.complaint.exception.ComplaintExceptions.ComplaintNotFoundException;
import com.civicsense.complaint.exception.ComplaintExceptions.DepartmentNotEligibleException;
import com.civicsense.complaint.exception.ComplaintExceptions.EvidenceRequiredException;
import com.civicsense.complaint.exception.ComplaintExceptions.NotOwnerException;
import com.civicsense.complaint.exception.ComplaintExceptions.ReasonRequiredException;
import com.civicsense.complaint.exception.ComplaintExceptions.ReassignmentNotAllowedException;
import com.civicsense.complaint.StatusTransitionPolicy.Actor;
import com.civicsense.domain.AiAnalysis;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.AssignmentType;
import com.civicsense.domain.Complaint;
import com.civicsense.domain.ComplaintAssignment;
import com.civicsense.domain.ComplaintComment;
import com.civicsense.domain.ComplaintFeedback;
import com.civicsense.domain.ComplaintImage;
import com.civicsense.domain.ComplaintInternalNote;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.ComplaintStatusHistory;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.IssueCategory;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.VerificationStatus;
import com.civicsense.domain.ReassignmentRequest;
import com.civicsense.domain.ReassignmentStatus;
import com.civicsense.domain.Role;
import com.civicsense.domain.RoutingDecision;
import com.civicsense.domain.Severity;
import com.civicsense.domain.UploadStatus;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentAdmin;
import com.civicsense.identity.DepartmentAdminRepository;
import com.civicsense.identity.DepartmentRepository;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;
import com.civicsense.notification.NotificationService;
import com.civicsense.repository.AiAnalysisRepository;
import com.civicsense.repository.ComplaintAssignmentRepository;
import com.civicsense.repository.ComplaintCommentRepository;
import com.civicsense.repository.ComplaintFeedbackRepository;
import com.civicsense.repository.ComplaintImageRepository;
import com.civicsense.repository.ComplaintInternalNoteRepository;
import com.civicsense.repository.ComplaintRepository;
import com.civicsense.repository.ComplaintStatusHistoryRepository;
import com.civicsense.repository.IssueCategoryRepository;
import com.civicsense.repository.ReassignmentRequestRepository;
import com.civicsense.repository.RoutingDecisionRepository;
import com.civicsense.routing.RoutingDecisionMapper;
import com.civicsense.routing.RoutingRequestedEvent;
import com.civicsense.security.AuthenticatedUser;
import com.civicsense.upload.UploadService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Complaint lifecycle. Every department-scoped read AND write is constrained
 * by the department id from the JWT; ids supplied by a client are never used
 * to widen scope. Cross-department resources return 404, not 403.
 */
@Service
public class ComplaintService {

    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository history;
    private final ComplaintCommentRepository comments;
    private final ComplaintInternalNoteRepository notes;
    private final ComplaintAssignmentRepository assignments;
    private final ComplaintImageRepository images;
    private final ComplaintFeedbackRepository feedback;
    private final ReassignmentRequestRepository reassignmentRequests;
    private final RoutingDecisionRepository routingDecisions;
    private final RoutingDecisionMapper routingDecisionMapper;
    private final IssueCategoryRepository categories;
    private final DepartmentRepository departments;
    private final DepartmentAdminRepository departmentAdmins;
    private final UserRepository users;
    private final NotificationService notifications;
    private final StatusTransitionPolicy policy;
    private final UploadService uploadService;
    private final AiAnalysisService aiAnalysisService;
    private final AiAnalysisRepository aiAnalyses;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Map<Severity, Duration> slas;

    public ComplaintService(
            ComplaintRepository complaints,
            ComplaintStatusHistoryRepository history,
            ComplaintCommentRepository comments,
            ComplaintInternalNoteRepository notes,
            ComplaintAssignmentRepository assignments,
            ComplaintImageRepository images,
            ComplaintFeedbackRepository feedback,
            ReassignmentRequestRepository reassignmentRequests,
            RoutingDecisionRepository routingDecisions,
            RoutingDecisionMapper routingDecisionMapper,
            IssueCategoryRepository categories,
            DepartmentRepository departments,
            DepartmentAdminRepository departmentAdmins,
            UserRepository users,
            NotificationService notifications,
            StatusTransitionPolicy policy,
            UploadService uploadService,
            AiAnalysisService aiAnalysisService,
            AiAnalysisRepository aiAnalyses,
            ApplicationEventPublisher events,
            Clock clock,
            EntityManager entityManager,
            ObjectMapper objectMapper,
            @Value("${civicsense.sla.critical-hours:4}") long criticalHours,
            @Value("${civicsense.sla.high-hours:24}") long highHours,
            @Value("${civicsense.sla.medium-hours:72}") long mediumHours,
            @Value("${civicsense.sla.low-hours:168}") long lowHours) {
        this.complaints = complaints;
        this.history = history;
        this.comments = comments;
        this.notes = notes;
        this.assignments = assignments;
        this.images = images;
        this.feedback = feedback;
        this.reassignmentRequests = reassignmentRequests;
        this.routingDecisions = routingDecisions;
        this.routingDecisionMapper = routingDecisionMapper;
        this.categories = categories;
        this.departments = departments;
        this.departmentAdmins = departmentAdmins;
        this.users = users;
        this.notifications = notifications;
        this.policy = policy;
        this.uploadService = uploadService;
        this.aiAnalysisService = aiAnalysisService;
        this.aiAnalyses = aiAnalyses;
        this.events = events;
        this.clock = clock;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.slas = new EnumMap<>(Severity.class);
        this.slas.put(Severity.CRITICAL, Duration.ofHours(criticalHours));
        this.slas.put(Severity.HIGH, Duration.ofHours(highHours));
        this.slas.put(Severity.MEDIUM, Duration.ofHours(mediumHours));
        this.slas.put(Severity.LOW, Duration.ofHours(lowHours));
    }

    // -----------------------------------------------------------------------
    // Citizen surface
    // -----------------------------------------------------------------------

    @Transactional
    @Audited(action = "COMPLAINT_SUBMIT", entityType = "complaint")
    public ComplaintDetail submit(AuthenticatedUser principal, SubmitComplaintRequest request) {
        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(nextComplaintNumber());
        complaint.setCitizen(users.getReferenceById(principal.id()));
        complaint.setDescription(request.description().trim());
        complaint.setAdditionalInfo(request.additionalInfo());
        complaint.setLatitude(request.latitude());
        complaint.setLongitude(request.longitude());
        complaint.setLocationWkt("POINT(" + request.longitude() + " " + request.latitude() + ")");
        complaint.setLocationSource(request.locationSource());
        complaint.setAddressLine(request.addressLine());
        complaint.setCity(request.city());
        complaint.setState(request.state());
        complaint.setPostalCode(request.postalCode());
        complaint.setCountry(request.country());
        complaint.setWard(request.ward());
        complaint.setZone(request.zone());
        complaint.setSeverity(Severity.MEDIUM);
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setAiRequiresReview(false);
        complaint.setResolutionDueAt(clock.instant().plus(slas.get(Severity.MEDIUM)));
        complaint.setCategory(resolveCategory(request.categoryCode()));
        complaint.setSubcategory(resolveCategory(request.subcategoryCode()));
        complaint.setCreatedAt(clock.instant());
        complaint.setUpdatedAt(clock.instant());

        // Staged uploads are validated before any complaint row is written. Ownership,
        // confirmation state, and count are enforced by UploadService.
        List<ComplaintImage> attachments = uploadService.attachableImages(principal, request.imageIds());
        boolean aiEnabled = !attachments.isEmpty();

        if (aiEnabled) {
            complaint.setStatus(ComplaintStatus.AI_ANALYSIS_PENDING);
        }

        Complaint saved = complaints.save(complaint);
        recordHistory(saved, null, ComplaintStatus.SUBMITTED, Actor.CITIZEN, principal, null);
        if (aiEnabled) {
            recordHistory(saved, ComplaintStatus.SUBMITTED, ComplaintStatus.AI_ANALYSIS_PENDING,
                    Actor.SYSTEM, principal, "Images attached; AI analysis queued");
            for (ComplaintImage image : attachments) {
                image.setComplaint(saved);
                image.setImageKind(com.civicsense.domain.ImageKind.CITIZEN);
                images.save(image);
            }
            aiAnalysisService.createPending(saved);
        }
        notifySuperAdmins(saved, NotificationType.COMPLAINT_SUBMITTED,
                "New complaint " + saved.getComplaintNumber(), "A citizen submitted a new complaint.");
        notify(principal.id(), saved.getId(), NotificationType.COMPLAINT_SUBMITTED,
                "Complaint " + saved.getComplaintNumber() + " submitted",
                "Your complaint has been recorded. Analysis and assignment are in progress.");
        ComplaintDetail result = detail(saved, principal);
        if (aiEnabled) {
            events.publishEvent(new ComplaintSubmittedEvent(saved.getId()));
        } else {
            // No image means no AI result. Routing still records a decision and
            // leaves the complaint in human-review state rather than guessing.
            events.publishEvent(new RoutingRequestedEvent(saved.getId()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ComplaintSummary> myComplaints(AuthenticatedUser principal, ComplaintStatus status,
            UUID categoryId, Instant from, Instant to, String search) {
        return complaints.findCitizenComplaints(principal.id(), status, categoryId, from, to, search)
                .stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintDetail myComplaint(UUID id, AuthenticatedUser principal) {
        Complaint complaint = complaints.findByIdAndCitizenId(id, principal.id())
                .orElseThrow(ComplaintNotFoundException::new);
        return detail(complaint, principal);
    }

    @Transactional(readOnly = true)
    public List<StatusEntry> timeline(UUID id, AuthenticatedUser principal) {
        Complaint complaint = accessibleComplaint(id, principal);
        return history.findAllByComplaintIdOrderByCreatedAtAsc(complaint.getId()).stream()
                .map(entry -> new StatusEntry(
                        entry.getFromStatus(), entry.getToStatus(), entry.getActorRole(),
                        entry.getReason(), entry.getCreatedAt()))
                .toList();
    }

    @Transactional
    public CommentResponse addComment(UUID id, CommentRequest request, AuthenticatedUser principal) {
        Complaint complaint = accessibleComplaint(id, principal);
        ComplaintComment comment = new ComplaintComment();
        comment.setComplaint(complaint);
        comment.setAuthor(users.getReferenceById(principal.id()));
        comment.setAuthorRole(principal.role().name());
        comment.setBody(request.body().trim());
        comment.setCreatedAt(clock.instant());
        comment.setUpdatedAt(clock.instant());
        ComplaintComment saved = comments.save(comment);
        return new CommentResponse(saved.getId(), principal.email(), saved.getAuthorRole(),
                saved.getBody(), saved.getCreatedAt());
    }

    @Transactional
    @Audited(action = "COMPLAINT_INFO_RESPONSE", entityType = "complaint")
    public void respondInfo(UUID id, CommentRequest request, AuthenticatedUser principal) {
        Complaint complaint = complaints.findByIdAndCitizenId(id, principal.id())
                .orElseThrow(ComplaintNotFoundException::new);
        addComment(id, request, principal);
        if (complaint.getRelatedDepartment() != null) {
            notifyDepartmentAdmins(complaint.getRelatedDepartment(), complaint.getId(),
                    NotificationType.INFO_RESPONDED, "Citizen responded to information request",
                    complaint.getComplaintNumber() + " has a new citizen response.");
        } else {
            notifySuperAdmins(complaint, NotificationType.INFO_RESPONDED,
                    "Citizen responded to information request", complaint.getComplaintNumber());
        }
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> comments(UUID id, AuthenticatedUser principal) {
        Complaint complaint = accessibleComplaint(id, principal);
        return comments.findAllByComplaintIdOrderByCreatedAtAsc(complaint.getId()).stream()
                .map(comment -> new CommentResponse(
                        comment.getId(), comment.getAuthor().getFullName(),
                        comment.getAuthorRole(), comment.getBody(), comment.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void reopen(UUID id, ReopenRequest request, AuthenticatedUser principal) {
        Complaint complaint = complaints.findByIdAndCitizenId(id, principal.id())
                .orElseThrow(ComplaintNotFoundException::new);
        changeStatus(complaint, ComplaintStatus.REOPENED, request.reason(),
                Actor.CITIZEN, principal);
        notifyReopenedRecipients(complaint, request.reason());
    }

    private void notifyReopenedRecipients(Complaint complaint, String reason) {
        String body = complaint.getComplaintNumber() + " was reopened by the citizen. " + reason;
        if (complaint.getRelatedDepartment() != null) {
            notifyDepartmentAdmins(complaint.getRelatedDepartment(), complaint.getId(),
                    NotificationType.COMPLAINT_REOPENED, "Complaint reopened", body);
        }
        notifySuperAdmins(complaint, NotificationType.COMPLAINT_REOPENED, "Complaint reopened", body);
    }

    @Transactional
    public void feedback(UUID id, FeedbackRequest request, AuthenticatedUser principal) {
        Complaint complaint = complaints.findByIdAndCitizenId(id, principal.id())
                .orElseThrow(ComplaintNotFoundException::new);
        if (feedback.findByComplaintId(id).isPresent()) {
            throw new com.civicsense.complaint.exception.ComplaintExceptions.DuplicateReferenceException(
                    "Feedback has already been submitted for this complaint");
        }
        if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
            throw new ReassignmentNotAllowedException("Feedback is only accepted for RESOLVED complaints");
        }
        ComplaintFeedback saved = new ComplaintFeedback();
        saved.setComplaint(complaint);
        saved.setCitizen(users.getReferenceById(principal.id()));
        saved.setRating(request.rating());
        saved.setComment(request.comment());
        saved.setResolutionConfirmed(request.resolutionConfirmed());
        saved.setDisputeReason(request.disputeReason());
        saved.setCreatedAt(clock.instant());
        saved.setUpdatedAt(clock.instant());
        feedback.save(saved);
        String feedbackSummary = "Citizen feedback received for " + complaint.getComplaintNumber()
                + ": rating " + request.rating()
                + (Boolean.TRUE.equals(request.resolutionConfirmed()) ? ", resolution confirmed." : ", resolution disputed.");
        if (complaint.getRelatedDepartment() != null) {
            notifyDepartmentAdmins(complaint.getRelatedDepartment(), complaint.getId(),
                    NotificationType.FEEDBACK_RECEIVED, "Citizen feedback received", feedbackSummary);
        }
        notifySuperAdmins(complaint, NotificationType.FEEDBACK_RECEIVED,
                "Citizen feedback received", feedbackSummary);
        if (Boolean.TRUE.equals(request.resolutionConfirmed())) {
            changeStatus(complaint, ComplaintStatus.CLOSED, null, Actor.CITIZEN, principal);
        } else {
            if (request.disputeReason() == null || request.disputeReason().isBlank()) {
                throw new ReassignmentNotAllowedException("A dispute reason is required");
            }
            changeStatus(complaint, ComplaintStatus.REOPENED, request.disputeReason(),
                    Actor.CITIZEN, principal);
            notifyReopenedRecipients(complaint, request.disputeReason());
        }
    }

    // -----------------------------------------------------------------------
    // Department surface — every method is scoped to the JWT department
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ComplaintSummary> departmentComplaints(AuthenticatedUser principal,
            ComplaintStatus status, Instant from, Instant to) {
        requireVerifiedDepartment(principal);
        return complaints.findDepartmentComplaints(principal.departmentId(), status, from, to)
                .stream().map(this::summary).toList();
    }

    /**
     * Rich Phase 7 queue. Every predicate is applied in the repository layer with
     * the department id taken from the JWT, so filters cannot widen scope.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ComplaintSummary> departmentComplaintQueue(
            AuthenticatedUser principal, int page, int size, String sort,
            List<ComplaintStatus> statuses, String categoryCode, String ward, String zone,
            Severity severity, String search, Boolean overdue, Boolean aiAssigned,
            Instant from, Instant to) {
        requireVerifiedDepartment(principal);
        return pagedQueue(page, size, sort, queueSpecification(
                principal.departmentId(), statuses, categoryCode, ward, zone, severity, search,
                overdue, aiAssigned, from, to, null, null));
    }

    /**
     * Platform-wide Phase 8 queue. Reuses the exact Specification used by the
     * department queue and adds optional cross-department filters; the
     * department-scoped method above keeps its original behaviour.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ComplaintSummary> platformComplaintQueue(
            int page, int size, String sort,
            List<ComplaintStatus> statuses, String categoryCode, String ward, String zone,
            Severity severity, String search, Boolean overdue, Boolean aiAssigned,
            Instant from, Instant to, UUID departmentId, String routingOutcome, String aiStatus) {
        return pagedQueue(page, size, sort, queueSpecification(departmentId, statuses,
                categoryCode, ward, zone, severity, search, overdue, aiAssigned, from, to,
                routingOutcome, aiStatus));
    }

    /** Append-only AI/override history for a single complaint. */
    @Transactional(readOnly = true)
    public AiAnalysisHistoryResponse aiHistory(UUID complaintId) {
        requireComplaint(complaintId);
        List<AiAnalysisHistoryEntry> entries = aiAnalyses
                .findAllByComplaintIdOrderByCreatedAtDesc(complaintId).stream()
                .sorted(Comparator.comparing(AiAnalysis::getCreatedAt,
                        Comparator.nullsLast(Comparator.<Instant>reverseOrder())))
                .map(this::toHistoryEntry)
                .toList();
        return new AiAnalysisHistoryResponse(entries);
    }

    private PagedResponse<ComplaintSummary> pagedQueue(int page, int size, String sort,
            Specification<Complaint> specification) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Complaint> result = complaints.findAll(specification,
                PageRequest.of(safePage, safeSize, parseQueueSort(sort)));
        return new PagedResponse<>(result.getContent().stream().map(this::summary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private Specification<Complaint> queueSpecification(
            UUID departmentId, List<ComplaintStatus> statuses, String categoryCode, String ward,
            String zone, Severity severity, String search, Boolean overdue, Boolean aiAssigned,
            Instant from, Instant to, String routingOutcome, String aiStatus) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (departmentId != null) {
                predicates.add(builder.equal(root.get("relatedDepartment").get("id"), departmentId));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (hasText(categoryCode)) {
                predicates.add(builder.equal(
                        root.join("category", JoinType.LEFT).get("code"), categoryCode.trim()));
            }
            if (hasText(ward)) {
                predicates.add(builder.equal(root.get("ward"), ward.trim()));
            }
            if (hasText(zone)) {
                predicates.add(builder.equal(root.get("zone"), zone.trim()));
            }
            if (severity != null) {
                predicates.add(builder.equal(root.get("severity"), severity));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("complaintNumber")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern),
                        builder.like(builder.lower(root.get("addressLine")), pattern),
                        builder.like(builder.lower(root.get("ward")), pattern),
                        builder.like(builder.lower(root.get("city")), pattern)));
            }
            if (overdue != null) {
                Instant now = clock.instant();
                Predicate isOverdue = builder.and(
                        root.get("status").in(activeStatuses()),
                        builder.isNotNull(root.get("resolutionDueAt")),
                        builder.lessThan(root.get("resolutionDueAt"), now));
                if (Boolean.TRUE.equals(overdue)) {
                    predicates.add(isOverdue);
                } else {
                    predicates.add(builder.or(
                            builder.not(root.get("status").in(activeStatuses())),
                            builder.isNull(root.get("resolutionDueAt")),
                            builder.greaterThanOrEqualTo(root.get("resolutionDueAt"), now)));
                }
            }
            if (aiAssigned != null) {
                var subquery = query.subquery(UUID.class);
                Root<ComplaintAssignment> assignment = subquery.from(ComplaintAssignment.class);
                subquery.select(assignment.get("id")).where(
                        builder.equal(assignment.get("complaint").get("id"), root.get("id")),
                        builder.equal(assignment.get("assignmentType"), AssignmentType.AUTO),
                        builder.isTrue(assignment.get("active")));
                predicates.add(Boolean.TRUE.equals(aiAssigned)
                        ? builder.exists(subquery) : builder.not(builder.exists(subquery)));
            }
            if (hasText(routingOutcome)) {
                var subquery = query.subquery(UUID.class);
                Root<RoutingDecision> decision = subquery.from(RoutingDecision.class);
                subquery.select(decision.get("id")).where(
                        builder.equal(decision.get("complaint").get("id"), root.get("id")),
                        builder.isTrue(decision.get("current")),
                        builder.equal(builder.upper(decision.<String>get("outcome")),
                                routingOutcome.trim().toUpperCase(Locale.ROOT)));
                predicates.add(builder.exists(subquery));
            }
            if (hasText(aiStatus)) {
                AiAnalysisStatus parsedStatus = null;
                try {
                    parsedStatus = AiAnalysisStatus.valueOf(aiStatus.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // Unknown status simply yields no complaints.
                }
                if (parsedStatus == null) {
                    predicates.add(builder.disjunction());
                } else {
                    var subquery = query.subquery(UUID.class);
                    Root<AiAnalysis> analysis = subquery.from(AiAnalysis.class);
                    subquery.select(analysis.get("id")).where(
                            builder.equal(analysis.get("complaint").get("id"), root.get("id")),
                            builder.equal(analysis.get("status"), parsedStatus));
                    predicates.add(builder.exists(subquery));
                }
            }
            query.distinct(true);
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public ComplaintDetail departmentComplaint(UUID id, AuthenticatedUser principal) {
        requireVerifiedDepartment(principal);
        Complaint complaint = complaints.findByIdAndRelatedDepartmentId(id, principal.departmentId())
                .orElseThrow(ComplaintNotFoundException::new);
        return detail(complaint, principal);
    }

    @Transactional
    @Audited(action = "COMPLAINT_ACCEPT", entityType = "complaint")
    public void accept(UUID id, AuthenticatedUser principal) {
        Complaint complaint = departmentComplaintEntity(id, principal);
        changeStatus(complaint, ComplaintStatus.ACKNOWLEDGED, null, Actor.DEPT_ADMIN, principal);
    }

    @Transactional
    @Audited(action = "COMPLAINT_REJECT", entityType = "complaint")
    public void reject(UUID id, String reason, AuthenticatedUser principal) {
        Complaint complaint = departmentComplaintEntity(id, principal);
        changeStatus(complaint, ComplaintStatus.REJECTED, reason, Actor.DEPT_ADMIN, principal);
    }

    @Transactional
    @Audited(action = "COMPLAINT_STATUS_CHANGE", entityType = "complaint")
    public void patchStatus(UUID id, StatusUpdateRequest request, AuthenticatedUser principal) {
        Complaint complaint = departmentComplaintEntity(id, principal);
        changeStatus(complaint, request.status(), request.reason(), Actor.DEPT_ADMIN, principal);
    }

    @Transactional
    @Audited(action = "COMPLAINT_RESOLVE", entityType = "complaint")
    public void resolve(UUID id, ResolveRequest request, AuthenticatedUser principal) {
        Complaint complaint = departmentComplaintEntity(id, principal);
        changeStatus(complaint, ComplaintStatus.RESOLVED, request.summary(), Actor.DEPT_ADMIN, principal);
    }

    @Transactional
    @Audited(action = "COMPLAINT_EVIDENCE_ATTACH", entityType = "complaint")
    public void attachEvidence(UUID id, EvidenceRequest request, AuthenticatedUser principal) {
        Complaint complaint = departmentComplaintEntity(id, principal);
        uploadService.attachVerifiedImage(principal, request.imageId(), complaint,
                Set.of(ImageKind.BEFORE, ImageKind.AFTER, ImageKind.EVIDENCE));
        notify(complaint.getCitizen().getId(), complaint.getId(), NotificationType.EVIDENCE_ATTACHED,
                "New evidence attached",
                complaint.getComplaintNumber() + " has a new department evidence image.");
    }

    @Transactional
    @Audited(action = "COMPLAINT_INFO_REQUEST", entityType = "complaint")
    public void requestInfo(UUID id, CommentRequest request, AuthenticatedUser principal) {
        Complaint complaint = departmentComplaintEntity(id, principal);
        addComment(id, request, principal);
        notify(complaint.getCitizen().getId(), complaint.getId(), NotificationType.INFO_REQUESTED,
                "More information requested", request.body());
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> notes(UUID id, AuthenticatedUser principal) {
        Complaint complaint = departmentComplaintEntity(id, principal);
        return notes.findAllByComplaintIdAndDepartmentIdOrderByCreatedAtAsc(
                        complaint.getId(), principal.departmentId())
                .stream()
                .map(note -> new NoteResponse(note.getId(), note.getAuthor().getFullName(),
                        note.getBody(), note.getCreatedAt()))
                .toList();
    }

    @Transactional
    @Audited(action = "INTERNAL_NOTE_CREATE", entityType = "complaint")
    public NoteResponse addNote(UUID id, NoteRequest request, AuthenticatedUser principal) {
        Complaint complaint = departmentComplaintEntity(id, principal);
        ComplaintInternalNote note = new ComplaintInternalNote();
        note.setComplaint(complaint);
        note.setDepartment(departments.getReferenceById(principal.departmentId()));
        note.setAuthor(users.getReferenceById(principal.id()));
        note.setBody(request.body().trim());
        note.setCreatedAt(clock.instant());
        note.setUpdatedAt(clock.instant());
        ComplaintInternalNote saved = notes.save(note);
        return new NoteResponse(saved.getId(), principal.email(), saved.getBody(), saved.getCreatedAt());
    }

    @Transactional
    @Audited(action = "REASSIGNMENT_REQUEST", entityType = "complaint")
    public void requestReassignment(UUID id, ReassignRequestRequest request, AuthenticatedUser principal) {
        Complaint complaint = departmentComplaintEntity(id, principal);
        Department target = departments.findById(request.toDepartmentId())
                .orElseThrow(DepartmentNotEligibleException::new);
        if (target.getVerificationStatus() != VerificationStatus.VERIFIED
                || !target.isActive()
                || !target.isAcceptsComplaints()) {
            throw new DepartmentNotEligibleException();
        }
        if (complaint.getStatus() != ComplaintStatus.ASSIGNED) {
            throw new ReassignmentNotAllowedException("Only ASSIGNED complaints can be reassigned");
        }
        ReassignmentRequest entity = new ReassignmentRequest();
        entity.setComplaint(complaint);
        entity.setFromDepartment(departments.getReferenceById(principal.departmentId()));
        entity.setToDepartment(target);
        entity.setRequestedBy(users.getReferenceById(principal.id()));
        entity.setReason(request.reason());
        entity.setStatus(ReassignmentStatus.PENDING);
        entity.setCreatedAt(clock.instant());
        reassignmentRequests.save(entity);
        notifySuperAdmins(complaint, NotificationType.REASSIGNMENT_REQUESTED,
                "Reassignment requested", request.reason());
    }

    @Transactional(readOnly = true)
    public List<ActivityEntry> departmentActivity(AuthenticatedUser principal, int limit) {
        requireVerifiedDepartment(principal);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return history.findRecentByDepartmentId(principal.departmentId(), PageRequest.of(0, safeLimit))
                .stream()
                .map(entry -> new ActivityEntry(
                        entry.getComplaint().getId(),
                        entry.getComplaint().getComplaintNumber(),
                        entry.getFromStatus(),
                        entry.getToStatus(),
                        entry.getActorRole(),
                        entry.getReason(),
                        entry.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse departmentAnalytics(AuthenticatedUser principal) {
        return departmentAnalytics(principal, 30);
    }

    /** All values are derived from persisted rows; no demo or hardcoded metrics. */
    @Transactional(readOnly = true)
    public AnalyticsResponse departmentAnalytics(AuthenticatedUser principal, int periodDays) {
        requireVerifiedDepartment(principal);
        UUID departmentId = principal.departmentId();
        int days = normalizePeriodDays(periodDays);
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofDays(days));
        List<Complaint> all = complaints.findAllByRelatedDepartmentIdOrderByCreatedAtDesc(departmentId);
        List<Complaint> resolved = all.stream()
                .filter(complaint -> complaint.getResolvedAt() != null)
                .toList();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Complaint complaint : all) {
            byStatus.merge(complaint.getStatus().name(), 1L, Long::sum);
        }
        long overdue = all.stream()
                .filter(complaint -> activeStatuses().contains(complaint.getStatus()))
                .filter(complaint -> complaint.getResolutionDueAt() != null
                        && complaint.getResolutionDueAt().isBefore(now))
                .count();
        long pending = all.stream().filter(complaint -> pendingStatuses().contains(complaint.getStatus())).count();
        long accepted = all.stream().filter(complaint -> acceptedStatuses().contains(complaint.getStatus())).count();
        long inProgress = all.stream()
                .filter(complaint -> complaint.getStatus() == ComplaintStatus.IN_PROGRESS
                        || complaint.getStatus() == ComplaintStatus.ON_HOLD)
                .count();
        long rejected = all.stream()
                .filter(complaint -> complaint.getStatus() == ComplaintStatus.REJECTED).count();
        long closed = all.stream()
                .filter(complaint -> complaint.getStatus() == ComplaintStatus.CLOSED).count();
        long receivedInPeriod = all.stream()
                .filter(complaint -> !complaint.getCreatedAt().isBefore(from)).count();
        long resolvedInPeriod = resolved.stream()
                .filter(complaint -> !complaint.getResolvedAt().isBefore(from)).count();

        Double averageResolutionHours = resolved.isEmpty() ? null : round(resolved.stream()
                .mapToDouble(complaint -> Duration.between(
                        complaint.getCreatedAt(), complaint.getResolvedAt()).toMillis() / 3_600_000.0)
                .average().orElse(0.0));
        Double averageOpenAgeHours = all.stream().noneMatch(complaint -> activeStatuses().contains(complaint.getStatus()))
                ? null : round(all.stream()
                        .filter(complaint -> activeStatuses().contains(complaint.getStatus()))
                        .mapToDouble(complaint -> Duration.between(complaint.getCreatedAt(), now).toMillis()
                                / 3_600_000.0)
                        .average().orElse(0.0));
        Double slaCompliancePercent = resolved.isEmpty() ? null : round(resolved.stream()
                .filter(complaint -> {
                    Duration target = slas.get(complaint.getSeverity());
                    return target != null && Duration.between(complaint.getCreatedAt(),
                            complaint.getResolvedAt()).compareTo(target) <= 0;
                })
                .count() * 100.0 / resolved.size());

        List<Complaint> periodComplaints = all.stream()
                .filter(complaint -> !complaint.getCreatedAt().isBefore(from))
                .toList();
        Map<String, Long> categoryTotals = new LinkedHashMap<>();
        for (Complaint complaint : periodComplaints) {
            String key = complaint.getCategory() == null ? "UNCATEGORIZED" : complaint.getCategory().getCode();
            categoryTotals.merge(key, 1L, Long::sum);
        }
        List<CategoryCount> byCategory = categoryTotals.entrySet().stream()
                .map(entry -> {
                    String code = entry.getKey();
                    String name = periodComplaints.stream()
                            .filter(complaint -> complaint.getCategory() != null
                                    && code.equals(complaint.getCategory().getCode()))
                            .map(complaint -> complaint.getCategory().getName())
                            .findFirst().orElse("Uncategorized");
                    return new CategoryCount(code, name, entry.getValue());
                })
                .sorted(Comparator.comparingLong(CategoryCount::count).reversed()
                        .thenComparing(CategoryCount::code))
                .toList();
        Map<String, Long> wardTotals = new LinkedHashMap<>();
        for (Complaint complaint : periodComplaints) {
            String ward = complaint.getWard() == null || complaint.getWard().isBlank()
                    ? "UNASSIGNED" : complaint.getWard();
            wardTotals.merge(ward, 1L, Long::sum);
        }
        List<WardCount> byWard = wardTotals.entrySet().stream()
                .map(entry -> new WardCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(WardCount::count).reversed()
                        .thenComparing(WardCount::ward))
                .toList();

        return new AnalyticsResponse(
                all.size(), byStatus,
                all.stream().filter(complaint -> complaint.getStatus() == ComplaintStatus.RESOLVED).count(),
                overdue,
                all.stream().filter(complaint -> complaint.getStatus() == ComplaintStatus.UNASSIGNED).count(),
                averageResolutionHours,
                days, receivedInPeriod, resolvedInPeriod, pending, accepted, inProgress,
                rejected, closed, byCategory, byWard, buildTrend(all, resolved, from, now, days),
                averageOpenAgeHours, slaCompliancePercent);
    }

    // -----------------------------------------------------------------------
    // Super-admin surface
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ComplaintSummary> allComplaints() {
        return complaints.findAllByOrderByCreatedAtDesc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintSummary> unassignedComplaints() {
        Map<UUID, Complaint> queue = new LinkedHashMap<>();
        complaints.findUnassigned().forEach(complaint -> queue.put(complaint.getId(), complaint));
        for (RoutingDecision decision : routingDecisions
                .findAllByCurrentTrueAndOutcomeNot("ASSIGNED")) {
            Complaint complaint = decision.getComplaint();
            if (complaint != null && complaint.getRelatedDepartment() == null
                    && switch (complaint.getStatus()) {
                        case SUBMITTED, PENDING_ASSIGNMENT, UNASSIGNED, AI_ANALYSIS_PENDING,
                                AI_ANALYSIS_COMPLETED, AI_ANALYSIS_FAILED -> true;
                        default -> false;
                    }) {
                queue.putIfAbsent(complaint.getId(), complaint);
            }
        }
        return queue.values().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintDetail adminComplaint(UUID id) {
        return detail(complaints.findById(id).orElseThrow(ComplaintNotFoundException::new), null);
    }

    @Transactional
    @Audited(action = "COMPLAINT_ASSIGN", entityType = "complaint")
    public void assign(UUID id, AssignRequest request, AuthenticatedUser admin) {
        Complaint complaint = complaints.findById(id).orElseThrow(ComplaintNotFoundException::new);
        Department target = departments.findById(request.departmentId())
                .orElseThrow(DepartmentNotEligibleException::new);
        if (target.getVerificationStatus() != VerificationStatus.VERIFIED
                || !target.isActive() || !target.isAcceptsComplaints()) {
            throw new DepartmentNotEligibleException();
        }
        policy.validate(complaint.getStatus(), ComplaintStatus.ASSIGNED, Actor.SUPER_ADMIN);
        Instant now = clock.instant();

        assignments.findByComplaintIdAndActiveTrue(id).ifPresent(active -> {
            active.setActive(false);
            active.setUnassignedAt(now);
        });
        ComplaintAssignment assignment = new ComplaintAssignment();
        assignment.setComplaint(complaint);
        assignment.setDepartment(target);
        assignment.setAssignmentType(AssignmentType.ADMIN_CORRECTION);
        assignment.setPreviousDepartment(complaint.getRelatedDepartment());
        assignment.setAssignedBy(users.getReferenceById(admin.id()));
        assignment.setReason(request.reason());
        assignment.setActive(true);
        assignment.setAssignedAt(now);
        assignment.setCreatedAt(now);
        assignments.save(assignment);

        complaint.setRelatedDepartment(target);
        complaint.setAssignedAt(now);
        complaint.setAdminOverrideByUserId(admin.id());
        complaint.setAdminOverrideAt(now);
        complaint.setAdminOverrideReason(request.reason());
        changeStatus(complaint, ComplaintStatus.ASSIGNED, request.reason(), Actor.SUPER_ADMIN, admin);
        notifyDepartmentAdmins(target, complaint.getId(), NotificationType.COMPLAINT_ASSIGNED,
                "Complaint assigned", complaint.getComplaintNumber() + " was assigned to your department.");
    }

    @Transactional
    @Audited(action = "COMPLAINT_MARK_UNASSIGNED", entityType = "complaint")
    public void markUnassigned(UUID id, String reason, AuthenticatedUser admin) {
        Complaint complaint = complaints.findById(id).orElseThrow(ComplaintNotFoundException::new);
        changeStatus(complaint, ComplaintStatus.UNASSIGNED, reason, Actor.SUPER_ADMIN, admin);
        complaint.setRelatedDepartment(null);
        assignments.findByComplaintIdAndActiveTrue(id).ifPresent(active -> {
            active.setActive(false);
            active.setUnassignedAt(clock.instant());
        });
        notifySuperAdmins(complaint, NotificationType.UNASSIGNED_COMPLAINT,
                "Complaint unassigned", complaint.getComplaintNumber());
    }

    @Transactional
    @Audited(action = "COMPLAINT_REASSIGN_DECISION", entityType = "reassignment_request")
    public void decideReassignment(UUID requestId, ReassignDecisionRequest request, AuthenticatedUser admin) {
        ReassignmentRequest entity = reassignmentRequests.findById(requestId)
                .orElseThrow(ComplaintNotFoundException::new);
        if (entity.getStatus() != ReassignmentStatus.PENDING) {
            throw new ReassignmentNotAllowedException("Request has already been reviewed");
        }
        if (request.decision() == ReassignmentStatus.APPROVED) {
            Complaint complaint = entity.getComplaint();
            if (entity.getToDepartment().getVerificationStatus() != VerificationStatus.VERIFIED
                    || !entity.getToDepartment().isAcceptsComplaints()) {
                throw new DepartmentNotEligibleException();
            }
            assignments.findByComplaintIdAndActiveTrue(complaint.getId()).ifPresent(active -> {
                active.setActive(false);
                active.setUnassignedAt(clock.instant());
            });
            ComplaintAssignment assignment = new ComplaintAssignment();
            assignment.setComplaint(complaint);
            assignment.setDepartment(entity.getToDepartment());
            assignment.setPreviousDepartment(entity.getFromDepartment());
            assignment.setAssignmentType(AssignmentType.REASSIGN);
            assignment.setAssignedBy(users.getReferenceById(admin.id()));
            assignment.setReason(entity.getReason());
            assignment.setActive(true);
            assignment.setAssignedAt(clock.instant());
            assignment.setCreatedAt(clock.instant());
            assignments.save(assignment);
            complaint.setRelatedDepartment(entity.getToDepartment());
            changeStatus(complaint, ComplaintStatus.ASSIGNED, entity.getReason(),
                    Actor.SUPER_ADMIN, admin);
            notifyDepartmentAdmins(entity.getToDepartment(), complaint.getId(),
                    NotificationType.COMPLAINT_ASSIGNED, "Complaint reassigned",
                    complaint.getComplaintNumber() + " was reassigned to your department.");
            notifyDepartmentAdmins(entity.getFromDepartment(), complaint.getId(),
                    NotificationType.REASSIGNMENT_APPROVED, "Reassignment approved",
                    complaint.getComplaintNumber() + " is now owned by another department.");
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ReasonRequiredException("reassignment rejection");
            }
            notifyDepartmentAdmins(entity.getFromDepartment(), entity.getComplaint().getId(),
                    NotificationType.REASSIGNMENT_REJECTED, "Reassignment rejected",
                    entity.getComplaint().getComplaintNumber() + " remains with your department. " + request.reason());
        }
        entity.setStatus(request.decision());
        entity.setReviewReason(request.reason());
        entity.setReviewedBy(users.getReferenceById(admin.id()));
        entity.setReviewedAt(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ReassignRequestResponse> pendingReassignments() {
        return reassignmentRequests.findAllByStatusOrderByCreatedAtDesc(ReassignmentStatus.PENDING)
                .stream().map(request -> new ReassignRequestResponse(
                        request.getId(), request.getComplaint().getId(),
                        request.getFromDepartment().getId(), request.getToDepartment().getId(),
                        request.getStatus(), request.getReason(), request.getReviewReason(),
                        request.getCreatedAt(), request.getReviewedAt()))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private Complaint accessibleComplaint(UUID id, AuthenticatedUser principal) {
        return switch (principal.role()) {
            case CITIZEN -> complaints.findByIdAndCitizenId(id, principal.id())
                    .orElseThrow(ComplaintNotFoundException::new);
            case DEPARTMENT_ADMIN -> {
                requireVerifiedDepartment(principal);
                yield complaints.findByIdAndRelatedDepartmentId(id, principal.departmentId())
                        .orElseThrow(ComplaintNotFoundException::new);
            }
            case SUPER_ADMIN -> complaints.findById(id).orElseThrow(ComplaintNotFoundException::new);
        };
    }

    private Complaint departmentComplaintEntity(UUID id, AuthenticatedUser principal) {
        requireVerifiedDepartment(principal);
        return complaints.findByIdAndRelatedDepartmentId(id, principal.departmentId())
                .orElseThrow(ComplaintNotFoundException::new);
    }

    private static void requireVerifiedDepartment(AuthenticatedUser principal) {
        if (!principal.isVerifiedDepartmentAdmin()) {
            throw new NotOwnerException();
        }
    }

    private static int normalizePeriodDays(int periodDays) {
        return Math.min(Math.max(periodDays <= 0 ? 30 : periodDays, 1), 365);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Sort parseQueueSort(String value) {
        if (!hasText(value)) {
            return Sort.by(Direction.DESC, "createdAt");
        }
        String[] parts = value.split(",", 2);
        String property = parts[0].trim();
        if (!Set.of("createdAt", "updatedAt", "status", "severity", "complaintNumber",
                "resolutionDueAt").contains(property)) {
            return Sort.by(Direction.DESC, "createdAt");
        }
        Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Direction.ASC : Direction.DESC;
        return Sort.by(direction, property);
    }

    private static Set<ComplaintStatus> activeStatuses() {
        return Set.of(ComplaintStatus.SUBMITTED, ComplaintStatus.AI_ANALYSIS_PENDING,
                ComplaintStatus.AI_ANALYSIS_COMPLETED, ComplaintStatus.AI_ANALYSIS_FAILED,
                ComplaintStatus.PENDING_ASSIGNMENT, ComplaintStatus.UNASSIGNED,
                ComplaintStatus.ASSIGNED, ComplaintStatus.ACKNOWLEDGED,
                ComplaintStatus.IN_PROGRESS, ComplaintStatus.ON_HOLD, ComplaintStatus.REOPENED);
    }

    private static Set<ComplaintStatus> pendingStatuses() {
        return Set.of(ComplaintStatus.SUBMITTED, ComplaintStatus.AI_ANALYSIS_PENDING,
                ComplaintStatus.AI_ANALYSIS_COMPLETED, ComplaintStatus.AI_ANALYSIS_FAILED,
                ComplaintStatus.PENDING_ASSIGNMENT, ComplaintStatus.UNASSIGNED);
    }

    private static Set<ComplaintStatus> acceptedStatuses() {
        return Set.of(ComplaintStatus.ASSIGNED, ComplaintStatus.ACKNOWLEDGED);
    }

    private static Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static List<TrendPoint> buildTrend(List<Complaint> all, List<Complaint> resolved,
            Instant from, Instant now, int periodDays) {
        int bucketDays = periodDays <= 31 ? 1 : 7;
        LocalDate startDate = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, long[]> buckets = new TreeMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(bucketDays)) {
            buckets.put(date, new long[2]);
        }
        for (Complaint complaint : all) {
            LocalDate date = complaint.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }
            long[] counts = buckets.get(bucketStart(date, startDate, bucketDays));
            if (counts != null) {
                counts[0]++;
            }
        }
        for (Complaint complaint : resolved) {
            LocalDate date = complaint.getResolvedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }
            long[] counts = buckets.get(bucketStart(date, startDate, bucketDays));
            if (counts != null) {
                counts[1]++;
            }
        }
        return buckets.entrySet().stream()
                .map(entry -> new TrendPoint(
                        entry.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    private static LocalDate bucketStart(LocalDate date, LocalDate startDate, int bucketDays) {
        long offset = java.time.temporal.ChronoUnit.DAYS.between(startDate, date);
        if (offset < 0) {
            return startDate;
        }
        return startDate.plusDays((offset / bucketDays) * bucketDays);
    }

    private void changeStatus(Complaint complaint, ComplaintStatus target, String reason,
            Actor actor, AuthenticatedUser principal) {
        ComplaintStatus from = complaint.getStatus();
        policy.validate(from, target, actor);
        if (policy.requiresReason(from, target) && (reason == null || reason.isBlank())) {
            throw new ReasonRequiredException(from + " → " + target);
        }
        if (policy.requiresEvidence(from, target)
                && images.countByComplaintIdAndImageKindInAndUploadStatus(complaint.getId(),
                        List.of(ImageKind.AFTER, ImageKind.EVIDENCE), UploadStatus.VERIFIED) == 0) {
            throw new EvidenceRequiredException();
        }

        complaint.setStatus(target);
        complaint.setUpdatedAt(clock.instant());
        if (target == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(clock.instant());
            complaint.setResolutionSummary(reason);
        }
        if (target == ComplaintStatus.CLOSED) {
            complaint.setClosedAt(clock.instant());
        }
        complaints.save(complaint);
        recordHistory(complaint, from, target, actor, principal, reason);

        NotificationType type = switch (target) {
            case ASSIGNED -> NotificationType.COMPLAINT_ASSIGNED;
            case ACKNOWLEDGED -> NotificationType.COMPLAINT_ACCEPTED;
            case RESOLVED -> NotificationType.COMPLAINT_RESOLVED;
            case REJECTED -> NotificationType.COMPLAINT_REJECTED;
            case REOPENED -> NotificationType.COMPLAINT_REOPENED;
            case CLOSED -> NotificationType.COMPLAINT_CLOSED;
            case UNASSIGNED -> NotificationType.UNASSIGNED_COMPLAINT;
            default -> NotificationType.STATUS_CHANGED;
        };
        notify(complaint.getCitizen().getId(), complaint.getId(), type,
                "Complaint " + complaint.getComplaintNumber(),
                "Status changed from " + from + " to " + target + ".");
    }

    private void recordHistory(Complaint complaint, ComplaintStatus from, ComplaintStatus to,
            Actor actor, AuthenticatedUser principal, String reason) {
        ComplaintStatusHistory entry = new ComplaintStatusHistory();
        entry.setComplaint(complaint);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setActorRole(actor.name());
        entry.setReason(reason);
        entry.setCreatedAt(clock.instant());
        if (principal != null) {
            entry.setChangedBy(users.getReferenceById(principal.id()));
        }
        history.save(entry);
    }

    private IssueCategory resolveCategory(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return categories.findByCode(code).orElseThrow(CategoryNotFoundException::new);
    }

    private void notify(UUID recipientId, UUID complaintId, NotificationType type, String title, String body) {
        notifications.notify(recipientId, complaintId, type, title, body);
    }

    private void notifySuperAdmins(Complaint complaint, NotificationType type, String title, String body) {
        users.findByRole(Role.SUPER_ADMIN)
                .forEach(admin -> notify(admin.getId(), complaint.getId(), type, title, body));
    }

    private void notifySuperAdmins(NotificationType type, String title, String body) {
        users.findByRole(Role.SUPER_ADMIN)
                .forEach(admin -> notify(admin.getId(), null, type, title, body));
    }

    private void notifyDepartmentAdmins(Department department, UUID complaintId,
            NotificationType type, String title, String body) {
        departmentAdmins.findByDepartmentId(department.getId())
                .forEach(admin -> notify(admin.getUserId(), complaintId, type, title, body));
    }

    private String nextComplaintNumber() {
        Object sequenceValue = entityManager.createNativeQuery("SELECT nextval('complaint_number_seq')")
                .getSingleResult();
        long value = sequenceValue instanceof Number number
                ? number.longValue()
                : Long.parseLong(sequenceValue.toString());
        return "CIV-" + clock.instant().atZone(java.time.ZoneOffset.UTC).getYear()
                + "-" + String.format("%06d", value);
    }

    private ComplaintSummary summary(Complaint complaint) {
        return new ComplaintSummary(
                complaint.getId(),
                complaint.getComplaintNumber(),
                complaint.getDescription(),
                complaint.getStatus(),
                complaint.getSeverity(),
                complaint.getCategory() == null ? null : complaint.getCategory().getCode(),
                complaint.getWard(),
                complaint.getCity(),
                complaint.getRelatedDepartment() == null ? null : complaint.getRelatedDepartment().getId(),
                complaint.getCreatedAt());
    }

    private ComplaintDetail detail(Complaint complaint, AuthenticatedUser principal) {
        Department department = complaint.getRelatedDepartment();
        List<StatusEntry> entries = new ArrayList<>();
        history.findAllByComplaintIdOrderByCreatedAtAsc(complaint.getId()).forEach(entry ->
                entries.add(new StatusEntry(entry.getFromStatus(), entry.getToStatus(),
                        entry.getActorRole(), entry.getReason(), entry.getCreatedAt())));
        List<CommentResponse> commentResponses = comments.findAllByComplaintIdOrderByCreatedAtAsc(
                complaint.getId()).stream()
                .map(comment -> new CommentResponse(comment.getId(),
                        comment.getAuthor().getFullName(), comment.getAuthorRole(),
                        comment.getBody(), comment.getCreatedAt()))
                .toList();
        List<AssignmentEntry> assignmentEntries = assignments.findAllByComplaintIdOrderByAssignedAtDesc(
                complaint.getId()).stream()
                .map(assignment -> new AssignmentEntry(
                        assignment.getDepartment().getId(), assignment.getDepartment().getName(),
                        assignment.getAssignmentType().name(), assignment.isActive(),
                        assignment.getAssignedAt(), assignment.getUnassignedAt()))
                .toList();
        FeedbackResponse feedbackResponse = feedback.findByComplaintId(complaint.getId())
                .map(item -> new FeedbackResponse(item.getRating(), item.getComment(),
                        item.isResolutionConfirmed(), item.getDisputeReason(), item.getCreatedAt()))
                .orElse(null);
        List<ComplaintImageResponse> imageResponses = images
                .findAllByComplaintIdOrderByCreatedAtAsc(complaint.getId()).stream()
                .map(image -> new ComplaintImageResponse(image.getId(), image.getStorageKey(),
                        image.getContentType(), image.getSizeBytes(), image.getImageKind(),
                        image.getUploadStatus(), image.getCreatedAt()))
                .toList();
        AiAnalysisResponse analysisResponse = aiAnalyses
                .findFirstByComplaintIdOrderByCreatedAtDesc(complaint.getId())
                .map(this::toAnalysisResponse)
                .orElse(null);
        List<com.civicsense.routing.dto.RoutingDtos.RoutingDecisionResponse> routingDecisionResponses =
                routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(complaint.getId()).stream()
                        .map(routingDecisionMapper::toResponse)
                        .toList();
        // Internal notes are department-private and are never serialized for citizens.
        List<NoteResponse> internalNotes = principal != null && principal.isVerifiedDepartmentAdmin()
                ? notes.findAllByComplaintIdAndDepartmentIdOrderByCreatedAtAsc(
                        complaint.getId(), principal.departmentId()).stream()
                        .map(note -> new NoteResponse(note.getId(), note.getAuthor().getFullName(),
                                note.getBody(), note.getCreatedAt()))
                        .toList()
                : List.of();
        return new ComplaintDetail(
                complaint.getId(), complaint.getComplaintNumber(), complaint.getDescription(),
                complaint.getAdditionalInfo(), complaint.getStatus(), complaint.getSeverity(),
                complaint.getCategory() == null ? null : complaint.getCategory().getCode(),
                complaint.getSubcategory() == null ? null : complaint.getSubcategory().getCode(),
                complaint.getAddressLine(), complaint.getCity(), complaint.getState(),
                complaint.getPostalCode(), complaint.getCountry(), complaint.getWard(), complaint.getZone(),
                complaint.getLocationSource(), complaint.getLatitude(), complaint.getLongitude(),
                department == null ? null : department.getId(),
                department == null ? null : department.getName(),
                complaint.getAssignedAt(), complaint.getResolvedAt(), complaint.getResolutionSummary(),
                complaint.getResolutionDueAt(), complaint.getCreatedAt(), entries, commentResponses,
                assignmentEntries, feedbackResponse,
                imageResponses, analysisResponse, routingDecisionResponses, internalNotes);
    }

    private AiAnalysisResponse toAnalysisResponse(AiAnalysis analysis) {
        return new AiAnalysisResponse(
                analysis.getId(), analysis.getStatus(), analysis.getIsCivicIssue(),
                analysis.getCategoryCode(), analysis.getSubcategoryCode(), analysis.getSeverity(),
                analysis.getDescription(), analysis.getSafetyRisk(), analysis.getVisibleDamage(),
                analysis.getIsBlurry(), analysis.getRequiresAdditionalInfo(), analysis.getConfidence(),
                analysis.getRecommendedDepartmentHint(), analysis.isRequiresHumanReview(),
                analysis.getErrorMessage(), analysis.getCreatedAt());
    }

    private AiAnalysisHistoryEntry toHistoryEntry(AiAnalysis analysis) {
        JsonNode payload = overridePayload(analysis);
        String description = analysis.getDescription();
        String reason = null;
        if (payload != null) {
            if (payload.hasNonNull("description")) {
                description = payload.get("description").asText();
            }
            if (payload.hasNonNull("adminOverrideReason")) {
                reason = payload.get("adminOverrideReason").asText();
            } else if (payload.hasNonNull("reason")) {
                reason = payload.get("reason").asText();
            }
        }
        return new AiAnalysisHistoryEntry(
                analysis.getId(), analysis.getStatus(), analysis.getSource(),
                analysis.getIsCivicIssue(), analysis.getCategoryCode(), analysis.getSubcategoryCode(),
                analysis.getSeverity(), description, analysis.getConfidence(),
                analysis.isRequiresHumanReview(), reason, analysis.getCreatedAt());
    }

    /** Only administrator override payloads are parsed, and only governed fields are read. */
    private JsonNode overridePayload(AiAnalysis analysis) {
        if (analysis.getSource() != com.civicsense.domain.AiAnalysisSource.ADMIN_OVERRIDE
                || !hasText(analysis.getRawResponse())) {
            return null;
        }
        try {
            return objectMapper.readTree(analysis.getRawResponse());
        } catch (Exception ex) {
            return null;
        }
    }

    private Complaint requireComplaint(UUID complaintId) {
        return complaints.findById(complaintId).orElseThrow(ComplaintNotFoundException::new);
    }
}
