package com.civicsense.upload;

/** Upload-specific errors mapped to RFC 7807 responses. */
public final class UploadExceptions {

    private UploadExceptions() {
    }

    public static class UploadNotFoundException extends RuntimeException {
        public UploadNotFoundException() {
            super("Upload record not found");
        }
    }

    public static class UploadNotOwnedException extends RuntimeException {
        public UploadNotOwnedException() {
            super("This upload does not belong to the current user");
        }
    }

    public static class UploadLimitExceededException extends RuntimeException {
        public UploadLimitExceededException(int limit) {
            super("A maximum of " + limit + " images may be staged at once");
        }
    }

    public static class UnsupportedImageTypeException extends RuntimeException {
        public UnsupportedImageTypeException(String contentType) {
            super("Unsupported image content type: " + contentType);
        }
    }

    public static class InvalidImageContentException extends RuntimeException {
        public InvalidImageContentException(String detail) {
            super(detail);
        }
    }

    public static class UploadNotPendingException extends RuntimeException {
        public UploadNotPendingException() {
            super("This upload has already been confirmed or rejected");
        }
    }

    public static class UploadAlreadyAttachedException extends RuntimeException {
        public UploadAlreadyAttachedException() {
            super("This upload is already attached to a complaint");
        }
    }
}
