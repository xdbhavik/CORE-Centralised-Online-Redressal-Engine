package com.SIH.mark1.ai.qdrant;

import java.util.Map;

/**
 * A vector search result with its score and associated chunk content.
 * <p>
 * This replaces the old {@code VectorSearchService.ScoredChunk} which held a full
 * {@code KnowledgeChunk} including an in-memory embedding array.
 * <p>
 * Now the embedding lives in Qdrant; only the payload (metadata + text) is returned.
 */
public record ScoredChunk(
        String id,           // Qdrant point ID
        String collection,   // which collection this came from
        String content,      // the chunk text (from payload)
        String source,       // source file (from payload)
        String department,   // department metadata (from payload)
        String category,     // category metadata (from payload)
        Map<String, Object> payload, // full raw payload
        float score          // cosine similarity score from Qdrant
) {
    /**
     * Factory method to build a ScoredChunk from a QdrantSearchResult.
     *
     * @param result     the raw Qdrant search result
     * @param collection the collection this result came from
     */
    public static ScoredChunk from(QdrantSearchResult result, String collection) {
        return new ScoredChunk(
                result.id(),
                collection,
                result.payloadString("content"),
                result.payloadString("source"),
                result.payloadString("department"),
                result.payloadString("category"),
                result.payload(),
                result.score()
        );
    }
}
