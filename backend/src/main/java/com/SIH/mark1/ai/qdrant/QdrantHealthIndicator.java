package com.SIH.mark1.ai.qdrant;

import com.SIH.mark1.ai.client.QdrantClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot Actuator Health Indicator for Qdrant.
 * <p>
 * Exposes Qdrant status at:
 * <pre>GET /actuator/health → "qdrant": {"status": "UP"}</pre>
 * <p>
 * When UP, also reports:
 * - Qdrant base URL
 * - Configured collection names
 * - Per-collection point counts (if available)
 */
@Component
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
public class QdrantHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(QdrantHealthIndicator.class);

    private final QdrantClient qdrantClient;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public QdrantHealthIndicator(QdrantClient qdrantClient,
                                  RagProperties ragProperties,
                                  ObjectMapper objectMapper) {
        this.qdrantClient = qdrantClient;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Health health() {
        if (!qdrantClient.isConfigured()) {
            return Health.unknown()
                    .withDetail("reason", "Qdrant not configured (ai.qdrant.base-url is blank)")
                    .build();
        }

        boolean reachable = qdrantClient.ping();
        if (!reachable) {
            return Health.down()
                    .withDetail("reason", "Qdrant ping failed — service unreachable")
                    .build();
        }

        // Build per-collection details
        Map<String, Object> collectionDetails = new LinkedHashMap<>();
        for (String collection : ragProperties.safeCollections()) {
            try {
                String info = qdrantClient.getCollectionInfo(collection);
                long pointCount = extractPointCount(info);
                collectionDetails.put(collection, Map.of("points", pointCount));
            } catch (Exception ex) {
                collectionDetails.put(collection, Map.of("error", ex.getMessage()));
            }
        }

        return Health.up()
                .withDetail("collections", collectionDetails)
                .withDetail("configuredCollections", ragProperties.safeCollections().size())
                .build();
    }

    @SuppressWarnings("unchecked")
    private long extractPointCount(String json) {
        if (json == null || json.isBlank()) return -1L;
        try {
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            Object result = root.get("result");
            if (result instanceof Map<?, ?> resultMap) {
                Object config = resultMap.get("points_count");
                if (config instanceof Number n) return n.longValue();
            }
            return -1L;
        } catch (Exception ex) {
            log.debug("Could not parse point count from collection info: {}", ex.getMessage());
            return -1L;
        }
    }
}
