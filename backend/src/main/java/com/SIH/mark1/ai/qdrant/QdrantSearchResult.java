package com.SIH.mark1.ai.qdrant;

import java.util.Map;

/**
 * Represents a single result returned from a Qdrant vector search.
 * <p>
 * Qdrant returns results with:
 * - id: the point identifier
 * - score: cosine similarity score (higher = more similar)
 * - payload: the metadata stored when the point was upserted
 */
public record QdrantSearchResult(
        String id,
        float score,
        Map<String, Object> payload
) {
    /**
     * Returns a payload value as String, or empty string if absent.
     */
    public String payloadString(String key) {
        Object val = payload.get(key);
        return val == null ? "" : String.valueOf(val);
    }

    /**
     * Returns a payload value as int, or 0 if absent/unparseable.
     */
    public int payloadInt(String key) {
        Object val = payload.get(key);
        if (val == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
