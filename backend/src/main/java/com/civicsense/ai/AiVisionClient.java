package com.civicsense.ai;

/** Provider-independent vision client. Implementations may call any AI provider. */
public interface AiVisionClient {

    /**
     * Analyzes complaint images and description. Implementations must return a
     * raw JSON string; parsing and validation happen server-side in {@link AiResponseValidator}.
     *
     * @throws AiProviderException on HTTP failure, malformed provider response, or timeout
     */
    AiProviderResponse analyze(AiAnalysisRequest request) throws AiProviderException;

    /** Stable provider identifier persisted with each analysis. */
    String providerName();
}
