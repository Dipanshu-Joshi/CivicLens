package com.civicsense.security;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.civicsense.observability.CorrelationId;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API-level rate limiting in addition to the login-specific limiter. Limits are
 * in-memory and per instance; Redis is required before horizontal scaling.
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/meta/", "/actuator/", "/v3/api-docs", "/swagger-ui", "/error", "/favicon.ico");

    private final RateLimitService rateLimits;
    private final ObjectMapper objectMapper;
    private final int globalCapacity;
    private final Duration globalWindow;
    private final int submitCapacity;
    private final Duration submitWindow;
    private final int presignCapacity;
    private final Duration presignWindow;

    public ApiRateLimitFilter(
            RateLimitService rateLimits,
            ObjectMapper objectMapper,
            @Value("${civicsense.rate-limits.global-capacity:120}") int globalCapacity,
            @Value("${civicsense.rate-limits.global-window-minutes:1}") long globalWindowMinutes,
            @Value("${civicsense.rate-limits.submit-capacity:3}") int submitCapacity,
            @Value("${civicsense.rate-limits.submit-window-minutes:1}") long submitWindowMinutes,
            @Value("${civicsense.rate-limits.presign-capacity:10}") int presignCapacity,
            @Value("${civicsense.rate-limits.presign-window-minutes:1}") long presignWindowMinutes) {
        this.rateLimits = rateLimits;
        this.objectMapper = objectMapper;
        this.globalCapacity = globalCapacity;
        this.globalWindow = Duration.ofMinutes(globalWindowMinutes);
        this.submitCapacity = submitCapacity;
        this.submitWindow = Duration.ofMinutes(submitWindowMinutes);
        this.presignCapacity = presignCapacity;
        this.presignWindow = Duration.ofMinutes(presignWindowMinutes);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = apiPath(request);
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = safeIp(request.getRemoteAddr());
        if (!rateLimits.tryConsume("api-global:" + ip, globalCapacity, globalWindow)) {
            reject(response, path, globalWindow);
            return;
        }

        String scoped = scope(request);
        if (path.equals("/citizen/complaints") && HttpMethod.POST.matches(request.getMethod())) {
            if (!rateLimits.tryConsume("submit:" + scoped, submitCapacity, submitWindow)) {
                reject(response, path, submitWindow);
                return;
            }
        } else if (path.equals("/uploads/presign") && HttpMethod.POST.matches(request.getMethod())) {
            if (!rateLimits.tryConsume("presign:" + scoped, presignCapacity, presignWindow)) {
                reject(response, path, presignWindow);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String apiPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isBlank() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
        return path.isBlank() ? "/" : path;
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String scope(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.id();
        }
        return "ip:" + safeIp(request.getRemoteAddr());
    }

    private String safeIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private void reject(HttpServletResponse response, String path, Duration window) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://civicsense.app/errors/too-many-requests");
        body.put("title", "Too many requests");
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("detail", "Too many attempts. Please try again later.");
        body.put("instance", path);
        body.put("correlationId", CorrelationId.current());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(rateLimits.retryAfterSeconds(window)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
