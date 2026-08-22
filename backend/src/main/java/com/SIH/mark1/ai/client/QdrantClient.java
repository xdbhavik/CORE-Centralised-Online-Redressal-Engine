package com.SIH.mark1.ai.client;

import com.SIH.mark1.ai.config.AIProperties;
import com.SIH.mark1.ai.qdrant.QdrantPoint;
import com.SIH.mark1.ai.qdrant.QdrantSearchResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP REST client for Qdrant Vector Database.
 * <p>
 * All Qdrant operations go through this single class.
 * Collections, upsert, search, delete — all REST calls are here.
 * <p>
 * API Reference: https://qdrant.github.io/qdrant/redoc/
 */
@Component
public class QdrantClient {

    private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);

    private final AIProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public QdrantClient(AIProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    // ──────────────────────────────────────────
    // Configuration Checks
    // ──────────────────────────────────────────

    /**
     * Returns true if Qdrant base-url is configured (non-blank).
     */
    public boolean isConfigured() {
        return properties.qdrant() != null
                && properties.qdrant().baseUrl() != null
                && !properties.qdrant().baseUrl().isBlank();
    }

    private String baseUrl() {
        return properties.qdrant().baseUrl();
    }

    // ──────────────────────────────────────────
    // Health
    // ──────────────────────────────────────────

    /**
     * Pings Qdrant to verify it is reachable.
     *
     * @return true if Qdrant returns HTTP 200
     */
    public boolean ping() {
        if (!isConfigured()) return false;
        try {
            restClient.get()
                    .uri(baseUrl() + "/healthz")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new RuntimeException("Qdrant health check failed: " + res.getStatusCode());
                    })
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.warn("Qdrant ping failed: {}", ex.getMessage());
            return false;
        }
    }

    // ──────────────────────────────────────────
    // Collection Management
    // ──────────────────────────────────────────

    /**
     * Checks if a collection exists in Qdrant.
     *
     * @param collectionName name of the collection
     * @return true if collection exists
     */
    public boolean collectionExists(String collectionName) {
        if (!isConfigured()) return false;
        try {
            String response = restClient.get()
                    .uri(baseUrl() + "/collections/" + collectionName)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        // 404 means collection does not exist — not an error for us
                    })
                    .body(String.class);
            return response != null && response.contains("\"status\":\"ok\"");
        } catch (Exception ex) {
            log.debug("Collection '{}' not found or Qdrant unreachable: {}", collectionName, ex.getMessage());
            return false;
        }
    }

    /**
     * Creates a new collection with COSINE distance metric.
     *
     * @param collectionName name of the collection to create
     * @param dimensions     vector dimension (must match embedding model output)
     */
    public void createCollection(String collectionName, int dimensions) {
        if (!isConfigured()) {
            log.warn("Qdrant not configured, skipping collection creation: {}", collectionName);
            return;
        }
        try {
            Map<String, Object> vectorsConfig = Map.of(
                    "size", dimensions,
                    "distance", "Cosine",
                    "on_disk", true
            );
            Map<String, Object> body = Map.of("vectors", vectorsConfig);

            restClient.put()
                    .uri(baseUrl() + "/collections/" + collectionName)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new RuntimeException("Failed to create collection '" + collectionName + "': " + res.getStatusCode());
                    })
                    .toBodilessEntity();

            log.info("✅ Qdrant collection created: '{}' (dimensions={}, distance=COSINE)", collectionName, dimensions);
        } catch (Exception ex) {
            log.error("❌ Failed to create Qdrant collection '{}': {}", collectionName, ex.getMessage());
            throw new RuntimeException("Qdrant collection creation failed: " + collectionName, ex);
        }
    }

    /**
     * Returns info about a collection (point count, status, etc.).
     *
     * @param collectionName collection name
     * @return raw JSON string from Qdrant, or null if not found
     */
    public String getCollectionInfo(String collectionName) {
        if (!isConfigured()) return null;
        try {
            return restClient.get()
                    .uri(baseUrl() + "/collections/" + collectionName)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.debug("Could not get info for collection '{}': {}", collectionName, ex.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────
    // Vector Operations
    // ──────────────────────────────────────────

    /**
     * Upserts (insert or update) a batch of points into a collection.
     *
     * @param collectionName target collection
     * @param points         list of QdrantPoint objects (id + vector + payload)
     */
    public void upsertPoints(String collectionName, List<QdrantPoint> points) {
        if (!isConfigured() || points == null || points.isEmpty()) return;
        try {
            Map<String, Object> body = Map.of("points", points);

            restClient.put()
                    .uri(baseUrl() + "/collections/" + collectionName + "/points")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw qdrantError("Upsert failed for collection '" + collectionName + "'", res);
                    })
                    .toBodilessEntity();

            log.debug("Upserted {} points into collection '{}'", points.size(), collectionName);
        } catch (Exception ex) {
            log.error("Failed to upsert points into '{}': {}", collectionName, ex.getMessage());
            throw new RuntimeException("Qdrant upsert failed", ex);
        }
    }

    /**
     * Performs an ANN (Approximate Nearest Neighbour) vector search.
     *
     * @param collectionName  collection to search
     * @param queryVector     embedding of the query
     * @param limit           max results to return
     * @param withPayload     if true, payloads are returned with results
     * @return list of scored search results
     */
    public List<QdrantSearchResult> searchPoints(String collectionName,
                                                  float[] queryVector,
                                                  int limit,
                                                  boolean withPayload) {
        if (!isConfigured()) return List.of();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("vector", queryVector);
            body.put("limit", limit);
            body.put("with_payload", withPayload);
            body.put("with_vector", false);

            String raw = restClient.post()
                    .uri(baseUrl() + "/collections/" + collectionName + "/points/search")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new RuntimeException("Search failed on '" + collectionName + "': " + res.getStatusCode());
                    })
                    .body(String.class);

            return parseSearchResults(raw);
        } catch (Exception ex) {
            log.error("Qdrant search failed on '{}': {}", collectionName, ex.getMessage());
            return List.of();
        }
    }

    /**
     * Deletes specific points by their IDs from a collection.
     *
     * @param collectionName collection name
     * @param ids            list of point IDs to delete
     */
    public void deletePoints(String collectionName, List<?> ids) {
        if (!isConfigured() || ids == null || ids.isEmpty()) return;
        try {
            Map<String, Object> body = Map.of(
                    "points", ids
            );

            restClient.post()
                    .uri(baseUrl() + "/collections/" + collectionName + "/points/delete")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new RuntimeException("Delete failed on '" + collectionName + "': " + res.getStatusCode());
                    })
                    .toBodilessEntity();

            log.debug("Deleted {} point(s) from collection '{}'", ids.size(), collectionName);
        } catch (Exception ex) {
            log.error("Failed to delete points from '{}': {}", collectionName, ex.getMessage());
        }
    }

    /**
     * Deletes all points matching a filter from a collection.
     * Used for bulk cleanup (e.g., delete all chunks from a specific source file).
     *
     * @param collectionName collection name
     * @param filterKey      payload field name to filter on (e.g., "source")
     * @param filterValue    value to match (e.g., "water.md")
     */
    public void deleteByFilter(String collectionName, String filterKey, String filterValue) {
        if (!isConfigured()) return;
        try {
            Map<String, Object> mustCondition = Map.of(
                    "key", filterKey,
                    "match", Map.of("value", filterValue)
            );
            Map<String, Object> filter = Map.of("must", List.of(mustCondition));
            Map<String, Object> body = Map.of("filter", filter);

            restClient.post()
                    .uri(baseUrl() + "/collections/" + collectionName + "/points/delete")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new RuntimeException("Delete-by-filter failed on '" + collectionName + "': " + res.getStatusCode());
                    })
                    .toBodilessEntity();

            log.debug("Deleted points from '{}' where {}={}", collectionName, filterKey, filterValue);
        } catch (Exception ex) {
            log.error("Failed to delete by filter from '{}': {}", collectionName, ex.getMessage());
        }
    }

    // ──────────────────────────────────────────
    // Internal Helpers
    // ──────────────────────────────────────────

    private RuntimeException qdrantError(String message, org.springframework.http.client.ClientHttpResponse res) {
        String status = "unknown";
        try {
            status = String.valueOf(res.getStatusCode());
        } catch (java.io.IOException ignored) {
            // keep default status label
        }
        try {
            byte[] bytes = res.getBody().readAllBytes();
            String body = new String(bytes, StandardCharsets.UTF_8);
            return new RuntimeException(message + ": " + status + " — " + body);
        } catch (java.io.IOException ex) {
            return new RuntimeException(message + ": " + status, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<QdrantSearchResult> parseSearchResults(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, Map.class);
            Object resultNode = parsed.get("result");
            if (!(resultNode instanceof List<?> resultList)) return List.of();
            return resultList.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> {
                        Map<String, Object> map = (Map<String, Object>) item;
                        String id = String.valueOf(map.get("id"));
                        float score = ((Number) map.getOrDefault("score", 0.0f)).floatValue();
                        Map<String, Object> payload = (Map<String, Object>) map.getOrDefault("payload", Map.of());
                        return new QdrantSearchResult(id, score, payload);
                    })
                    .toList();
        } catch (Exception ex) {
            log.error("Failed to parse Qdrant search response: {}", ex.getMessage());
            return List.of();
        }
    }
}
