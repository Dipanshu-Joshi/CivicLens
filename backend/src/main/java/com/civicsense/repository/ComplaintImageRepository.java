package com.civicsense.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.ComplaintImage;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.UploadStatus;

public interface ComplaintImageRepository extends JpaRepository<ComplaintImage, UUID> {

    long countByComplaintIdAndImageKindInAndUploadStatus(
            UUID complaintId, List<ImageKind> kinds, UploadStatus uploadStatus);

    List<ComplaintImage> findAllByComplaintIdOrderByCreatedAtAsc(UUID complaintId);

    long countByUploadedByIdAndUploadStatusIn(UUID uploadedById, List<UploadStatus> statuses);

    java.util.Optional<ComplaintImage> findByIdAndUploadedById(UUID id, UUID uploadedById);

    List<ComplaintImage> findAllByIdIn(List<UUID> ids);

    List<ComplaintImage> findAllByComplaintIdAndUploadStatusOrderByCreatedAtAsc(
            UUID complaintId, UploadStatus status);
}
