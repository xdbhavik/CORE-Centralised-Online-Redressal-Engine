package com.SIH.mark1.ai.embedding;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import com.SIH.mark1.ai.config.AIProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Real semantic embeddings from a multilingual sentence-transformer running locally
 * through DJL + ONNX Runtime.
 *
 * <p>This is the component that actually fixes retrieval. The previous implementation was
 * bag-of-words feature hashing, which could only match on exact shared tokens — so a Hindi
 * or Hinglish complaint ("paani nahi aa raha") never retrieved the English knowledge base
 * article it belonged to ("Water Supply"). A sentence-transformer maps both to nearby points
 * in vector space, which is the whole premise RAG depends on.</p>
 *
 * <p><strong>Model:</strong> {@code paraphrase-multilingual-MiniLM-L12-v2} — 384 dimensions,
 * chosen deliberately so it matches the existing {@code ai.embedding.dimensions=384} and the
 * Qdrant collections already created at that size. Upgrading the model therefore needs only a
 * re-index, not a collection migration.</p>
 *
 * <p><strong>Failure behaviour:</strong> the model is fetched on first startup and cached on
 * disk. If it cannot be loaded (no network on first boot, corrupted cache, unsupported
 * platform) this provider reports {@link #isAvailable()} as {@code false} and
 * {@link EmbeddingService} transparently falls back to {@link HashingEmbeddingProvider}.
 * Degraded retrieval beats a dead application.</p>
 */
@Component
public class OnnxEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingProvider.class);

    /** Sentence-transformer with 384-dim output, matching the configured Qdrant collections. */
    private static final String DEFAULT_MODEL =
            "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2";

    private final AIProperties properties;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private volatile boolean available = false;
    private int resolvedDimensions;

    public OnnxEmbeddingProvider(AIProperties properties) {
        this.properties = properties;
        this.resolvedDimensions = properties.embedding() == null || properties.embedding().dimensions() <= 0
                ? 384
                : properties.embedding().dimensions();
    }

    @PostConstruct
    void load() {
        String modelId = modelName();
        try {
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelId)
                    .optEngine("PyTorch")
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .build();

            this.model = criteria.loadModel();
            this.predictor = model.newPredictor();

            // Probe once so a broken model surfaces here at startup rather than mid-request,
            // and so we can verify the real vector width against the configured one.
            float[] probe = predictor.predict("connectivity probe");
            if (probe == null || probe.length == 0) {
                throw new IllegalStateException("model returned an empty vector");
            }

            if (probe.length != resolvedDimensions) {
                // Refuse to run rather than write mis-sized vectors that Qdrant would reject
                // (or worse, silently score against a differently-shaped index).
                throw new IllegalStateException(
                        "embedding width mismatch: model produces " + probe.length
                                + " but ai.embedding.dimensions=" + resolvedDimensions
                                + ". Update the property and recreate the Qdrant collections.");
            }

            this.available = true;
            log.info("✅ Semantic embedding model loaded: {} ({} dimensions)", modelId, probe.length);
        } catch (Throwable ex) {
            // Throwable, not Exception: a missing native ONNX/PyTorch binary surfaces as
            // UnsatisfiedLinkError / NoClassDefFoundError, and that must still degrade cleanly.
            this.available = false;
            log.warn("⚠️  Could not load semantic embedding model '{}' — falling back to lexical "
                    + "hashing embeddings. Retrieval quality will be reduced (no cross-language "
                    + "matching). Cause: {}", modelId, ex.toString());
        }
    }

    @PreDestroy
    void unload() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
    }

    /** Whether the model loaded successfully and this provider can serve embeddings. */
    public boolean isAvailable() {
        return available;
    }

    @Override
    public double[] embed(String text) {
        if (!available || text == null || text.isBlank()) {
            return new double[resolvedDimensions];
        }
        try {
            float[] raw = predictor.predict(text);
            return l2Normalize(raw);
        } catch (Exception ex) {
            log.warn("Embedding failed, returning zero vector: {}", ex.getMessage());
            return new double[resolvedDimensions];
        }
    }

    @Override
    public double[][] embedAll(List<String> texts) {
        if (!available || texts == null || texts.isEmpty()) {
            return new double[texts == null ? 0 : texts.size()][resolvedDimensions];
        }
        try {
            // Batched inference is markedly faster than per-chunk calls during a full reindex.
            List<float[]> batch = predictor.batchPredict(texts);
            double[][] out = new double[batch.size()][];
            for (int i = 0; i < batch.size(); i++) {
                out[i] = l2Normalize(batch.get(i));
            }
            return out;
        } catch (Exception ex) {
            log.warn("Batch embedding failed, falling back to sequential: {}", ex.getMessage());
            return EmbeddingProvider.super.embedAll(texts);
        }
    }

    @Override
    public int dimensions() {
        return resolvedDimensions;
    }

    @Override
    public String modelId() {
        return modelName();
    }

    private String modelName() {
        String configured = properties.embedding() == null ? null : properties.embedding().model();
        if (configured == null || configured.isBlank() || configured.startsWith("local-hashing")) {
            return DEFAULT_MODEL;
        }
        return configured;
    }

    /**
     * Cosine similarity in Qdrant assumes unit vectors; normalising here keeps scores
     * comparable across both providers.
     */
    private double[] l2Normalize(float[] raw) {
        double[] vector = new double[raw.length];
        double sum = 0.0d;
        for (int i = 0; i < raw.length; i++) {
            vector[i] = raw[i];
            sum += vector[i] * vector[i];
        }
        if (sum > 0.0d) {
            double length = Math.sqrt(sum);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= length;
            }
        }
        return vector;
    }
}
