package com.SIH.mark1.ai.embedding;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmbeddingCache {

    private final Map<String, double[]> cache = new ConcurrentHashMap<>();
    private final EmbeddingGenerator embeddingGenerator;

    public EmbeddingCache(EmbeddingGenerator embeddingGenerator) {
        this.embeddingGenerator = embeddingGenerator;
    }

    public double[] get(String text) {
        return cache.computeIfAbsent(text == null ? "" : text, embeddingGenerator::generate);
    }

    public void clear() {
        cache.clear();
    }
}
