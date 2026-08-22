package com.SIH.mark1.ai.qdrant;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Builds Qdrant-compatible point IDs.
 * <p>
 * Qdrant accepts only unsigned integers or UUID strings — plain strings like
 * {@code "dept-1"} or {@code "chunk-abc123"} are rejected with HTTP 400.
 */
public final class QdrantPointId {

    private QdrantPointId() {
    }

    /** Numeric ID for a MySQL-backed entity (department, category, complaint). */
    public static long entity(long id) {
        return id;
    }

    /** Deterministic UUID for a knowledge document chunk (stable across re-index). */
    public static String chunkUuid(String source, int index) {
        String key = "chunk:" + source + "#" + index;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
