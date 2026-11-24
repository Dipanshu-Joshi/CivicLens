package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.civicsense.domain.ComplaintImage;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.UploadStatus;
import com.civicsense.storage.ObjectStorage;
import com.civicsense.storage.PresignedUrl;
import com.civicsense.storage.StorageObjectNotFoundException;
import com.civicsense.storage.StorageUnavailableException;
import com.civicsense.storage.StoredObject;

class Phase5UploadIntegrationTest extends Phase3TestSupport {

    @MockitoBean
    ObjectStorage storage;

    @BeforeEach
    void setUp() {
        resetAndSeed();
        when(storage.presignPut(anyString(), anyString(), any(Duration.class)))
                .thenReturn(new PresignedUrl("https://storage.test/upload", Instant.now().plusSeconds(900),
                        Map.of()));
        when(storage.presignGet(anyString(), any(Duration.class)))
                .thenReturn(new PresignedUrl("https://storage.test/view", Instant.now().plusSeconds(300),
                        Map.of()));
    }

    @Test
    void presignRejectsUnsupportedMimeType() throws Exception {
        mockMvc.perform(post("/uploads/presign")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"bad.pdf","contentType":"application/pdf",
                                 "sizeBytes":100,"imageKind":"CITIZEN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid upload"));
    }

    @Test
    void presignRejectsOversizedFile() throws Exception {
        mockMvc.perform(post("/uploads/presign")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"big.png","contentType":"image/png",
                                 "sizeBytes":10485761,"imageKind":"CITIZEN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void presignEnforcesMaxImageCount() throws Exception {
        for (int i = 0; i < 5; i++) {
            stageImage(citizenA.getId(), UploadStatus.PENDING);
        }
        mockMvc.perform(post("/uploads/presign")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"sixth.png","contentType":"image/png",
                                 "sizeBytes":100,"imageKind":"CITIZEN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("maximum of 5")));
    }

    @Test
    void confirmRejectsMismatchedMagicBytes() throws Exception {
        UUID imageId = stageImage(citizenA.getId(), UploadStatus.PENDING);
        when(storage.get(anyString())).thenReturn(new StoredObject(
                new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08},
                "image/png", 8));

        mockMvc.perform(post("/uploads/" + imageId + "/confirm")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("do not match")));

        assertThat(images.findById(imageId).orElseThrow().getUploadStatus())
                .isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void confirmVerifiesValidImageAndStoresMetadata() throws Exception {
        UUID imageId = stageImage(citizenA.getId(), UploadStatus.PENDING);
        byte[] png = validPng();
        when(storage.get(anyString())).thenReturn(new StoredObject(png, "image/png", png.length));

        mockMvc.perform(post("/uploads/" + imageId + "/confirm")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.width").value(2))
                .andExpect(jsonPath("$.height").value(2));

        ComplaintImage saved = images.findById(imageId).orElseThrow();
        assertThat(saved.getUploadStatus()).isEqualTo(UploadStatus.VERIFIED);
        assertThat(saved.getChecksumSha256()).hasSize(64);
        assertThat(saved.getPhash()).hasSize(16);
    }

    @Test
    void citizenCannotConfirmAnotherCitizensUpload() throws Exception {
        UUID imageId = stageImage(citizenA.getId(), UploadStatus.PENDING);
        mockMvc.perform(post("/uploads/" + imageId + "/confirm")
                        .header("Authorization", "Bearer " + citizenBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void discardRemovesRecordAndStorageObject() throws Exception {
        UUID imageId = stageImage(citizenA.getId(), UploadStatus.PENDING);
        mockMvc.perform(delete("/uploads/" + imageId)
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk());
        assertThat(images.findById(imageId)).isEmpty();
        verify(storage).delete(anyString());
    }

    @Test
    void viewUrlRequiresOwnership() throws Exception {
        UUID imageId = stageImage(citizenA.getId(), UploadStatus.VERIFIED);
        mockMvc.perform(get("/uploads/" + imageId + "/url")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://storage.test/view"));
        mockMvc.perform(get("/uploads/" + imageId + "/url")
                        .header("Authorization", "Bearer " + citizenBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void storageFailureDuringPresignReturnsServiceUnavailable() throws Exception {
        when(storage.presignPut(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new StorageUnavailableException("storage down"));

        mockMvc.perform(post("/uploads/presign")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"down.png","contentType":"image/png",
                                 "sizeBytes":100,"imageKind":"CITIZEN"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Storage unavailable"));
    }

    @Test
    void storageFailureDuringConfirmLeavesImagePending() throws Exception {
        UUID imageId = stageImage(citizenA.getId(), UploadStatus.PENDING);
        when(storage.get(anyString())).thenThrow(new StorageUnavailableException("storage down"));

        mockMvc.perform(post("/uploads/" + imageId + "/confirm")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isServiceUnavailable());

        assertThat(images.findById(imageId).orElseThrow().getUploadStatus())
                .isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void presignStorageFailureRollsBackPendingImage() throws Exception {
        long before = images.count();
        when(storage.presignPut(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new StorageUnavailableException("storage down"));

        mockMvc.perform(post("/uploads/presign")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"rollback.png","contentType":"image/png",
                                 "sizeBytes":100,"imageKind":"CITIZEN"}
                                """))
                .andExpect(status().isServiceUnavailable());

        assertThat(images.count()).isEqualTo(before);
    }

    @Test
    void confirmRejectsMissingOrOversizedStoredObjectAndKeepsImagePending() throws Exception {
        UUID missingId = stageImage(citizenA.getId(), UploadStatus.PENDING);
        when(storage.get(anyString())).thenThrow(new StorageObjectNotFoundException("missing-key"));
        mockMvc.perform(post("/uploads/" + missingId + "/confirm")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No object")));
        assertThat(images.findById(missingId).orElseThrow().getUploadStatus())
                .isEqualTo(UploadStatus.PENDING);

        UUID oversizedId = stageImage(citizenA.getId(), UploadStatus.PENDING);
        byte[] png = validPng();
        doReturn(new StoredObject(png, "image/png", 10_485_761L))
                .when(storage).get(anyString());
        mockMvc.perform(post("/uploads/" + oversizedId + "/confirm")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("size limit")));
        assertThat(images.findById(oversizedId).orElseThrow().getUploadStatus())
                .isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void crossUserDiscardIsForbiddenAndPreservesImageAndObject() throws Exception {
        UUID imageId = stageImage(citizenA.getId(), UploadStatus.PENDING);

        mockMvc.perform(delete("/uploads/" + imageId)
                        .header("Authorization", "Bearer " + citizenBToken))
                .andExpect(status().isForbidden());

        assertThat(images.findById(imageId)).isPresent();
        verify(storage, never()).delete(anyString());
    }

    @Test
    void storageFailureDuringDiscardKeepsImageRecord() throws Exception {
        UUID imageId = stageImage(citizenA.getId(), UploadStatus.PENDING);
        doThrow(new StorageUnavailableException("storage down")).when(storage).delete(anyString());

        mockMvc.perform(delete("/uploads/" + imageId)
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isServiceUnavailable());

        assertThat(images.findById(imageId)).isPresent();
    }

    @Test
    void presignedUrlsUseConfiguredPutAndViewTtls() throws Exception {
        mockMvc.perform(post("/uploads/presign")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType("application/json")
                        .content("""
                                {"fileName":"ttl.png","contentType":"image/png",
                                 "sizeBytes":100,"imageKind":"CITIZEN"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<Duration> putTtl = ArgumentCaptor.forClass(Duration.class);
        verify(storage).presignPut(anyString(), anyString(), putTtl.capture());
        assertThat(putTtl.getValue()).isEqualTo(Duration.ofSeconds(900));

        UUID imageId = stageImage(citizenA.getId(), UploadStatus.VERIFIED);
        Instant before = Instant.now();
        var response = mockMvc.perform(get("/uploads/" + imageId + "/url")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        Instant after = Instant.now();

        ArgumentCaptor<Duration> viewTtl = ArgumentCaptor.forClass(Duration.class);
        verify(storage).presignGet(anyString(), viewTtl.capture());
        assertThat(viewTtl.getValue()).isEqualTo(Duration.ofSeconds(300));
        Instant expiresAt = Instant.parse(objectMapper.readTree(response.getContentAsString())
                .get("expiresAt").asText());
        assertThat(expiresAt).isAfter(before.plusSeconds(295)).isBefore(after.plusSeconds(305));
    }

    private UUID stageImage(UUID ownerId, UploadStatus status) {
        ComplaintImage image = new ComplaintImage();
        image.setStorageKey("uploads/" + ownerId + "/" + UUID.randomUUID() + ".png");
        image.setOriginalFilename("test.png");
        image.setContentType("image/png");
        image.setSizeBytes(100L);
        image.setImageKind(ImageKind.CITIZEN);
        image.setUploadStatus(status);
        image.setUploadedBy(users.getReferenceById(ownerId));
        image.setCreatedAt(Instant.now());
        if (status == UploadStatus.VERIFIED) {
            image.setWidth(2);
            image.setHeight(2);
            image.setChecksumSha256("abc");
            image.setPhash("def");
        }
        return images.save(image).getId();
    }

    private static byte[] validPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFFFFFF);
        image.setRGB(1, 1, 0x000000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
