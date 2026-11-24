package com.civicsense.ai;

/** Server-validated AI output. Department hint is advisory only; never used for assignment. */
public record AiAnalysisResult(
        boolean civicIssue,
        String categoryCode,
        String subcategoryCode,
        String severity,
        String description,
        boolean safetyRisk,
        String visibleDamage,
        boolean blurry,
        boolean requiresAdditionalInfo,
        double confidence,
        String recommendedDepartmentHint,
        boolean requiresHumanReview,
        String rawJson,
        int latencyMs,
        int attemptNumber) {
}
