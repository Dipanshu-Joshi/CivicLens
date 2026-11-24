package com.civicsense.ai;

import java.util.List;

/** All untrusted inputs are confined to this immutable request. */
public record AiAnalysisRequest(
        List<ImageReference> images,
        String citizenDescription,
        String addressHint,
        String promptVersion) {

    public record ImageReference(String imageId, String signedUrl) {
    }
}
