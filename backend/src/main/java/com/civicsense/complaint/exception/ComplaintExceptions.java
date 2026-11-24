package com.civicsense.complaint.exception;

import java.util.Set;

import com.civicsense.domain.ComplaintStatus;

/** Domain errors for Phase 3. Mapped to problem details by GlobalExceptionHandler. */
public final class ComplaintExceptions {

    private ComplaintExceptions() {
    }

    public static class ComplaintNotFoundException extends RuntimeException {
        public ComplaintNotFoundException() {
            super("Complaint not found");
        }
    }

    public static class InvalidStatusTransitionException extends RuntimeException {
        private final Set<ComplaintStatus> allowed;

        public InvalidStatusTransitionException(ComplaintStatus from, ComplaintStatus to,
                Set<ComplaintStatus> allowed) {
            super("Cannot move from " + from + " to " + to
                    + (allowed.isEmpty() ? "" : ". Allowed: " + allowed));
            this.allowed = allowed;
        }

        public Set<ComplaintStatus> allowed() {
            return allowed;
        }
    }

    public static class ReasonRequiredException extends RuntimeException {
        public ReasonRequiredException(String transition) {
            super("A reason is required for " + transition);
        }
    }

    public static class EvidenceRequiredException extends RuntimeException {
        public EvidenceRequiredException() {
            super("At least one verified AFTER or EVIDENCE image is required before resolution");
        }
    }

    public static class NotOwnerException extends RuntimeException {
        public NotOwnerException() {
            super("Only the complaint owner may perform this action");
        }
    }

    public static class DepartmentNotEligibleException extends RuntimeException {
        public DepartmentNotEligibleException() {
            super("The target department is not verified and accepting complaints");
        }
    }

    public static class ReassignmentNotAllowedException extends RuntimeException {
        public ReassignmentNotAllowedException(String detail) {
            super(detail);
        }
    }

    public static class InvalidLocationException extends RuntimeException {
        public InvalidLocationException(String detail) {
            super(detail);
        }
    }

    public static class InvalidBoundaryException extends RuntimeException {
        public InvalidBoundaryException(String detail) {
            super(detail);
        }
    }

    public static class CategoryNotFoundException extends RuntimeException {
        public CategoryNotFoundException() {
            super("Category not found");
        }
    }

    public static class ServiceAreaNotFoundException extends RuntimeException {
        public ServiceAreaNotFoundException() {
            super("Service area not found");
        }
    }

    public static class JurisdictionNotFoundException extends RuntimeException {
        public JurisdictionNotFoundException() {
            super("Jurisdiction not found");
        }
    }

    public static class CategoryInUseException extends RuntimeException {
        public CategoryInUseException() {
            super("Category is referenced by complaints or departments and cannot be deactivated");
        }
    }

    public static class DuplicateReferenceException extends RuntimeException {
        public DuplicateReferenceException(String detail) {
            super(detail);
        }
    }
}
