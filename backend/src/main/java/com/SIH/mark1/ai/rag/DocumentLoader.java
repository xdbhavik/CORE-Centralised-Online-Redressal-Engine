package com.SIH.mark1.ai.rag;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.SIH.mark1.ai.qdrant.QdrantPointId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads knowledge documents from the classpath {@code resources/knowledge/} directory.
 * <p>
 * Folder structure convention:
 * <pre>
 *   knowledge/
 *     departments/         → collection = "department_knowledge", department = "departments"
 *       water_supply.md    → category = "water supply"
 *     categories/          → collection = "category_knowledge"
 *     resolution_sla/      → collection = "sop_knowledge"
 *     faq/                 → collection = "faq_knowledge"
 *     government_orders/   → collection = "government_rules"
 * </pre>
 * <p>
 * Documents are NOT loaded into RAM anymore — they are handed to
 * {@code QdrantIndexService} for persistent storage in Qdrant.
 */
@Component
public class DocumentLoader {

    private static final String KNOWLEDGE_PATTERN = "classpath*:/knowledge/**/*.md";

    /** Maps folder names to Qdrant collection names */
    private static final java.util.Map<String, String> FOLDER_TO_COLLECTION = java.util.Map.of(
            "departments", "department_knowledge",
            "categories", "category_knowledge",
            "resolution_sla", "sop_knowledge",
            "faq", "faq_knowledge",
            "government_orders", "government_rules",
            "policies", "government_rules"
    );

    public List<LoadedDocument> load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<LoadedDocument> documents = new ArrayList<>();
        try {
            for (Resource resource : resolver.getResources(KNOWLEDGE_PATTERN)) {
                String fileName = resource.getFilename() == null
                        ? resource.getDescription()
                        : resource.getFilename();
                String urlPath = resource.getURL().toString();
                String folderName = resolveFolderName(urlPath);
                String collection = FOLDER_TO_COLLECTION.getOrDefault(folderName, "department_knowledge");
                String department = toDisplayName(folderName);
                String category = toDisplayName(stripExtension(fileName));
                String content = resource.getContentAsString(StandardCharsets.UTF_8);

                documents.add(new LoadedDocument(
                        fileName,
                        collection,
                        content,
                        department,
                        category,
                        "en",
                        "1.0",
                        LocalDate.now().toString()
                ));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load AI knowledge files", ex);
        }
        return documents;
    }

    /**
     * Generates a stable, deterministic Qdrant point ID based on source file + chunk index.
     * Same input always produces same UUID — safe for upsert (idempotent).
     */
    public static String chunkId(String source, int index) {
        return QdrantPointId.chunkUuid(source, index);
    }

    // ──────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────

    private String resolveFolderName(String path) {
        String normalized = path.replace("\\", "/");
        int knowledgeIdx = normalized.indexOf("/knowledge/");
        if (knowledgeIdx < 0) return "knowledge";
        String remainder = normalized.substring(knowledgeIdx + "/knowledge/".length());
        int slash = remainder.indexOf('/');
        return slash < 0 ? "knowledge" : remainder.substring(0, slash);
    }

    private String stripExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private String toDisplayName(String slug) {
        if (slug == null || slug.isBlank()) return "";
        return slug.replace('_', ' ').replace('-', ' ').trim();
    }

    // ──────────────────────────────────────────
    // Data Model
    // ──────────────────────────────────────────

    public record LoadedDocument(
            String source,
            String collection,
            String content,
            String department,
            String category,
            String language,
            String version,
            String updatedAt
    ) {
    }
}

