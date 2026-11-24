package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.civicsense.domain.AiAnalysis;
import com.civicsense.domain.AiAnalysisRun;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.AuditLog;
import com.civicsense.domain.Complaint;
import com.civicsense.domain.LocationSource;
import com.civicsense.retention.DataRetentionService;

class Phase10DataRetentionIntegrationTest extends Phase3TestSupport {

    @Autowired
    private DataRetentionService retention;

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void cleanupDeletesOnlyRowsOlderThanRetentionCutoff() throws Exception {
        Instant now = Instant.parse("2026-11-21T00:00:00Z");
        Instant old = now.minus(800, ChronoUnit.DAYS);
        Instant recent = now.minus(30, ChronoUnit.DAYS);

        Complaint oldComplaint = complaint("RET-OLD");
        Complaint recentComplaint = complaint("RET-NEW");

        AiAnalysis oldAnalysis = analysis(oldComplaint, old);
        AiAnalysis recentAnalysis = analysis(recentComplaint, recent);
        aiAnalysisRuns.save(run(oldComplaint, oldAnalysis, old));
        aiAnalysisRuns.save(run(recentComplaint, recentAnalysis, recent));
        auditLogs.save(audit(old));
        auditLogs.save(audit(recent));

        DataRetentionService.RetentionResult result = retention.cleanup(now);

        assertThat(result.cutoff()).isBefore(now).isAfter(old);
        assertThat(result.auditLogs()).isEqualTo(1);
        assertThat(result.aiAnalyses()).isEqualTo(1);
        assertThat(result.aiAnalysisRuns()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(3);
        assertThat(auditLogs.findAll()).extracting(AuditLog::getAction).containsExactly("RECENT");
        assertThat(aiAnalyses.findAll()).extracting(AiAnalysis::getId).containsExactly(recentAnalysis.getId());
        assertThat(aiAnalysisRuns.findAll()).extracting(AiAnalysisRun::getComplaintId)
                .containsExactly(recentComplaint.getId());
    }

    private Complaint complaint(String number) {
        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(number + "-" + UUID.randomUUID().toString().substring(0, 8));
        complaint.setCitizen(citizenA);
        complaint.setDescription("Retention test complaint");
        complaint.setLocationSource(LocationSource.GPS);
        complaint.setLatitude(28.6139);
        complaint.setLongitude(77.2090);
        return complaints.save(complaint);
    }

    private AiAnalysis analysis(Complaint complaint, Instant createdAt) {
        AiAnalysis analysis = new AiAnalysis();
        analysis.setComplaint(complaint);
        analysis.setProvider("stub");
        analysis.setModel("test");
        analysis.setStatus(AiAnalysisStatus.SUCCESS);
        analysis.setCreatedAt(createdAt);
        return aiAnalyses.save(analysis);
    }

    private AiAnalysisRun run(Complaint complaint, AiAnalysis analysis, Instant createdAt) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setComplaintId(complaint.getId());
        run.setAnalysis(analysis);
        run.setCreatedAt(createdAt);
        return run;
    }

    private AuditLog audit(Instant createdAt) {
        AuditLog log = new AuditLog();
        log.setAction(createdAt.isAfter(Instant.parse("2025-01-01T00:00:00Z")) ? "RECENT" : "OLD");
        log.setEntityType("test");
        log.setEntityId(UUID.randomUUID().toString());
        log.setCreatedAt(createdAt);
        return log;
    }
}
