package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.civicsense.ai.AiAnalysisRequest;
import com.civicsense.ai.AiProviderException;
import com.civicsense.ai.AiResponseValidator;
import com.civicsense.ai.OpenAiCompatibleVisionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/** HTTP-level provider test using a local JDK stub — no paid API calls. */
class Phase5OpenAiCompatibleClientTest {

    private HttpServer server;
    private OpenAiCompatibleVisionClient client;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedAuth = new AtomicReference<>();
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new OpenAiCompatibleVisionClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key", "test-model", Duration.ofSeconds(2), mapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsImagesAndDescriptionAndExtractsJson() throws Exception {
        String aiJson = """
                {"isCivicIssue":true,"category":"DRAINAGE","subcategory":"BLOCKED_DRAIN",
                 "severity":"MEDIUM","description":"The drain is blocked with debris and waste.",
                 "safetyRisk":false,"visibleDamage":"Standing water","isBlurry":false,
                 "requiresAdditionalInfo":false,"confidence":0.88,
                 "recommendedDepartment":"Sanitation","requiresHumanReview":false}
                """;
        responseBody.set(mapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", aiJson))))));

        AiAnalysisRequest request = new AiAnalysisRequest(
                List.of(new AiAnalysisRequest.ImageReference("img-1",
                        "https://storage.test/presigned?sig=abc")),
                "Drain is blocked", "Main Street", "civic-v1");

        var response = client.analyze(request);

        assertThat(response.rawJson()).isEqualTo(aiJson);
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(receivedAuth.get()).isEqualTo("Bearer test-key");
        assertThat(receivedBody.get()).contains("https://storage.test/presigned?sig=abc");
        assertThat(receivedBody.get()).contains("Drain is blocked");
        assertThat(receivedBody.get()).contains("\"response_format\"");
    }

    @Test
    void httpFailureIsReportedAsProviderException() {
        responseStatus.set(500);
        responseBody.set("boom");

        assertThatThrownBy(() -> client.analyze(new AiAnalysisRequest(
                List.of(), "A civic issue description", null, "civic-v1")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("AI provider call failed");
    }

    @Test
    void providerSwapProducesSameRoutingResult() {
        String json = """
                {"isCivicIssue":true,"category":"WASTE_MANAGEMENT","subcategory":"GARBAGE_PILE",
                 "severity":"HIGH","description":"A large garbage pile blocks the footpath.",
                 "safetyRisk":false,"visibleDamage":"Overflowing bin","isBlurry":false,
                 "requiresAdditionalInfo":false,"confidence":0.91,
                 "recommendedDepartment":"Waste Management","requiresHumanReview":false}
                """;
        var validator = new AiResponseValidator(mapper);
        var result = validator.validate(
                new com.civicsense.ai.AiProviderResponse(json, 10, "m"), 1, 0.75);

        assertThat(result.categoryCode()).isEqualTo("WASTE_MANAGEMENT");
        assertThat(result.severity()).isEqualTo("HIGH");
        assertThat(result.requiresHumanReview()).isFalse();
    }
}
