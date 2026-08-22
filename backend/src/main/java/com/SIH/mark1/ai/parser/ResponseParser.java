package com.SIH.mark1.ai.parser;

import com.SIH.mark1.ai.dto.AIResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ResponseParser {

    private final ObjectMapper objectMapper;

    public ResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AIResponse parseAnalysis(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(rawResponse));
            String title = text(root, "title", "Complaint Analysis");
            String summary = text(root, "summary", "");
            String department = text(root, "department", "UNKNOWN");
            String category = text(root, "category", "UNKNOWN");
            String priority = text(root, "priority", "MEDIUM");
            int confidence = confidence(root, department, category);
            String scope = text(root, "scope", "LOCAL_AREA").toUpperCase();
            String resourceType = text(root, "resourceType", "UNKNOWN").toUpperCase();
            String resourceIdentifier = nullableText(root, "resourceIdentifier");

            return new AIResponse(title, summary, department, category, priority, confidence, scope, resourceType, resourceIdentifier);
        } catch (Exception ex) {
            return new AIResponse("Complaint Analysis", "", "UNKNOWN", "UNKNOWN", "MEDIUM", 0);
        }
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String text(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText(fallback);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String nullableText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private int confidence(JsonNode root, String department, String category) {
        JsonNode node = root.path("confidence");
        int confidence = 0;
        if (node.isNumber()) {
            confidence = node.asInt();
        } else if (node.isTextual()) {
            String digits = node.asText().replaceAll("[^0-9]", "");
            if (!digits.isBlank()) {
                confidence = Integer.parseInt(digits);
            }
        }

        if (confidence <= 0 && (!"UNKNOWN".equalsIgnoreCase(department) || !"UNKNOWN".equalsIgnoreCase(category))) {
            return 75;
        }
        return Math.max(0, Math.min(100, confidence));
    }
}
