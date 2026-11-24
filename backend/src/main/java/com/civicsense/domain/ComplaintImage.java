package com.civicsense.domain;

import java.time.Instant;
import java.util.UUID;

import com.civicsense.identity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "complaint_images")
@Getter
@Setter
@NoArgsConstructor
public class ComplaintImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Optional until the upload is confirmed and attached to a persisted complaint.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id")
    private Complaint complaint;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    private Integer width;
    private Integer height;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_kind", nullable = false)
    private ImageKind imageKind;

    private String phash;

    @Column(name = "checksum_sha256")
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false)
    private UploadStatus uploadStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
