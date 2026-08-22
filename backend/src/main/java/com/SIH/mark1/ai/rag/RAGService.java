package com.SIH.mark1.ai.rag;

import com.SIH.mark1.ai.qdrant.ScoredChunk;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade service that combines knowledge retrieval and context assembly.
 * <p>
 * Used by {@code ComplaintAnalysisService} and {@code ChatAssistantService}
 * to get a ready-to-inject context string from Qdrant.
 */
@Service
public class RAGService {

    private final KnowledgeRetriever knowledgeRetriever;
    private final ContextAssembler contextAssembler;

    public RAGService(KnowledgeRetriever knowledgeRetriever, ContextAssembler contextAssembler) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.contextAssembler = contextAssembler;
    }

    /**
     * Retrieves top-5 relevant chunks from Qdrant and assembles them into a context string.
     *
     * @param query the complaint or question to search with
     * @return RagContext containing the raw chunks and the assembled context string
     */
    public RagContext retrieveContext(String query) {
        List<ScoredChunk> chunks = knowledgeRetriever.retrieve(query, 5);
        return new RagContext(chunks, contextAssembler.assemble(chunks));
    }

    /**
     * Triggers a full re-index of all knowledge documents into Qdrant.
     */
    public void reindex() {
        knowledgeRetriever.reindex();
    }

    /**
     * Returns the number of chunks indexed in the last run.
     */
    public int indexedChunks() {
        return knowledgeRetriever.size();
    }

    /**
     * Represents the result of a RAG retrieval operation.
     *
     * @param chunks  the scored knowledge chunks retrieved from Qdrant
     * @param context the assembled context string to inject into the LLM prompt
     */
    public record RagContext(List<ScoredChunk> chunks, String context) {
    }
}

