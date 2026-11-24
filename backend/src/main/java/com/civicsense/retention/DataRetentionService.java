package com.civicsense.retention;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.observability.CorrelationId;
import com.civicsense.repository.AiAnalysisRepository;
import com.civicsense.repository.AiAnalysisRunRepository;
import com.civicsense.repository.AuditLogRepository;

/** Removes expired audit and AI history without exposing a deletion API. */
@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);

    private final AuditLogRepository auditLogs;
    private final AiAnalysisRepository aiAnalyses;
    private final AiAnalysisRunRepository aiAnalysisRuns;
    private final Clock clock;
    private final int retentionMonths;
    private final boolean enabled;

    public DataRetentionService(
            AuditLogRepository auditLogs,
            AiAnalysisRepository aiAnalyses,
            AiAnalysisRunRepository aiAnalysisRuns,
            Clock clock,
            @Value("${civicsense.retention.months:24}") int retentionMonths,
            @Value("${civicsense.retention.enabled:false}") boolean enabled) {
        this.auditLogs = auditLogs;
        this.aiAnalyses = aiAnalyses;
        this.aiAnalysisRuns = aiAnalysisRuns;
        this.clock = clock;
        this.retentionMonths = retentionMonths;
        this.enabled = enabled;
    }

    /** Spring's "-" cron expression disables scheduling unless explicitly configured. */
    @Scheduled(cron = "${civicsense.retention.cron:-}")
    public void scheduledCleanup() {
        CorrelationId.beginTask();
        try {
            if (enabled) {
                cleanup(clock.instant());
            }
        } finally {
            CorrelationId.endTask();
        }
    }

    /** Deterministic entry point for tests and manual administrator operations. */
    @Transactional
    public RetentionResult cleanup(Instant now) {
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(retentionMonths).toInstant();
        int runs = aiAnalysisRuns.deleteByCreatedAtBefore(cutoff);
        int analyses = aiAnalyses.deleteByCreatedAtBefore(cutoff);
        int audits = auditLogs.deleteByCreatedAtBefore(cutoff);
        RetentionResult result = new RetentionResult(cutoff, audits, analyses, runs);
        log.info("Retention cleanup removed {} audit logs, {} analyses and {} analysis runs before {}",
                audits, analyses, runs, cutoff);
        return result;
    }

    public record RetentionResult(Instant cutoff, int auditLogs, int aiAnalyses, int aiAnalysisRuns) {
        public int total() {
            return auditLogs + aiAnalyses + aiAnalysisRuns;
        }
    }
}
