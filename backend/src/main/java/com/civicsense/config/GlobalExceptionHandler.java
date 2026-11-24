package com.civicsense.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.civicsense.admin.AdminExceptions.AdminNotFoundException;
import com.civicsense.admin.AdminExceptions.AdminValidationException;
import com.civicsense.admin.AdminExceptions.InvalidAdminActionException;
import com.civicsense.auth.exception.AuthExceptions.AccountLockedException;
import com.civicsense.auth.exception.AuthExceptions.DepartmentAlreadyExistsException;
import com.civicsense.auth.exception.AuthExceptions.DepartmentNotFoundException;
import com.civicsense.auth.exception.AuthExceptions.EmailAlreadyExistsException;
import com.civicsense.auth.exception.AuthExceptions.InvalidCredentialsException;
import com.civicsense.auth.exception.AuthExceptions.InvalidResetTokenException;
import com.civicsense.auth.exception.AuthExceptions.InvalidTokenException;
import com.civicsense.auth.exception.AuthExceptions.InvalidVerificationDecisionException;
import com.civicsense.ai.AiProviderException;
import com.civicsense.ai.InvalidAiResponseException;
import com.civicsense.auth.exception.AuthExceptions.RateLimitExceededException;
import com.civicsense.complaint.exception.ComplaintExceptions.CategoryInUseException;
import com.civicsense.complaint.exception.ComplaintExceptions.CategoryNotFoundException;
import com.civicsense.complaint.exception.ComplaintExceptions.ComplaintNotFoundException;
import com.civicsense.complaint.exception.ComplaintExceptions.DepartmentNotEligibleException;
import com.civicsense.complaint.exception.ComplaintExceptions.DuplicateReferenceException;
import com.civicsense.complaint.exception.ComplaintExceptions.EvidenceRequiredException;
import com.civicsense.complaint.exception.ComplaintExceptions.InvalidBoundaryException;
import com.civicsense.complaint.exception.ComplaintExceptions.InvalidLocationException;
import com.civicsense.complaint.exception.ComplaintExceptions.InvalidStatusTransitionException;
import com.civicsense.complaint.exception.ComplaintExceptions.JurisdictionNotFoundException;
import com.civicsense.complaint.exception.ComplaintExceptions.NotOwnerException;
import com.civicsense.complaint.exception.ComplaintExceptions.ReasonRequiredException;
import com.civicsense.complaint.exception.ComplaintExceptions.ReassignmentNotAllowedException;
import com.civicsense.complaint.exception.ComplaintExceptions.ServiceAreaNotFoundException;
import com.civicsense.department.exception.DepartmentExceptions.InvalidDepartmentSettingException;
import com.civicsense.notification.NotificationPreferenceException;
import com.civicsense.observability.CorrelationId;
import com.civicsense.security.RateLimitService;
import com.civicsense.storage.StorageObjectNotFoundException;
import com.civicsense.storage.StorageUnavailableException;
import com.civicsense.upload.UploadExceptions.InvalidImageContentException;
import com.civicsense.upload.UploadExceptions.UploadAlreadyAttachedException;
import com.civicsense.upload.UploadExceptions.UploadLimitExceededException;
import com.civicsense.upload.UploadExceptions.UploadNotOwnedException;
import com.civicsense.upload.UploadExceptions.UploadNotPendingException;
import com.civicsense.upload.UploadExceptions.UploadNotFoundException;
import com.civicsense.upload.UploadExceptions.UnsupportedImageTypeException;

import jakarta.servlet.http.HttpServletRequest;

/** RFC 7807-shaped error responses with a correlation id on every failure. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final RateLimitService rateLimitService;

    public GlobalExceptionHandler(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> Map.of(
                        "field", error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed",
                "Request body contains invalid fields", request, errors);
    }

    @ExceptionHandler({EmailAlreadyExistsException.class, DepartmentAlreadyExistsException.class})
    ResponseEntity<Map<String, Object>> conflict(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request, null);
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidTokenException.class})
    ResponseEntity<Map<String, Object>> unauthorized(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<Map<String, Object>> locked(AccountLockedException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "Account locked", ex.getMessage(), request, null);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<Map<String, Object>> rateLimited(
            RateLimitExceededException ex, HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> response = build(
                HttpStatus.TOO_MANY_REQUESTS, "Too many requests", ex.getMessage(), request, null);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.retryAfterSeconds()))
                .body(response.getBody());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidVerificationDecisionException.class)
    ResponseEntity<Map<String, Object>> badDecision(
            InvalidVerificationDecisionException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid decision", ex.getMessage(), request, null);
    }

    @ExceptionHandler({DepartmentNotFoundException.class, ComplaintNotFoundException.class,
            CategoryNotFoundException.class, ServiceAreaNotFoundException.class,
            JurisdictionNotFoundException.class})
    ResponseEntity<Map<String, Object>> notFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> routeNotFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not found", "Resource not found", request, null);
    }

    @ExceptionHandler({InvalidStatusTransitionException.class, CategoryInUseException.class,
            DuplicateReferenceException.class})
    ResponseEntity<Map<String, Object>> phase3Conflict(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request, null);
    }

    @ExceptionHandler({ReasonRequiredException.class, EvidenceRequiredException.class,
            DepartmentNotEligibleException.class, ReassignmentNotAllowedException.class,
            InvalidLocationException.class, InvalidBoundaryException.class,
            InvalidDepartmentSettingException.class, NotificationPreferenceException.class,
            InvalidResetTokenException.class})
    ResponseEntity<Map<String, Object>> badRequest(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), request, null);
    }

    @ExceptionHandler(NotOwnerException.class)
    ResponseEntity<Map<String, Object>> notOwner(NotOwnerException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidAdminActionException.class)
    ResponseEntity<Map<String, Object>> invalidAdminAction(
            InvalidAdminActionException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request, null);
    }

    @ExceptionHandler(AdminNotFoundException.class)
    ResponseEntity<Map<String, Object>> adminNotFound(
            AdminNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), request, null);
    }

    @ExceptionHandler(AdminValidationException.class)
    ResponseEntity<Map<String, Object>> adminValidation(
            AdminValidationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), request, null);
    }

    @ExceptionHandler({UnsupportedImageTypeException.class, InvalidImageContentException.class,
            UploadLimitExceededException.class, UploadNotPendingException.class,
            UploadAlreadyAttachedException.class})
    ResponseEntity<Map<String, Object>> invalidUpload(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid upload", ex.getMessage(), request, null);
    }

    @ExceptionHandler({UploadNotFoundException.class, StorageObjectNotFoundException.class})
    ResponseEntity<Map<String, Object>> uploadNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), request, null);
    }

    @ExceptionHandler(UploadNotOwnedException.class)
    ResponseEntity<Map<String, Object>> uploadNotOwned(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request, null);
    }

    @ExceptionHandler(StorageUnavailableException.class)
    ResponseEntity<Map<String, Object>> storageUnavailable(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Storage unavailable",
                "Image storage is temporarily unavailable", request, null);
    }

    // AI runs after the submit transaction commits; these handlers exist for any
    // synchronous future use and deliberately do not expose provider internals.
    @ExceptionHandler(InvalidAiResponseException.class)
    ResponseEntity<Map<String, Object>> invalidAiResponse(
            InvalidAiResponseException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, "Invalid AI response",
                "The AI provider returned an invalid response", request, null);
    }

    @ExceptionHandler(AiProviderException.class)
    ResponseEntity<Map<String, Object>> aiProviderUnavailable(
            AiProviderException ex, HttpServletRequest request) {
        HttpStatus status = ex.isTimeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        return build(status, "AI provider unavailable",
                "The AI provider is temporarily unavailable", request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Unexpected server error", request, null);
    }

    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request,
            List<Map<String, String>> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://civicsense.app/errors/" + title.toLowerCase().replace(' ', '-'));
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("correlationId", CorrelationId.getOrCreate(request));
        if (errors != null) {
            body.put("errors", errors);
        }
        return ResponseEntity.status(status).body(body);
    }
}
