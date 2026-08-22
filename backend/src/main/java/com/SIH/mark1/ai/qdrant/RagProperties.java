package com.SIH.mark1.ai.qdrant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the configurable RAG collection list from application.properties.
 * <p>
 * To add a new collection, simply append to application.properties:
 * <pre>
 *   rag.collections[4]=my_new_collection
 * </pre>
 * QdrantCollectionInitializer will auto-create it on next startup.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(List<String> collections, Double minScore) {

    /**
     * Returns a safe, non-null list of collection names.
     */
    public List<String> safeCollections() {
        return collections == null ? List.of() : collections;
    }

    /**
     * Minimum cosine similarity score for a retrieved chunk to be injected
     * into the LLM prompt. Chunks below this threshold are treated as noise.
     * Defaults to 0.25 (suitable for the local-hashing embedding model).
     */
    public double resolvedMinScore() {
        return minScore == null ? 0.25d : minScore;
    }
}
