package com.SIH.mark1.ai.rag;

import com.SIH.mark1.ai.client.QdrantClient;
import com.SIH.mark1.ai.qdrant.QdrantIndexService;
import com.SIH.mark1.ai.qdrant.QdrantSearchResult;
import com.SIH.mark1.ai.qdrant.RagProperties;
import com.SIH.mark1.ai.qdrant.ScoredChunk;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages knowledge indexing and retrieval backed by Qdrant.
 * <p>
 * On startup ({@code @PostConstruct}):
 * <ol>
 *   <li>Load all Markdown documents via {@link DocumentLoader}</li>
 *   <li>Split into chunks via {@link ChunkService}</li>
 *   <li>Generate embeddings via {@link EmbeddingService}</li>
 *   <li>Upsert into Qdrant via {@link QdrantIndexService}</li>
 * </ol>
 * <p>
 * On retrieve:
 * <ol>
 *   <li>Embed the query</li>
 *   <li>Search configured Qdrant collections via ANN</li>
 *   <li>Merge, sort by score, return top-K</li>
 * </ol>
 * <p>
 * If Qdrant is not configured or unreachable, indexing is skipped gracefully.
 */
@Service
@DependsOn("qdrantCollectionInitializer")
public class KnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetriever.class);

    /**
     * Collections to search during retrieval.
     * knowledge_base collections — ordered by relevance priority.
     */
    private static final List<String> SEARCH_COLLECTIONS = List.of(
            "department_knowledge",
            "category_knowledge",
            "sop_knowledge",
            "faq_knowledge",
            "government_rules"
    );

    private final DocumentLoader documentLoader;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final QdrantIndexService qdrantIndexService;
    private final QdrantClient qdrantClient;
    private final RagProperties ragProperties;

    /** Tracks how many chunks were indexed in the last @PostConstruct run */
    private volatile int lastIndexedCount = 0;

    public KnowledgeRetriever(DocumentLoader documentLoader,
                               ChunkService chunkService,
                               EmbeddingService embeddingService,
                               QdrantIndexService qdrantIndexService,
                               QdrantClient qdrantClient,
                               RagProperties ragProperties) {
        this.documentLoader = documentLoader;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.qdrantIndexService = qdrantIndexService;
        this.qdrantClient = qdrantClient;
        this.ragProperties = ragProperties;
    }

    /**
     * Indexes all knowledge documents into Qdrant on application startup.
     * Skipped gracefully if Qdrant is not configured or unreachable.
     */
    @PostConstruct
    public void index() {
        if (!qdrantClient.isConfigured()) {
            log.info("Qdrant not configured — knowledge will NOT be indexed. Retrieval will return empty context.");
            return;
        }
        if (!qdrantClient.ping()) {
            log.warn("⚠️  Qdrant unreachable — skipping knowledge indexing at startup.");
            return;
        }

        log.info("📚 Starting knowledge indexing into Qdrant...");
        int total = 0;

        for (DocumentLoader.LoadedDocument document : documentLoader.load()) {
            List<String> rawChunks = chunkService.chunk(document.content());
            List<KnowledgeChunk> chunks = new ArrayList<>();
            List<double[]> embeddings = new ArrayList<>();

            for (int i = 0; i < rawChunks.size(); i++) {
                String text = rawChunks.get(i);
                String chunkId = DocumentLoader.chunkId(document.source(), i);
                KnowledgeChunk chunk = new KnowledgeChunk(
                        chunkId,
                        document.source(),
                        document.collection(),
                        text,
                        document.department(),
                        document.category(),
                        document.language(),
                        document.version(),
                        document.updatedAt()
                );
                chunks.add(chunk);
                embeddings.add(embeddingService.embed(text));
            }

            qdrantIndexService.upsertChunks(document.collection(), chunks, embeddings);
            total += chunks.size();
        }

        lastIndexedCount = total;
        log.info("✅ Knowledge indexing complete — {} chunks indexed into Qdrant.", total);
    }

    /**
     * Retrieves the top-K most relevant chunks from Qdrant for the given query.
     * Searches across all configured knowledge collections and merges results by score.
     *
     * @param query the user's complaint or query text
     * @param limit max number of results to return (across all collections)
     * @return sorted list of scored chunks (highest score first)
     */
    public List<ScoredChunk> retrieve(String query, int limit) {
        if (!qdrantClient.isConfigured()) {
            log.debug("Qdrant not configured — returning empty context for retrieval.");
            return List.of();
        }

        double[] queryEmbedding = embeddingService.embed(query);
        float[] floatVector = toFloat(queryEmbedding);

        List<ScoredChunk> allResults = new ArrayList<>();
        for (String collection : SEARCH_COLLECTIONS) {
            List<QdrantSearchResult> results = qdrantClient.searchPoints(collection, floatVector, limit, true);
            results.stream()
                    .map(r -> ScoredChunk.from(r, collection))
                    .forEach(allResults::add);
        }

        // Filter out low-score noise, sort by score descending, return top limit
        double minScore = ragProperties.resolvedMinScore();
        List<ScoredChunk> filtered = allResults.stream()
                .filter(chunk -> chunk.score() >= minScore)
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .limit(limit)
                .toList();
        if (filtered.isEmpty() && !allResults.isEmpty()) {
            log.debug("All {} retrieved chunks were below min-score {} — returning empty context.",
                    allResults.size(), minScore);
        }
        return filtered;
    }

    /**
     * Triggers a fresh re-index of all documents (useful for admin-triggered reindex).
     */
    public void reindex() {
        index();
    }

    /**
     * Returns the number of chunks indexed in the last startup run.
     */
    public int size() {
        return lastIndexedCount;
    }

    // ──────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────

    private float[] toFloat(double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            floats[i] = (float) doubles[i];
        }
        return floats;
    }
}

