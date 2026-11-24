package com.civicsense.auth.exception;

/**
 * Auth-related exceptions. They are grouped as static nested classes to keep
 * the Phase 2 error surface small; the global handler maps each to its RFC 7807
 * response.
 */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException() {
            super("An account with this email already exists");
        }
    }

    public static class DepartmentAlreadyExistsException extends RuntimeException {
        public DepartmentAlreadyExistsException() {
            super("A department with this official email already exists");
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException() {
            super("Invalid or expired token");
        }
    }

    /** Password-reset tokens are invalid client input, not failed auth credentials. */
    public static class InvalidResetTokenException extends RuntimeException {
        public InvalidResetTokenException() {
            super("Invalid or expired password reset token");
        }
    }

    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException() {
            super("Account temporarily locked due to repeated failed logins");
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitExceededException() {
            this(900);
        }

        public RateLimitExceededException(long retryAfterSeconds) {
            super("Too many attempts. Please try again later.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public static class DepartmentNotFoundException extends RuntimeException {
        public DepartmentNotFoundException() {
            super("Department not found");
        }
    }

    public static class InvalidVerificationDecisionException extends RuntimeException {
        public InvalidVerificationDecisionException() {
            super("Decision must be VERIFIED or REJECTED");
        }
    }
}
