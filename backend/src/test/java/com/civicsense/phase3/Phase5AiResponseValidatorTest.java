package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.civicsense.ai.AiAnalysisResult;
import com.civicsense.ai.AiProviderResponse;
import com.civicsense.ai.AiResponseValidator;
import com.civicsense.ai.InvalidAiResponseException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Untrusted provider output is validated before it can reach a citizen or the router. */
class Phase5AiResponseValidatorTest {

    private final AiResponseValidator validator = new AiResponseValidator(new ObjectMapper());

    @Test
    void acceptsEverySeededCivicCategory() {
        for (String category : List.of(
                "ROAD_MAINTENANCE", "WASTE_MANAGEMENT", "SANITATION", "WATER_SUPPLY",
                "ELECTRICITY", "DRAINAGE", "PARKS_PUBLIC_SPACES", "PUBLIC_INFRASTRUCTURE",
                "UNCATEGORIZED")) {
            String json = json().replace("ROAD_MAINTENANCE", category);
            var result = validate(json);
            assertThat(result.categoryCode()).isEqualTo(category);
        }
    }

    @Test
    void blurryUnrelatedAndLowConfidenceAlwaysForceHumanReview() {
        assertThat(validate(json().replace("\"isCivicIssue\":true", "\"isCivicIssue\":false"))
                .requiresHumanReview()).isTrue();
        assertThat(validate(json().replace("\"isBlurry\":false", "\"isBlurry\":true"))
                .requiresHumanReview()).isTrue();
        assertThat(validate(json().replace("\"confidence\":0.92", "\"confidence\":0.60"))
                .requiresHumanReview()).isTrue();

        var trusted = validate(json());
        assertThat(trusted.requiresHumanReview()).isFalse();
        assertThat(trusted.recommendedDepartmentHint()).isEqualTo("Road Maintenance Division");
    }

    @Test
    void malformedAndOutOfRangeResponsesAreRejected() {
        List<String> invalid = List.of(
                "{not json",
                "[]",
                json().replace("\"category\":\"ROAD_MAINTENANCE\"", "\"category\":\"FINANCE\""),
                json().replace("\"severity\":\"HIGH\"", "\"severity\":\"URGENT\""),
                json().replace("\"confidence\":0.92", "\"confidence\":1.4"),
                json().replace("\"description\":\"A deep pothole is visible across the road surface.\"",
                        "\"description\":\"short\""),
                json().replace("\"safetyRisk\":true", "\"safetyRisk\":\"yes\""),
                json().replace("\"description\":\"A deep pothole is visible across the road surface.\",", "")
        );
        for (String json : invalid) {
            assertThatThrownBy(() -> validate(json))
                    .isInstanceOf(InvalidAiResponseException.class);
        }
    }

    private AiAnalysisResult validate(String json) {
        return validator.validate(new AiProviderResponse(json, 5, "test-model"), 1, 0.75);
    }

    private static String json() {
        return """
                {
                  "isCivicIssue":true,
                  "category":"ROAD_MAINTENANCE",
                  "subcategory":"POTHOLE",
                  "severity":"HIGH",
                  "description":"A deep pothole is visible across the road surface.",
                  "safetyRisk":true,
                  "visibleDamage":"Large crater in asphalt",
                  "isBlurry":false,
                  "requiresAdditionalInfo":false,
                  "confidence":0.92,
                  "recommendedDepartment":"Road Maintenance Division",
                  "requiresHumanReview":false
                }
                """;
    }
}
