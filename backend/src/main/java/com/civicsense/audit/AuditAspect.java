package com.civicsense.audit;

import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.civicsense.domain.AuditLog;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;
import com.civicsense.observability.CorrelationId;
import com.civicsense.repository.AuditLogRepository;
import com.civicsense.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Writes an append-only audit row after a successful audited method call.
 * No user input is trusted for actor identity; it always comes from the JWT.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final HttpServletRequest request;

    public AuditAspect(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            Clock clock,
            HttpServletRequest request) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.request = request;
    }

    @Around("@annotation(audited)")
    public Object record(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();

        AuditLog log = new AuditLog();
        log.setAction(audited.action());
        log.setEntityType(audited.entityType());
        log.setEntityId(entityId(joinPoint.getArgs(), result));
        log.setReason(reason(joinPoint.getArgs()));
        log.setAfterState(trim(Arrays.toString(joinPoint.getArgs())));
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCorrelationId(CorrelationId.getOrCreate(request));
        log.setCreatedAt(clock.instant());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            log.setActorRole(principal.role().name());
            log.setActor(userRepository.getReferenceById(principal.id()));
        } else {
            log.setActorRole("SYSTEM");
        }
        auditLogRepository.save(log);
        return result;
    }

    private static String entityId(Object[] args, Object result) {
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid.toString();
            }
        }
        String resultId = readId(result);
        return resultId == null ? UUID.randomUUID().toString() : truncate(resultId, 60);
    }

    /**
     * Creation methods receive a request DTO rather than an id, so the persisted
     * response is the authoritative source for the audit entity id.
     */
    private static String readId(Object value) {
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value == null) {
            return null;
        }
        for (String methodName : new String[] {"id", "getId"}) {
            try {
                Object id = value.getClass().getMethod(methodName).invoke(value);
                if (id instanceof UUID uuid) {
                    return uuid.toString();
                }
                if (id instanceof String text && !text.isBlank()) {
                    return text;
                }
            } catch (ReflectiveOperationException ignored) {
                // This DTO does not expose an id; continue to the next convention.
            }
        }
        return null;
    }

    /**
     * Administrative request records expose a mandatory reason. Read it from
     * either the record accessor or a conventional bean getter; never fall
     * back to arbitrary argument stringification for the dedicated column.
     */
    private static String reason(Object[] args) {
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            for (String methodName : new String[] {"reason", "getReason"}) {
                String value = readString(arg, methodName);
                if (value != null && !value.isBlank()) {
                    return truncate(value, 1000);
                }
            }
        }
        return null;
    }

    private static String readString(Object value, String methodName) {
        try {
            Object result = value.getClass().getMethod(methodName).invoke(value);
            return result instanceof String text ? text : null;
        } catch (ReflectiveOperationException ignored) {
            // This argument does not carry a reason; continue scanning.
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? null : truncate(value, 2000);
    }

    private static String truncate(String value, int maxLength) {
        return value.substring(0, Math.min(value.length(), maxLength));
    }
}
