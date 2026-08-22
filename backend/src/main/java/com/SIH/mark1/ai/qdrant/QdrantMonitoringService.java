package com.SIH.mark1.ai.qdrant;

import com.SIH.mark1.ai.client.QdrantClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides Qdrant monitoring metrics for observability.
 * <p>
 * Exposes per-collection statistics that can be included in admin dashboards or
 * health responses.
 * <p>
 * Metrics available:
 * - Knowledge count per collection (Qdrant point count)
 * - Collection status (green/yellow/grey)
 * - Total indexed chunks across all collections
 */
@Service
public class QdrantMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(QdrantMonitoringService.class);

    private final QdrantClient qdrantClient;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public QdrantMonitoringService(QdrantClient qdrantClient,
                                    RagProperties ragProperties,
                                    ObjectMapper objectMapper) {
        this.qdrantClient = qdrantClient;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns a map of collection → point count for all configured RAG collections.
     * Returns -1 for a collection if it cannot be queried.
     *
     * @return map of collectionName → pointCount
     */
    public Map<String, Long> getCollectionPointCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (!qdrantClient.isConfigured()) {
            return counts;
        }
        for (String collection : ragProperties.safeCollections()) {
            counts.put(collection, getPointCount(collection));
        }
        return counts;
    }

    /**
     * Returns total points across all collections.
     */
    public long getTotalIndexedChunks() {
        return getCollectionPointCounts().values().stream()
                .filter(c -> c >= 0)
                .mapToLong(Long::longValue)
                .sum();
    }

    /**
     * Returns true if Qdrant is reachable and all collections exist.
     */
    public boolean isFullyOperational() {
        if (!qdrantClient.isConfigured() || !qdrantClient.ping()) return false;
        return ragProperties.safeCollections().stream()
                .allMatch(qdrantClient::collectionExists);
    }

    // ──────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private long getPointCount(String collectionName) {
        try {
            String info = qdrantClient.getCollectionInfo(collectionName);
            if (info == null || info.isBlank()) return -1L;
            Map<String, Object> root = objectMapper.readValue(info, Map.class);
            Object result = root.get("result");
            if (result instanceof Map<?, ?> resultMap) {
                Object count = resultMap.get("points_count");
                if (count instanceof Number n) return n.longValue();
            }
            return -1L;
        } catch (Exception ex) {
            log.debug("Could not get point count for '{}': {}", collectionName, ex.getMessage());
            return -1L;
        }
    }
}
