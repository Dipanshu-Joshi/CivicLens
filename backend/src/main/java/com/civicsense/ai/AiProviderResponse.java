package com.civicsense.ai;

/** Raw provider output plus measured latency. Never trusted until validated. */
public record AiProviderResponse(String rawJson, int latencyMs, String model) {
}
