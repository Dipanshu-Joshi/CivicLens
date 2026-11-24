package com.civicsense.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generic OpenAI-compatible chat-completions client. Works with any provider
 * exposing that wire format; the base URL and key come from environment config.
 */
public class OpenAiCompatibleVisionClient implements AiVisionClient {

    private static final String SYSTEM_PROMPT = """
            You are a civic issue image analyst. Examine the supplied images and the citizen
            description. Return ONLY a JSON object (no markdown) with these exact fields:
            isCivicIssue (boolean), category (one of ROAD_MAINTENANCE, WASTE_MANAGEMENT,
            SANITATION, WATER_SUPPLY, ELECTRICITY, DRAINAGE, PARKS_PUBLIC_SPACES,
            PUBLIC_INFRASTRUCTURE, UNCATEGORIZED), subcategory (string 2-60 chars),
            severity (LOW|MEDIUM|HIGH|CRITICAL), description (string 10-500 chars),
            safetyRisk (boolean), visibleDamage (string max 300 chars), isBlurry (boolean),
            requiresAdditionalInfo (boolean), confidence (number 0-1),
            recommendedDepartment (string max 200 chars), requiresHumanReview (boolean).
            Never infer or output any address, coordinate, or precise location from images.
            The recommendedDepartment is an advisory hint only and never an assignment.
            """;

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper mapper;

    public OpenAiCompatibleVisionClient(String baseUrl, String apiKey, String model,
                                        Duration timeout, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
        this.mapper = mapper;
    }

    @Override
    public AiProviderResponse analyze(AiAnalysisRequest request) throws AiProviderException {
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> body = buildBody(request);
            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (response == null) {
                throw new AiProviderException("Empty response from AI provider", false);
            }
            JsonNode root = mapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new AiProviderException("AI provider response had no text content", false);
            }
            return new AiProviderResponse(content.asText(),
                    (int) (System.currentTimeMillis() - started), model);
        } catch (ResourceAccessException exception) {
            boolean timeout = exception.getCause() instanceof java.net.SocketTimeoutException
                    || exception.getMessage() != null
                    && exception.getMessage().toLowerCase().contains("timeout");
            throw new AiProviderException("AI provider call failed: " + exception.getMessage(),
                    exception, timeout);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiProviderException("AI provider call failed: " + exception.getMessage(),
                    exception, false);
        }
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    private Map<String, Object> buildBody(AiAnalysisRequest request) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", buildUserText(request)));
        for (AiAnalysisRequest.ImageReference image : request.images()) {
            content.add(Map.of("type", "image_url",
                    "image_url", Map.of("url", image.signedUrl())));
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", content);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(Map.of("role", "system", "content", SYSTEM_PROMPT), message));
        return body;
    }

    private static String buildUserText(AiAnalysisRequest request) {
        StringBuilder text = new StringBuilder("Citizen description:\n")
                .append(request.citizenDescription());
        if (request.addressHint() != null && !request.addressHint().isBlank()) {
            text.append("\nManual address hint (user-provided, may be approximate):\n")
                    .append(request.addressHint());
        }
        text.append("\nAnalyse the attached images. Do not infer any location from them.");
        return text.toString();
    }
}
