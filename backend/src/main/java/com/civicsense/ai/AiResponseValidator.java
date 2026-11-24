package com.civicsense.ai;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Validates untrusted provider JSON before any value is persisted or displayed. */
@Component
public class AiResponseValidator {

    private static final Set<String> CATEGORIES = Set.of(
            "ROAD_MAINTENANCE", "WASTE_MANAGEMENT", "SANITATION", "WATER_SUPPLY",
            "ELECTRICITY", "DRAINAGE", "PARKS_PUBLIC_SPACES", "PUBLIC_INFRASTRUCTURE",
            "UNCATEGORIZED");

    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final ObjectMapper mapper;

    public AiResponseValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AiAnalysisResult validate(AiProviderResponse response, int attemptNumber,
                                     double confidenceThreshold) {
        JsonNode root;
        try {
            root = mapper.readTree(response.rawJson());
        } catch (Exception exception) {
            throw new InvalidAiResponseException("AI response was not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidAiResponseException("AI response must be a JSON object");
        }

        boolean civicIssue = bool(root, "isCivicIssue");
        String categoryCode = text(root, "category", 2, 60);
        if (!CATEGORIES.contains(categoryCode)) {
            throw new InvalidAiResponseException("AI response contains an unknown category");
        }
        String subcategoryCode = text(root, "subcategory", 2, 60);
        String severity = text(root, "severity", 4, 10);
        if (!SEVERITIES.contains(severity)) {
            throw new InvalidAiResponseException("AI response contains an unknown severity");
        }
        String description = text(root, "description", 10, 500);
        boolean safetyRisk = bool(root, "safetyRisk");
        String visibleDamage = optionalText(root, "visibleDamage", 300);
        boolean blurry = bool(root, "isBlurry");
        boolean requiresAdditionalInfo = bool(root, "requiresAdditionalInfo");
        double confidence = number(root, "confidence");
        String recommendedDepartmentHint = optionalText(root, "recommendedDepartment", 200);
        boolean aiSaysReview = bool(root, "requiresHumanReview");

        // The backend recomputes the review flag independently of the provider.
        boolean requiresHumanReview = !civicIssue || blurry || requiresAdditionalInfo
                || confidence < confidenceThreshold || aiSaysReview;

        return new AiAnalysisResult(civicIssue, categoryCode, subcategoryCode, severity,
                description, safetyRisk, visibleDamage, blurry, requiresAdditionalInfo,
                confidence, recommendedDepartmentHint, requiresHumanReview,
                response.rawJson(), response.latencyMs(), attemptNumber);
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isBoolean()) {
            throw new InvalidAiResponseException("Field '" + field + "' must be a boolean");
        }
        return value.booleanValue();
    }

    private static String text(JsonNode node, String field, int min, int max) {
        String value = optionalText(node, field, max);
        if (value.length() < min) {
            throw new InvalidAiResponseException("Field '" + field + "' is too short");
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw new InvalidAiResponseException("Field '" + field + "' must be a string");
        }
        String text = value.asText();
        if (text.length() > max) {
            throw new InvalidAiResponseException("Field '" + field + "' is too long");
        }
        return text;
    }

    private static double number(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isNumber()) {
            throw new InvalidAiResponseException("Field '" + field + "' must be a number");
        }
        double number = value.asDouble();
        if (number < 0 || number > 1) {
            throw new InvalidAiResponseException("Field '" + field + "' must be between 0 and 1");
        }
        return number;
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new InvalidAiResponseException("Missing required field '" + field + "'");
        }
        return value;
    }
}
