package com.civicsense.observability;

import java.util.UUID;

import org.slf4j.MDC;

import jakarta.servlet.http.HttpServletRequest;

/** Single request-boundary correlation ID shared by headers, errors, logs and audit rows. */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = "civicsense.correlationId";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String getOrCreate(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }
        String supplied = request.getHeader(HEADER);
        String resolved = isValid(supplied) ? supplied.trim() : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, resolved);
        return resolved;
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    /** Starts an isolated correlation scope for scheduled work with no request header. */
    public static String beginTask() {
        String value = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, value);
        return value;
    }

    public static void endTask() {
        MDC.remove(MDC_KEY);
    }

    private static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
