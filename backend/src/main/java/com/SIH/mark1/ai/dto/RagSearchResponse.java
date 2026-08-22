package com.SIH.mark1.ai.dto;

import java.util.List;

public record RagSearchResponse(String query, List<SearchResult> results) {
    public record SearchResult(String source, String content, double score) {
    }
}
