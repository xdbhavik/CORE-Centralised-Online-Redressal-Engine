package com.SIH.mark1.ai.rag;

import com.SIH.mark1.ai.qdrant.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Assembles a ranked list of retrieved knowledge chunks into a single context string
 * that can be injected into the LLM prompt.
 * <p>
 * Updated to use the new {@link ScoredChunk} backed by Qdrant instead of in-memory
 * {@code VectorSearchService.ScoredChunk}.
 */
@Component
public class ContextAssembler {

    public String assemble(List<ScoredChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        for (ScoredChunk scoredChunk : chunks) {
            context.append("Source: ")
                    .append(scoredChunk.source());
            if (!scoredChunk.department().isBlank()) {
                context.append(" | Department: ").append(scoredChunk.department());
            }
            if (!scoredChunk.category().isBlank()) {
                context.append(" | Category: ").append(scoredChunk.category());
            }
            context.append(" (score: ")
                    .append(String.format("%.2f", scoredChunk.score()))
                    .append(")\n")
                    .append(scoredChunk.content())
                    .append("\n\n");
        }
        return context.toString().trim();
    }
}

