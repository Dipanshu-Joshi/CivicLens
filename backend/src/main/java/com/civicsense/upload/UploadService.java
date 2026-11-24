package com.civicsense.upload;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.domain.Complaint;
import com.civicsense.domain.ComplaintImage;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.Role;
import com.civicsense.domain.UploadStatus;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;
import com.civicsense.repository.ComplaintImageRepository;
import com.civicsense.security.AuthenticatedUser;
import com.civicsense.storage.ObjectStorage;
import com.civicsense.storage.PresignedUrl;
import com.civicsense.storage.StorageObjectNotFoundException;
import com.civicsense.storage.StoredObject;
import com.civicsense.upload.UploadDtos.ConfirmUploadResponse;
import com.civicsense.upload.UploadDtos.PresignUploadRequest;
import com.civicsense.upload.UploadDtos.PresignUploadResponse;
import com.civicsense.upload.UploadExceptions.InvalidImageContentException;
import com.civicsense.upload.UploadExceptions.UploadAlreadyAttachedException;
import com.civicsense.upload.UploadExceptions.UploadLimitExceededException;
import com.civicsense.upload.UploadExceptions.UploadNotOwnedException;
import com.civicsense.upload.UploadExceptions.UploadNotPendingException;
import com.civicsense.upload.UploadExceptions.UploadNotFoundException;
import com.civicsense.upload.UploadExceptions.UnsupportedImageTypeException;

/** Staged upload lifecycle: presign → client PUT → confirm → attach on submit. */
@Service
public class UploadService {

    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A},
            "image/webp", new byte[] {'R', 'I', 'F', 'F'});

    private final ComplaintImageRepository images;
    private final UserRepository users;
    private final ObjectStorage storage;
    private final int maxImages;
    private final long maxBytes;
    private final Duration uploadTtl;
    private final Duration viewTtl;

    public UploadService(
            ComplaintImageRepository images,
            UserRepository users,
            ObjectStorage storage,
            @Value("${civicsense.ai.max-images:5}") int maxImages,
            @Value("${civicsense.upload.max-bytes:10485760}") long maxBytes,
            @Value("${civicsense.upload.presign-ttl-seconds:900}") long presignTtlSeconds,
            @Value("${civicsense.upload.view-ttl-seconds:300}") long viewTtlSeconds) {
        this.images = images;
        this.users = users;
        this.storage = storage;
        this.maxImages = maxImages;
        this.maxBytes = maxBytes;
        this.uploadTtl = Duration.ofSeconds(presignTtlSeconds);
        this.viewTtl = Duration.ofSeconds(viewTtlSeconds);
    }

    @Transactional
    public PresignUploadResponse presign(AuthenticatedUser principal, PresignUploadRequest request) {
        String contentType = request.contentType().toLowerCase(Locale.ROOT);
        if (!MAGIC_BYTES.containsKey(contentType)) {
            throw new UnsupportedImageTypeException(request.contentType());
        }
        if (request.sizeBytes() > maxBytes) {
            throw new InvalidImageContentException(
                    "Image exceeds the " + maxBytes + " byte limit");
        }
        long staged = images.countByUploadedByIdAndUploadStatusIn(
                principal.id(), List.of(UploadStatus.PENDING, UploadStatus.VERIFIED));
        if (staged >= maxImages) {
            throw new UploadLimitExceededException(maxImages);
        }

        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
        String key = "uploads/" + principal.id() + "/" + UUID.randomUUID() + "." + extension;

        ComplaintImage image = new ComplaintImage();
        image.setStorageKey(key);
        image.setOriginalFilename(safeFileName(request.fileName()));
        image.setContentType(contentType);
        image.setSizeBytes(request.sizeBytes());
        image.setImageKind(request.imageKind());
        image.setUploadStatus(UploadStatus.PENDING);
        image.setUploadedBy(users.getReferenceById(principal.id()));
        image.setCreatedAt(Instant.now());
        ComplaintImage saved = images.save(image);

        PresignedUrl url = storage.presignPut(key, contentType, uploadTtl);
        return new PresignUploadResponse(saved.getId(), url.url(), key, contentType,
                url.expiresAt(), url.headers());
    }

    @Transactional
    public ConfirmUploadResponse confirm(AuthenticatedUser principal, UUID imageId) {
        ComplaintImage image = ownedPending(imageId, principal);

        StoredObject object;
        try {
            object = storage.get(image.getStorageKey());
        } catch (StorageObjectNotFoundException exception) {
            throw new InvalidImageContentException("No object was uploaded for this image");
        }

        verifyMagicBytes(object.bytes(), image.getContentType());
        if (object.size() > maxBytes) {
            throw new InvalidImageContentException("Uploaded object exceeds the size limit");
        }

        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(object.bytes()));
        } catch (Exception exception) {
            throw new InvalidImageContentException("Uploaded object is not a decodable image");
        }
        if (decoded == null) {
            throw new InvalidImageContentException("Uploaded object is not a decodable image");
        }

        image.setWidth(decoded.getWidth());
        image.setHeight(decoded.getHeight());
        image.setSizeBytes(object.size());
        image.setChecksumSha256(sha256(object.bytes()));
        image.setPhash(averageHash(decoded));
        image.setUploadStatus(UploadStatus.VERIFIED);
        images.save(image);

        return new ConfirmUploadResponse(image.getId(),
                image.getComplaint() == null ? null : image.getComplaint().getId(),
                image.getStorageKey(), image.getContentType(), image.getSizeBytes(),
                image.getWidth(), image.getHeight(), image.getUploadStatus(), image.getCreatedAt());
    }

    @Transactional
    public void discard(AuthenticatedUser principal, UUID imageId) {
        ComplaintImage image = images.findById(imageId).orElseThrow(UploadNotFoundException::new);
        if (image.getUploadedBy() == null
                || !image.getUploadedBy().getId().equals(principal.id())) {
            throw new UploadNotOwnedException();
        }
        if (image.getComplaint() != null) {
            throw new UploadAlreadyAttachedException();
        }
        storage.delete(image.getStorageKey());
        images.delete(image);
    }

    @Transactional(readOnly = true)
    public com.civicsense.upload.UploadDtos.ImageViewUrlResponse imageUrl(
            AuthenticatedUser principal, UUID imageId) {
        ComplaintImage image = images.findById(imageId).orElseThrow(UploadNotFoundException::new);
        if (image.getUploadedBy() != null && image.getUploadedBy().getId().equals(principal.id())) {
            // Owner of the upload.
        } else if (image.getComplaint() != null
                && image.getComplaint().getCitizen().getId().equals(principal.id())) {
            // Citizen owner of the complaint.
        } else if (image.getComplaint() != null
                && image.getComplaint().getRelatedDepartment() != null
                && principal.isVerifiedDepartmentAdmin()
                && principal.departmentId().equals(
                        image.getComplaint().getRelatedDepartment().getId())) {
            // Verified admin of the department currently responsible for the complaint.
        } else if (principal.role() == Role.SUPER_ADMIN) {
            // Authenticated platform administrator inspecting complaint evidence.
        } else {
            throw new UploadNotOwnedException();
        }
        if (image.getUploadStatus() != UploadStatus.VERIFIED) {
            throw new InvalidImageContentException("Only verified images can be viewed");
        }
        PresignedUrl url = storage.presignGet(image.getStorageKey(), viewTtl);
        return new com.civicsense.upload.UploadDtos.ImageViewUrlResponse(
                image.getId(), url.url(), url.expiresAt());
    }

    /** Used by complaint submission; enforces ownership and verified state. */
    public List<ComplaintImage> attachableImages(AuthenticatedUser principal, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > maxImages) {
            throw new UploadLimitExceededException(maxImages);
        }
        List<ComplaintImage> found = images.findAllByIdIn(ids);
        if (found.size() != ids.size()) {
            throw new UploadNotFoundException();
        }
        for (ComplaintImage image : found) {
            if (image.getUploadedBy() == null
                    || !image.getUploadedBy().getId().equals(principal.id())) {
                throw new UploadNotOwnedException();
            }
            if (image.getComplaint() != null) {
                throw new UploadAlreadyAttachedException();
            }
            if (image.getUploadStatus() != UploadStatus.VERIFIED) {
                throw new UploadNotPendingException();
            }
        }
        return found;
    }

    /**
     * Attaches a verified staged upload to an existing complaint. Callers must have
     * already authorized the complaint; this method only enforces upload ownership,
     * confirmation state, and the allowed image kinds.
     */
    public void attachVerifiedImage(AuthenticatedUser principal, UUID imageId, Complaint complaint,
            Set<ImageKind> allowedKinds) {
        if (imageId == null) {
            throw new UploadNotFoundException();
        }
        ComplaintImage image = images.findByIdAndUploadedById(imageId, principal.id())
                .orElseThrow(UploadNotOwnedException::new);
        if (image.getUploadStatus() != UploadStatus.VERIFIED) {
            throw new UploadNotPendingException();
        }
        if (!allowedKinds.contains(image.getImageKind())) {
            throw new InvalidImageContentException(
                    "This image kind cannot be attached as complaint evidence");
        }
        if (image.getComplaint() != null && !image.getComplaint().getId().equals(complaint.getId())) {
            throw new UploadAlreadyAttachedException();
        }
        image.setComplaint(complaint);
        images.save(image);
    }

    private ComplaintImage ownedPending(UUID imageId, AuthenticatedUser principal) {
        ComplaintImage image = images.findByIdAndUploadedById(imageId, principal.id())
                .orElseThrow(UploadNotOwnedException::new);
        if (image.getComplaint() != null) {
            throw new UploadAlreadyAttachedException();
        }
        if (image.getUploadStatus() != UploadStatus.PENDING) {
            throw new UploadNotPendingException();
        }
        return image;
    }

    private static void verifyMagicBytes(byte[] bytes, String contentType) {
        byte[] expected = MAGIC_BYTES.get(contentType);
        if (expected == null || bytes.length < expected.length) {
            throw new InvalidImageContentException("Image content could not be verified");
        }
        for (int i = 0; i < expected.length; i++) {
            if (bytes[i] != expected[i]) {
                throw new InvalidImageContentException("Image bytes do not match the declared type");
            }
        }
        if ("image/webp".equals(contentType)) {
            String riff = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
            String webp = new String(bytes, 8, Math.min(12, bytes.length) - 8, StandardCharsets.US_ASCII);
            if (!"RIFF".equals(riff) || !"WEBP".equals(webp)) {
                throw new InvalidImageContentException("Image bytes do not match the declared type");
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** 8x8 grayscale average hash; used only as a duplicate indicator. */
    private static String averageHash(BufferedImage source) {
        BufferedImage small = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D graphics = small.createGraphics();
        graphics.drawImage(source, 0, 0, 8, 8, null);
        graphics.dispose();
        long bits = 0;
        long sum = 0;
        int[] pixels = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                pixels[y * 8 + x] = small.getRGB(x, y) & 0xFF;
                sum += pixels[y * 8 + x];
            }
        }
        long average = sum / 64;
        for (int i = 0; i < 64; i++) {
            if (pixels[i] > average) {
                bits |= 1L << i;
            }
        }
        return String.format(Locale.ROOT, "%016x", bits);
    }

    private static String safeFileName(String value) {
        return value.replace('\\', '/').substring(value.lastIndexOf('/') + 1);
    }
}
