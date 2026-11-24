package com.civicsense.upload;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.civicsense.domain.ImageKind;
import com.civicsense.domain.UploadStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request/response records for the secure image upload flow. */
public final class UploadDtos {

    private UploadDtos() {
    }

    public record PresignUploadRequest(
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 100) String contentType,
            @NotNull @Min(1) @Max(10L * 1024 * 1024) Long sizeBytes,
            @NotNull ImageKind imageKind) {
    }

    public record PresignUploadResponse(
            UUID imageId,
            String uploadUrl,
            String storageKey,
            String contentType,
            Instant expiresAt,
            Map<String, String> headers) {
    }

    public record ConfirmUploadResponse(
            UUID imageId,
            UUID complaintId,
            String storageKey,
            String contentType,
            Long sizeBytes,
            Integer width,
            Integer height,
            UploadStatus uploadStatus,
            Instant createdAt) {
    }

    public record ImageViewUrlResponse(UUID imageId, String url, Instant expiresAt) {
    }

    public record RejectedUploadResponse(List<UploadRejection> rejected) {
    }

    public record UploadRejection(String fileName, String reason) {
    }
}
