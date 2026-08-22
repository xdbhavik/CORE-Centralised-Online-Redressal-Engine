package com.SIH.mark1.ai.embedding;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dependency-free lexical embedding used as a <em>fallback only</em>.
 *
 * <p>This replaces the original {@code EmbeddingGenerator}, which had two fatal flaws:</p>
 * <ol>
 *   <li><strong>It destroyed non-Latin text.</strong> The old normaliser ran
 *       {@code replaceAll("[^a-z0-9\\s]", " ")}, which strips every Devanagari codepoint.
 *       A Hindi query therefore produced an all-zero vector, making cosine similarity
 *       undefined and guaranteeing that Hindi retrieval returned nothing. This class is
 *       Unicode-aware ({@code \p{L}\p{N}}) so Hindi, Hinglish and English all survive.</li>
 *   <li><strong>Whole-word hashing meant zero fuzzy matching.</strong> Two texts only
 *       collided on exact token equality, so "paani" never matched "water" — or even
 *       "paani" vs "pani". Adding character n-grams gives sub-word overlap, which recovers
 *       morphological variants and transliteration spelling drift.</li>
 * </ol>
 *
 * <p>It is still <em>not</em> semantic — it cannot know "paani" means "water". That is what
 * {@link OnnxEmbeddingProvider} is for. This exists so a missing model file degrades
 * retrieval quality rather than taking the assistant down.</p>
 */
@Component
public class HashingEmbeddingProvider implements EmbeddingProvider {

    /** Signed feature hashing needs two independent digests per feature. */
    private static final int MIN_NGRAM = 3;
    private static final int MAX_NGRAM = 5;

    private final int dimensions;

    public HashingEmbeddingProvider(com.SIH.mark1.ai.config.AIProperties properties) {
        int configured = properties.embedding() == null ? 0 : properties.embedding().dimensions();
        this.dimensions = configured > 0 ? configured : 384;
    }

    @Override
    public double[] embed(String text) {
        double[] vector = new double[dimensions];
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return vector;
        }

        // Word-level features capture exact terms (complaint numbers, ward names).
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                addFeature(vector, "w:" + token, 1.0d);
            }
        }

        // Character n-grams give fuzzy sub-word overlap across scripts and spellings.
        for (String gram : charNgrams(normalized)) {
            addFeature(vector, "g:" + gram, 0.5d);
        }

        sublinearScale(vector);
        l2Normalize(vector);
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelId() {
        return "hashing-ngram-" + dimensions;
    }

    @Override
    public boolean isSemantic() {
        return false;
    }

    // ──────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────

    /**
     * Unicode-aware normalisation. Keeps letters/digits of <em>any</em> script, so
     * Devanagari input is preserved instead of being erased.
     */
    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder builder = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char ch = decomposed.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toLowerCase(ch));
            } else {
                builder.append(' ');
            }
        }
        return builder.toString().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private List<String> charNgrams(String normalized) {
        List<String> grams = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() < MIN_NGRAM) {
                continue;
            }
            String padded = "^" + token + "$";
            for (int n = MIN_NGRAM; n <= MAX_NGRAM; n++) {
                for (int i = 0; i + n <= padded.length(); i++) {
                    grams.add(padded.substring(i, i + n));
                }
            }
        }
        return grams;
    }

    /**
     * Signed feature hashing: a second hash bit decides the sign, which keeps collision
     * error zero-mean instead of always inflating buckets the way the old code did.
     */
    private void addFeature(double[] vector, String feature, double weight) {
        byte[] digest = sha256(feature);
        int index = Math.floorMod(intAt(digest, 0), vector.length);
        double sign = (digest[4] & 1) == 0 ? 1.0d : -1.0d;
        vector[index] += sign * weight;
    }

    /** Damps runaway term frequency so one repeated word cannot dominate the vector. */
    private void sublinearScale(double[] vector) {
        for (int i = 0; i < vector.length; i++) {
            double value = vector[i];
            if (value != 0.0d) {
                vector[i] = Math.signum(value) * (1.0d + Math.log(Math.abs(value)));
            }
        }
    }

    private void l2Normalize(double[] vector) {
        double sum = 0.0d;
        for (double value : vector) {
            sum += value * value;
        }
        if (sum == 0.0d) {
            return;
        }
        double length = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= length;
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JLS; this branch is unreachable in practice.
            byte[] fallback = new byte[8];
            int hash = value.hashCode();
            for (int i = 0; i < 4; i++) {
                fallback[i] = (byte) (hash >>> (8 * i));
            }
            return fallback;
        }
    }

    private int intAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }
}
