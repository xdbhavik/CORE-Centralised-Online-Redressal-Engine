package com.SIH.mark1.ai.qdrant;

import com.SIH.mark1.ai.client.QdrantClient;
import com.SIH.mark1.ai.config.AIProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Automatically creates Qdrant collections on application startup.
 * <p>
 * Collections are driven by the {@code rag.collections} list in application.properties.
 * Adding a new collection requires ONLY a config change — no code change.
 * <p>
 * Flow:
 * <pre>
 *   @PostConstruct
 *       ↓
 *   Loop rag.collections
 *       ↓
 *   collectionExists(name)?
 *       YES → skip (log)
 *       NO  → createCollection(name, ai.embedding.dimensions)
 *       ↓
 *   Log summary: "X collections verified, Y created"
 * </pre>
 * <p>
 * This bean is only active when {@code ai.qdrant.base-url} is non-blank.
 * If Qdrant is unreachable, a WARNING is logged and the app continues —
 * the app will NOT crash at startup due to Qdrant being down.
 */
@Component
@ConditionalOnProperty(name = "ai.qdrant.base-url", matchIfMissing = false)
public class QdrantCollectionInitializer {

    private static final Logger log = LoggerFactory.getLogger(QdrantCollectionInitializer.class);

    private final QdrantClient qdrantClient;
    private final RagProperties ragProperties;
    private final AIProperties aiProperties;

    public QdrantCollectionInitializer(QdrantClient qdrantClient,
                                        RagProperties ragProperties,
                                        AIProperties aiProperties) {
        this.qdrantClient = qdrantClient;
        this.ragProperties = ragProperties;
        this.aiProperties = aiProperties;
    }

    @PostConstruct
    public void initializeCollections() {
        if (!qdrantClient.isConfigured()) {
            log.info("Qdrant not configured (ai.qdrant.base-url is blank), skipping collection initialization.");
            return;
        }

        // Verify Qdrant is reachable before attempting any collection operations
        if (!qdrantClient.ping()) {
            log.warn("⚠️  Qdrant is unreachable at '{}'. Collections will NOT be initialized. " +
                    "Start Qdrant and restart the application to initialize.", aiProperties.qdrant().baseUrl());
            return;
        }

        int dimensions = aiProperties.embedding().dimensions();
        log.info("🚀 Qdrant collection initialization started. Embedding dimensions: {}", dimensions);

        int existing = 0;
        int created = 0;

        for (String collectionName : ragProperties.safeCollections()) {
            try {
                if (qdrantClient.collectionExists(collectionName)) {
                    log.info("  ✓ Collection '{}' already exists — skipping.", collectionName);
                    existing++;
                } else {
                    log.info("  ➕ Collection '{}' not found — creating...", collectionName);
                    qdrantClient.createCollection(collectionName, dimensions);
                    created++;
                }
            } catch (Exception ex) {
                log.error("  ❌ Failed to initialize collection '{}': {}", collectionName, ex.getMessage());
            }
        }

        log.info("✅ Qdrant initialization complete — {} existing, {} newly created.",
                existing, created);
    }
}
