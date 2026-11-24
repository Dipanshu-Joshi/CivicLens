package com.civicsense.upload;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civicsense.security.AuthenticatedUser;
import com.civicsense.upload.UploadDtos.ConfirmUploadResponse;
import com.civicsense.upload.UploadDtos.ImageViewUrlResponse;
import com.civicsense.upload.UploadDtos.PresignUploadRequest;
import com.civicsense.upload.UploadDtos.PresignUploadResponse;

import jakarta.validation.Valid;

/** Authenticated image upload endpoints. Every record is bound to the JWT user. */
@RestController
@RequestMapping("/uploads")
@PreAuthorize("isAuthenticated()")
public class UploadController {

    private final UploadService service;

    public UploadController(UploadService service) {
        this.service = service;
    }

    @PostMapping("/presign")
    PresignUploadResponse presign(
            @Valid @RequestBody PresignUploadRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.presign(principal, request);
    }

    @PostMapping("/{imageId}/confirm")
    ConfirmUploadResponse confirm(
            @PathVariable UUID imageId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.confirm(principal, imageId);
    }

    @DeleteMapping("/{imageId}")
    void discard(
            @PathVariable UUID imageId, @AuthenticationPrincipal AuthenticatedUser principal) {
        service.discard(principal, imageId);
    }

    @GetMapping("/{imageId}/url")
    ImageViewUrlResponse imageUrl(
            @PathVariable UUID imageId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.imageUrl(principal, imageId);
    }
}
