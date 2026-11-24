package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.civicsense.ai.AiAnalysisRequest;
import com.civicsense.ai.AiAnalysisService;
import com.civicsense.ai.AiProviderException;
import com.civicsense.ai.AiProviderResponse;
import com.civicsense.ai.AiVisionClient;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.ComplaintImage;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.UploadStatus;
import com.civicsense.storage.ObjectStorage;
import com.civicsense.storage.PresignedUrl;
import com.civicsense.storage.StorageUnavailableException;

class Phase5AiAnalysisIntegrationTest extends Phase3TestSupport {

    @MockitoBean
    AiVisionClient aiClient;

    @MockitoBean
    ObjectStorage storage;

    @Autowired
    AiAnalysisService analysisService;

    private static final String VALID_JSON = """
            {
              "isCivicIssue": true,
              "category": "ROAD_MAINTENANCE",
              "subcategory": "POTHOLE",
              "severity": "HIGH",
              "description": "A deep pothole is visible across the road surface.",
              "safetyRisk": true,
              "visibleDamage": "Large crater in asphalt",
              "isBlurry": false,
              "requiresAdditionalInfo": false,
              "confidence": 0.92,
              "recommendedDepartment": "Road Maintenance Division",
              "requiresHumanReview": false
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        resetAndSeed();
        when(storage.presignGet(any(), any(Duration.class)))
                .thenReturn(new PresignedUrl("https://storage.test/image",
                        Instant.now().plusSeconds(300), Map.of()));
        when(aiClient.providerName()).thenReturn("test-provider");
        when(aiClient.analyze(any(AiAnalysisRequest.class)))
                .thenReturn(new AiProviderResponse(VALID_JSON, 12, "test-model"));
    }

    @Test
    void submitWithVerifiedImageEnqueuesAnalysis() throws Exception {
        UUID imageId = stageVerifiedImage();
        UUID complaintId = submitWithImage(imageId);

        var complaint = complaints.findById(complaintId).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.AI_ANALYSIS_COMPLETED);
        assertThat(images.findById(imageId).orElseThrow().getComplaint().getId())
                .isEqualTo(complaintId);
        var analysis = aiAnalyses.findFirstByComplaintIdOrderByCreatedAtDesc(complaintId).orElseThrow();
        assertThat(analysis.getStatus()).isEqualTo(AiAnalysisStatus.SUCCESS);
        assertThat(analysis.getAttemptNumber()).isEqualTo(1);
        assertThat(analysis.getConfidence()).isEqualTo(0.92);
        assertThat(analysis.getDescription()).isEqualTo(
                "A deep pothole is visible across the road surface.");
        assertThat(analysis.getVisibleDamage()).isEqualTo("Large crater in asphalt");
        assertThat(analysis.getRecommendedDepartmentHint()).isEqualTo("Road Maintenance Division");
        assertThat(complaint.getRelatedDepartment()).isNull();
    }

    @Test
    void malformedResponseMarksInvalidSchemaButKeepsComplaint() throws Exception {
        when(aiClient.analyze(any(AiAnalysisRequest.class)))
                .thenReturn(new AiProviderResponse("{not json", 5, "test-model"));

        UUID complaintId = submitWithImage(stageVerifiedImage());

        assertThat(complaints.findById(complaintId)).isPresent();
        var analysis = aiAnalyses.findFirstByComplaintIdOrderByCreatedAtDesc(complaintId).orElseThrow();
        assertThat(analysis.getStatus()).isEqualTo(AiAnalysisStatus.INVALID_SCHEMA);
        assertThat(analysis.getRawResponse()).isEqualTo("{not json");
        assertThat(complaints.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.AI_ANALYSIS_FAILED);
    }

    @Test
    void lowConfidenceForcesHumanReviewEvenWhenAiSaysNo() throws Exception {
        when(aiClient.analyze(any(AiAnalysisRequest.class)))
                .thenReturn(new AiProviderResponse(VALID_JSON.replace("\"confidence\": 0.92",
                        "\"confidence\": 0.60"), 5, "test-model"));

        UUID complaintId = submitWithImage(stageVerifiedImage());
        var complaint = complaints.findById(complaintId).orElseThrow();
        var analysis = aiAnalyses.findFirstByComplaintIdOrderByCreatedAtDesc(complaintId).orElseThrow();

        assertThat(analysis.isRequiresHumanReview()).isTrue();
        assertThat(complaint.isAiRequiresReview()).isTrue();
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.AI_ANALYSIS_COMPLETED);
    }

    @Test
    void timeoutIsRecordedAndComplaintSurvives() throws Exception {
        when(aiClient.analyze(any(AiAnalysisRequest.class)))
                .thenThrow(new AiProviderException("Read timed out", true));

        UUID complaintId = submitWithImage(stageVerifiedImage());
        var analysis = aiAnalyses.findFirstByComplaintIdOrderByCreatedAtDesc(complaintId).orElseThrow();

        assertThat(analysis.getStatus()).isEqualTo(AiAnalysisStatus.TIMEOUT);
        assertThat(complaints.findById(complaintId)).isPresent();
        assertThat(verifyAiCalls()).isEqualTo(3);
    }

    @Test
    void storageFailureDuringAiImageRetrievalLeavesComplaintIntact() throws Exception {
        when(storage.presignGet(any(), any(Duration.class)))
                .thenThrow(new StorageUnavailableException("storage down"));

        UUID complaintId = submitWithImage(stageVerifiedImage());

        assertThat(complaints.findById(complaintId)).isPresent();
        assertThat(complaints.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.AI_ANALYSIS_FAILED);
        assertThat(aiAnalyses.findFirstByComplaintIdOrderByCreatedAtDesc(complaintId)).isPresent();
    }

    @Test
    void providerFailureRetriesThenSucceeds() throws Exception {
        when(aiClient.analyze(any(AiAnalysisRequest.class)))
                .thenThrow(new AiProviderException("HTTP 500", false))
                .thenThrow(new AiProviderException("HTTP 503", false))
                .thenReturn(new AiProviderResponse(VALID_JSON, 20, "test-model"));

        UUID complaintId = submitWithImage(stageVerifiedImage());
        var analysis = aiAnalyses.findFirstByComplaintIdOrderByCreatedAtDesc(complaintId).orElseThrow();

        assertThat(analysis.getStatus()).isEqualTo(AiAnalysisStatus.SUCCESS);
        assertThat(analysis.getAttemptNumber()).isEqualTo(3);
        assertThat(verifyAiCalls()).isEqualTo(3);
    }

    @Test
    void duplicateProcessingIsPrevented() throws Exception {
        UUID complaintId = submitWithImage(stageVerifiedImage());

        analysisService.process(complaintId);
        analysisService.process(complaintId);

        assertThat(aiAnalysisRuns.count()).isEqualTo(1);
        assertThat(verifyAiCalls()).isEqualTo(1);
    }

    @Test
    void complaintWithoutImagesNeverRunsAi() throws Exception {
        UUID complaintId = submitWithoutImages();

        assertThat(aiAnalyses.count()).isZero();
        assertThat(aiAnalysisRuns.count()).isZero();
        assertThat(verifyAiCalls()).isZero();
        assertThat(complaints.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.SUBMITTED);
    }

    @Test
    void detailExposesAiStatusAndImages() throws Exception {
        UUID imageId = stageVerifiedImage();
        UUID complaintId = submitWithImage(imageId);

        mockMvc.perform(get("/citizen/complaints/" + complaintId)
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AI_ANALYSIS_COMPLETED"))
                .andExpect(jsonPath("$.images[0].id").value(imageId.toString()))
                .andExpect(jsonPath("$.analysis.status").value("SUCCESS"))
                .andExpect(jsonPath("$.analysis.categoryCode").value("ROAD_MAINTENANCE"))
                .andExpect(jsonPath("$.analysis.recommendedDepartmentHint")
                        .value("Road Maintenance Division"));
    }

    private int verifyAiCalls() {
        return org.mockito.Mockito.mockingDetails(aiClient).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("analyze")).toList().size();
    }

    private UUID stageVerifiedImage() {
        ComplaintImage image = new ComplaintImage();
        image.setStorageKey("uploads/" + citizenA.getId() + "/" + UUID.randomUUID() + ".png");
        image.setOriginalFilename("pothole.png");
        image.setContentType("image/png");
        image.setSizeBytes(2048L);
        image.setImageKind(ImageKind.CITIZEN);
        image.setUploadStatus(UploadStatus.VERIFIED);
        image.setUploadedBy(users.getReferenceById(citizenA.getId()));
        image.setWidth(640);
        image.setHeight(480);
        image.setChecksumSha256("a".repeat(64));
        image.setPhash("b".repeat(16));
        image.setCreatedAt(Instant.now());
        return images.save(image).getId();
    }

    private UUID submitWithImage(UUID imageId) throws Exception {
        String response = mockMvc.perform(post("/citizen/complaints")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "description": "There is a large pothole on the main road near the market.",
                                  "latitude": 28.6139,
                                  "longitude": 77.2090,
                                  "locationSource": "GPS",
                                  "addressLine": "Main Road",
                                  "city": "Delhi",
                                  "imageIds": ["%s"]
                                }
                                """.formatted(imageId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID submitWithoutImages() throws Exception {
        String response = mockMvc.perform(post("/citizen/complaints")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "description": "There is a large pothole on the main road near the market.",
                                  "latitude": 28.6139,
                                  "longitude": 77.2090,
                                  "locationSource": "GPS"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
