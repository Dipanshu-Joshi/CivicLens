package com.civicsense.ai;

import java.time.Duration;

/** Deterministic offline provider for dev/test. Never registered in prod. */
public class StubVisionClient implements AiVisionClient {

    private static final String STUB_JSON = """
            {
              "isCivicIssue": true,
              "category": "UNCATEGORIZED",
              "subcategory": "GENERAL",
              "severity": "MEDIUM",
              "description": "Stub analysis: a civic issue may be visible; human confirmation required.",
              "safetyRisk": false,
              "visibleDamage": "",
              "isBlurry": false,
              "requiresAdditionalInfo": true,
              "confidence": 0.6,
              "recommendedDepartment": "",
              "requiresHumanReview": true
            }
            """;

    @Override
    public AiProviderResponse analyze(AiAnalysisRequest request) {
        return new AiProviderResponse(STUB_JSON, 1, "stub-v1");
    }

    @Override
    public String providerName() {
        return "stub";
    }

    /** Injected by tests that need to simulate provider latency. */
    public static AiProviderResponse delayed(String json, Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return new AiProviderResponse(json, (int) delay.toMillis(), "stub-v1");
    }
}
