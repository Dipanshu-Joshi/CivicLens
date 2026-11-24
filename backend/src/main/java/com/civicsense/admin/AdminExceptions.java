package com.civicsense.admin;

/** Failures specific to the platform/governance admin surface. */
public final class AdminExceptions {

    private AdminExceptions() {
    }

    /** Shared server-side guard for every reason-bearing administrative mutation. */
    public static String requireReason(String reason) {
        String trimmed = reason == null ? null : reason.trim();
        if (trimmed == null || trimmed.length() < 10 || trimmed.length() > 1000) {
            throw new AdminValidationException("A reason of 10 to 1000 characters is required");
        }
        return trimmed;
    }

    /** The request is well formed but the action is not permitted in the current state. */
    public static class InvalidAdminActionException extends RuntimeException {
        public InvalidAdminActionException(String message) {
            super(message);
        }
    }

    /** Missing or out-of-range administrative input. */
    public static class AdminValidationException extends RuntimeException {
        public AdminValidationException(String message) {
            super(message);
        }
    }

    /** Administrative target does not exist. */
    public static class AdminNotFoundException extends RuntimeException {
        public AdminNotFoundException(String message) {
            super(message);
        }
    }
}
