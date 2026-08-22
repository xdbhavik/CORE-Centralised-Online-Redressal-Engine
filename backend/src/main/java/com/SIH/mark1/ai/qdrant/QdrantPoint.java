package com.SIH.mark1.ai.qdrant;

import java.util.List;
import java.util.Map;

/**
 * Represents a single vector point to be stored in Qdrant.
 * <p>
 * Each point has:
 * - id: unique string identifier (e.g., "chunk-abc123", "dept-1", "cmp-456")
 * - vector: the embedding float array
 * - payload: arbitrary metadata stored alongside the vector
 */
public record QdrantPoint(
        Object id,
        List<Float> vector,
        Map<String, Object> payload
) {
    /**
     * Convenience factory from double[] embedding.
     * {@code id} must be an unsigned integer or UUID string — Qdrant rejects other formats.
     */
    public static QdrantPoint of(Object id, double[] embedding, Map<String, Object> payload) {
        List<Float> floatVector = new java.util.ArrayList<>(embedding.length);
        for (double v : embedding) {
            floatVector.add((float) v);
        }
        return new QdrantPoint(id, floatVector, payload);
    }
}
