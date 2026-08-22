package com.SIH.mark1.ai.qdrant;

import com.SIH.mark1.ai.client.QdrantClient;
import com.SIH.mark1.ai.config.AIProperties;
import com.SIH.mark1.ai.rag.KnowledgeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for all Qdrant vector indexing operations.
 * <p>
 * Replaces the old in-memory {@code List<KnowledgeChunk>} approach.
 * Knowledge chunks are now persisted into Qdrant with full metadata payloads.
 * <p>
 * Responsibilities:
 * - Upsert single or batch chunks
 * - Delete chunks by ID or by payload filter (e.g., by source file)
 * - Reindex an entire collection (delete-all + re-upsert)
 */
@Service
public class QdrantIndexService {

    private static final Logger log = LoggerFactory.getLogger(QdrantIndexService.class);

    private final QdrantClient qdrantClient;
    private final AIProperties aiProperties;

    public QdrantIndexService(QdrantClient qdrantClient, AIProperties aiProperties) {
        this.qdrantClient = qdrantClient;
        this.aiProperties = aiProperties;
    }

    /**
     * Upserts a batch of chunks (with their embeddings) into the specified Qdrant collection.
     *
     * @param collectionName target collection
     * @param chunks         chunk list — content and metadata
     * @param embeddings     parallel array of embeddings, embeddings[i] corresponds to chunks[i]
     */
    public void upsertChunks(String collectionName, List<KnowledgeChunk> chunks, List<double[]> embeddings) {
        if (!qdrantClient.isConfigured()) {
            log.debug("Qdrant not configured — skipping upsert into '{}'", collectionName);
            return;
        }
        if (chunks.isEmpty()) {
            log.debug("No chunks to upsert into '{}'", collectionName);
            return;
        }

        List<QdrantPoint> points = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            double[] embedding = embeddings.get(i);
            Map<String, Object> payload = buildPayload(chunk);
            points.add(QdrantPoint.of(chunk.id(), embedding, payload));
        }

        qdrantClient.upsertPoints(collectionName, points);
        log.info("Indexed {} chunks into Qdrant collection '{}'", points.size(), collectionName);
    }

    /**
     * Upserts a single chunk with its embedding.
     *
     * @param collectionName target collection
     * @param chunk          the chunk to upsert
     * @param embedding      its vector embedding
     */
    public void upsertChunk(String collectionName, KnowledgeChunk chunk, double[] embedding) {
        upsertChunks(collectionName, List.of(chunk), List.of(embedding));
    }

    /**
     * Deletes a specific point by ID from a collection.
     *
     * @param collectionName collection name
     * @param pointId        the point ID to delete
     */
    public void deleteChunk(String collectionName, Object pointId) {
        qdrantClient.deletePoints(collectionName, List.of(pointId));
        log.debug("Deleted point '{}' from collection '{}'", pointId, collectionName);
    }

    /**
     * Deletes all points from a collection that match a source file name.
     * Used when a knowledge document is updated — delete old chunks, upsert new ones.
     *
     * @param collectionName collection name
     * @param sourceFile     the source file name (e.g., "water_supply.md")
     */
    public void deleteBySource(String collectionName, String sourceFile) {
        qdrantClient.deleteByFilter(collectionName, "source", sourceFile);
        log.info("Deleted all chunks from '{}' where source='{}'", collectionName, sourceFile);
    }

    /**
     * Returns the configured embedding dimensions.
     */
    public int dimensions() {
        return aiProperties.embedding().dimensions();
    }

    // ──────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────

    private Map<String, Object> buildPayload(KnowledgeChunk chunk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", chunk.content());
        payload.put("source", chunk.source());
        payload.put("collection", chunk.collection());
        payload.put("department", chunk.department());
        payload.put("category", chunk.category());
        payload.put("language", chunk.language());
        payload.put("version", chunk.version());
        payload.put("updatedAt", chunk.updatedAt());
        return payload;
    }
}
