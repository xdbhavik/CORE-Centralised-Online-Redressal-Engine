package com.SIH.mark1.ai.embedding;

/**
 * Strategy interface for turning text into a dense vector.
 *
 * <p>Introduced so the retrieval stack is no longer hard-wired to one implementation.
 * Two providers ship today:</p>
 * <ul>
 *   <li>{@link OnnxEmbeddingProvider} — a real multilingual sentence-transformer. Primary.</li>
 *   <li>{@link HashingEmbeddingProvider} — dependency-free lexical fallback used when the
 *       ONNX model cannot be loaded, so retrieval degrades instead of dying.</li>
 * </ul>
 *
 * <p><strong>Critical invariant:</strong> every vector written to Qdrant must come from the
 * same provider that later embeds the query. Mixing providers silently produces garbage
 * similarity scores, which is why {@link EmbeddingService} resolves exactly one provider at
 * startup and records {@link #modelId()} in each Qdrant payload.</p>
 */
public interface EmbeddingProvider {

    /**
     * Embeds text into a unit-length vector of exactly {@link #dimensions()} values.
     * Implementations must never throw and never return {@code null}; on internal failure
     * they should return a zero vector so the caller can detect and skip it.
     */
    double[] embed(String text);

    /** Vector length produced by this provider. Must match the Qdrant collection size. */
    int dimensions();

    /**
     * Stable identifier for this provider + model, stored alongside every indexed chunk.
     * Changing it triggers a re-index, which is what makes model upgrades safe.
     */
    String modelId();

    /** Whether this provider produces true semantic embeddings (vs. a lexical approximation). */
    default boolean isSemantic() {
        return true;
    }

    /**
     * Embeds a batch. Overridden by providers that can amortise model invocation.
     */
    default double[][] embedAll(java.util.List<String> texts) {
        double[][] out = new double[texts.size()][];
        for (int i = 0; i < texts.size(); i++) {
            out[i] = embed(texts.get(i));
        }
        return out;
    }
}
