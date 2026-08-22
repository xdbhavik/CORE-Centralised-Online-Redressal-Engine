package com.SIH.mark1.ai.rag;

/**
 * Represents a single knowledge chunk — a paragraph extracted from a source document.
 * <p>
 * The {@code embedding} field has been removed; vectors are stored in Qdrant, not in RAM.
 * Metadata fields (department, category, language, etc.) are stored as Qdrant payload
 * alongside the vector, enabling filtered search in the future.
 */
public record KnowledgeChunk(
        Object id,           // Qdrant point ID: unsigned integer or UUID
        String source,       // original file name, e.g. "water_supply.md"
        String collection,   // target Qdrant collection, e.g. "department_knowledge"
        String content,      // the actual chunk text
        String department,   // extracted from folder structure, e.g. "Water Supply"
        String category,     // extracted from file name, e.g. "Pipeline Leakage"
        String language,     // "en" by default
        String version,      // document version, "1.0" default
        String updatedAt     // ISO-8601 date string
) {
    /**
     * Convenience factory for chunks without explicit metadata.
     * Useful during transition — metadata defaults to empty strings.
     */
    public static KnowledgeChunk simple(Object id, String source, String collection, String content) {
        return new KnowledgeChunk(id, source, collection, content, "", "", "en", "1.0",
                java.time.LocalDate.now().toString());
    }
}

