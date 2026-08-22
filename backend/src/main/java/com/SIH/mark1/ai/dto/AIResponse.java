package com.SIH.mark1.ai.dto;

public record AIResponse(
        String title,
        String summary,
        String department,
        String category,
        String priority,
        int confidence,
        String scope,
        String resourceType,
        String resourceIdentifier
) {
    public AIResponse(String title, String summary, String department, String category, String priority, int confidence) {
        this(title, summary, department, category, priority, confidence, "LOCAL_AREA", "UNKNOWN", null);
    }

    public AIResponse {
        scope = scope == null || scope.isBlank() ? "LOCAL_AREA" : scope;
        resourceType = resourceType == null || resourceType.isBlank() ? "UNKNOWN" : resourceType;
    }
}
