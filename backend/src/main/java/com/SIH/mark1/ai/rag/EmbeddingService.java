package com.SIH.mark1.ai.rag;

import com.SIH.mark1.ai.embedding.EmbeddingProvider;
import com.SIH.mark1.ai.embedding.HashingEmbeddingProvider;
import com.SIH.mark1.ai.embedding.OnnxEmbeddingProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single entry point for producing embeddings, and the one place that decides
 * <em>which</em> {@link EmbeddingProvider} the whole application uses.
 *
 * <p>Centralising the choice matters because a vector is only meaningful relative to the
 * model that produced it. If indexing used one provider and querying another, every cosine
 * score would be noise — and nothing would visibly fail. Routing all callers (RAG retrieval,
 * knowledge indexing, and complaint duplicate detection) through here makes that mistake
 * impossible to make by accident.</p>
 *
 * <p>Selection: prefer the semantic ONNX model; fall back to lexical hashing if it did not
 * load. The active {@link #modelId()} is written into every Qdrant payload so a later model
 * change can be detected and trigger a re-index.</p>
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    /** Bounded so a flood of distinct queries cannot grow the cache without limit. */
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final OnnxEmbeddingProvider onnxProvider;
    private final HashingEmbeddingProvider hashingProvider;
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    private EmbeddingProvider active;

    public EmbeddingService(OnnxEmbeddingProvider onnxProvider,
                            HashingEmbeddingProvider hashingProvider) {
        this.onnxProvider = onnxProvider;
        this.hashingProvider = hashingProvider;
        this.active = hashingProvider;
    }

    @PostConstruct
    void selectProvider() {
        if (onnxProvider.isAvailable()) {
            this.active = onnxProvider;
            log.info("🧠 Embeddings: using semantic model '{}' ({} dims)",
                    active.modelId(), active.dimensions());
        } else {
            this.active = hashingProvider;
            log.warn("🧠 Embeddings: semantic model unavailable — using lexical fallback '{}'. "
                    + "Cross-language retrieval (Hindi query → English document) will NOT work "
                    + "until the model loads.", active.modelId());
        }
    }

    /** Embeds a single text, memoised. */
    public double[] embed(String text) {
        String key = text == null ? "" : text;
        double[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        double[] vector = active.embed(key);
        if (cache.size() < MAX_CACHE_ENTRIES) {
            cache.put(key, vector);
        }
        return vector;
    }

    /** Embeds many texts, using batched inference where the provider supports it. */
    public double[][] embedAll(List<String> texts) {
        return active.embedAll(texts);
    }

    /** Identifier of the provider currently in use; recorded in Qdrant payloads. */
    public String modelId() {
        return active.modelId();
    }

    public int dimensions() {
        return active.dimensions();
    }

    /** Whether retrieval currently has true semantic matching, or only lexical overlap. */
    public boolean isSemantic() {
        return active.isSemantic();
    }

    /**
     * True when every value is zero, which means the text could not be embedded.
     * Such vectors must never be indexed: cosine similarity against them is undefined
     * and Qdrant would return meaningless neighbours.
     */
    public static boolean isZeroVector(double[] vector) {
        if (vector == null || vector.length == 0) {
            return true;
        }
        for (double value : vector) {
            if (value != 0.0d) {
                return false;
            }
        }
        return true;
    }

    public void clearCache() {
        cache.clear();
    }
}
