package com.SIH.mark1.ai.embedding;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thin compatibility shim over the active {@link EmbeddingProvider}.
 *
 * <p>This class used to <em>contain</em> the embedding algorithm (384-bucket word hashing),
 * and {@code KnowledgeSyncService} called it directly while RAG retrieval went through
 * {@code EmbeddingService}. Two independent paths to two different code paths is exactly how
 * an index and its queries drift apart, so the logic moved into
 * {@link HashingEmbeddingProvider} / {@link OnnxEmbeddingProvider} and this type now simply
 * delegates. Existing callers keep working while sharing one provider.</p>
 *
 * <p>Prefer injecting {@code EmbeddingService} in new code.</p>
 */
@Component
public class EmbeddingGenerator {

    private final com.SIH.mark1.ai.rag.EmbeddingService embeddingService;

    // @Lazy breaks the cycle: EmbeddingService → providers, and legacy callers → this shim.
    public EmbeddingGenerator(@Lazy com.SIH.mark1.ai.rag.EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public double[] generate(String text) {
        return embeddingService.embed(text);
    }

    public double[][] generateAll(List<String> texts) {
        return embeddingService.embedAll(texts);
    }

    public int dimensions() {
        return embeddingService.dimensions();
    }
}
