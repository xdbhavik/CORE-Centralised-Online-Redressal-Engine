package com.SIH.mark1.ai.qdrant;

import com.SIH.mark1.ai.client.QdrantClient;
import com.SIH.mark1.ai.dto.AIResponse;
import com.SIH.mark1.ai.duplicate.ComplaintEmbeddingTextBuilder;
import com.SIH.mark1.ai.duplicate.ComplaintScope;
import com.SIH.mark1.ai.duplicate.ResourceExtractor;
import com.SIH.mark1.ai.embedding.EmbeddingGenerator;
import com.SIH.mark1.ai.rag.KnowledgeChunk;
import com.SIH.mark1.model.Category;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.Department;
import com.SIH.mark1.repository.ComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for keeping Qdrant in sync with MySQL data.
 * <p>
 * When a Department, Category, or Complaint is created/updated/deleted in MySQL,
 * the corresponding vectors in Qdrant are upserted or removed.
 * <p>
 * This ensures the RAG retrieval always reflects the current database state.
 * <p>
 * Called by:
 * - {@link KnowledgeEventListener} (event-driven, via Spring ApplicationEvents)
 * - Admin APIs that need immediate sync
 */
@Service
@ConditionalOnProperty(name = "ai.qdrant.base-url", matchIfMissing = false)
public class KnowledgeSyncService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncService.class);

    // Collection names — must match rag.collections in application.properties
    public static final String COLLECTION_DEPARTMENT = "department_knowledge";
    public static final String COLLECTION_CATEGORY = "category_knowledge";
    public static final String COLLECTION_COMPLAINT_HISTORY = "complaint_history";

    private final QdrantIndexService qdrantIndexService;
    private final QdrantClient qdrantClient;
    private final EmbeddingGenerator embeddingGenerator;
    private final ComplaintEmbeddingTextBuilder embeddingTextBuilder;
    private final ResourceExtractor resourceExtractor;
    private final ComplaintRepository complaintRepository;

    public KnowledgeSyncService(QdrantIndexService qdrantIndexService,
                                 QdrantClient qdrantClient,
                                 EmbeddingGenerator embeddingGenerator,
                                 ComplaintEmbeddingTextBuilder embeddingTextBuilder,
                                 ResourceExtractor resourceExtractor,
                                 ComplaintRepository complaintRepository) {
        this.qdrantIndexService = qdrantIndexService;
        this.qdrantClient = qdrantClient;
        this.embeddingGenerator = embeddingGenerator;
        this.embeddingTextBuilder = embeddingTextBuilder;
        this.resourceExtractor = resourceExtractor;
        this.complaintRepository = complaintRepository;
    }

    // ──────────────────────────────────────────
    // Department Sync
    // ──────────────────────────────────────────

    /**
     * Upserts a Department's vector into the {@code department_knowledge} collection.
     * Upsert is idempotent — calling this on update replaces the old vector.
     *
     * @param department the Department entity to sync
     */
    public void syncDepartment(Department department) {
        String text = buildDepartmentText(department);
        double[] embedding = embeddingGenerator.generate(text);
        long pointId = QdrantPointId.entity(department.getDepartmentId());

        KnowledgeChunk chunk = new KnowledgeChunk(
                pointId,
                "mysql:departments",
                COLLECTION_DEPARTMENT,
                text,
                department.getDepartmentName(),
                "",
                "en",
                "1.0",
                LocalDate.now().toString()
        );

        qdrantIndexService.upsertChunk(COLLECTION_DEPARTMENT, chunk, embedding);
        log.info("Synced Department '{}' (id={}) to Qdrant collection '{}'",
                department.getDepartmentName(), department.getDepartmentId(), COLLECTION_DEPARTMENT);
    }

    /**
     * Removes a Department's vector from Qdrant.
     *
     * @param departmentId the ID of the department to remove
     */
    public void deleteDepartmentVector(Long departmentId) {
        long pointId = QdrantPointId.entity(departmentId);
        qdrantIndexService.deleteChunk(COLLECTION_DEPARTMENT, pointId);
        log.info("Deleted Department vector for id={} from Qdrant", departmentId);
    }

    // ──────────────────────────────────────────
    // Category Sync
    // ──────────────────────────────────────────

    /**
     * Upserts a Category's vector into the {@code category_knowledge} collection.
     *
     * @param category the Category entity to sync
     */
    public void syncCategory(Category category) {
        String text = buildCategoryText(category);
        double[] embedding = embeddingGenerator.generate(text);
        long pointId = QdrantPointId.entity(category.getCategoryId());

        KnowledgeChunk chunk = new KnowledgeChunk(
                pointId,
                "mysql:categories",
                COLLECTION_CATEGORY,
                text,
                category.getDepartment() != null ? category.getDepartment().getDepartmentName() : "",
                category.getCategoryName(),
                "en",
                "1.0",
                LocalDate.now().toString()
        );

        qdrantIndexService.upsertChunk(COLLECTION_CATEGORY, chunk, embedding);
        log.info("Synced Category '{}' (id={}) to Qdrant collection '{}'",
                category.getCategoryName(), category.getCategoryId(), COLLECTION_CATEGORY);
    }

    /**
     * Removes a Category's vector from Qdrant.
     *
     * @param categoryId the ID of the category to remove
     */
    public void deleteCategoryVector(Long categoryId) {
        long pointId = QdrantPointId.entity(categoryId);
        qdrantIndexService.deleteChunk(COLLECTION_CATEGORY, pointId);
        log.info("Deleted Category vector for id={} from Qdrant", categoryId);
    }

    // ──────────────────────────────────────────
    // Complaint History Sync
    // ──────────────────────────────────────────

    /**
     * Indexes a resolved or new complaint into the {@code complaint_history} collection.
     * Used for duplicate detection — Qdrant ANN search replaces full-table cosine scan.
     *
     * @param complaint the Complaint entity to index
     */
    public void syncComplaint(Complaint complaint) {
        syncComplaint(complaint, null);
    }

    /**
     * Indexes a complaint into the {@code complaint_history} collection.
     * When AI analysis is available, scope and resource metadata come from AI rather than heuristics.
     */
    public void syncComplaint(Complaint complaint, AIResponse analysis) {
        if (complaint.getDescription() == null || complaint.getDescription().isBlank()) {
            log.debug("Skipping complaint sync — empty description for id={}", complaint.getComplaintId());
            return;
        }

        String text = embeddingTextBuilder.build(complaint);
        double[] embedding = embeddingGenerator.generate(text);
        long pointId = QdrantPointId.entity(complaint.getComplaintId());

        String department = complaint.getDepartment() != null
                ? complaint.getDepartment().getDepartmentName() : "";
        String category = complaint.getCategory() != null
                ? complaint.getCategory().getCategoryName() : "";
        String status = complaint.getCurrentStatus() != null
                ? complaint.getCurrentStatus().getStatusCode() : "";
        String ward = complaint.getWard() != null ? complaint.getWard() : "";
        ComplaintScope scope = analysis != null
                ? ComplaintScope.from(analysis.scope())
                : inferScope(complaint, text);
        ResourceExtractor.ExtractedResource resource = analysis != null
                ? resourceExtractor.fromAnalysis(analysis, text)
                : resourceExtractor.extract(text, department, category);
        String areaKey = resourceExtractor.areaKey(
                complaint.getLocationAddress(),
                complaint.getWard(),
                complaint.getCity(),
                complaint.getState(),
                complaint.getPincode());

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("content", text);
        payload.put("source", "mysql:complaints");
        payload.put("collection", COLLECTION_COMPLAINT_HISTORY);
        payload.put("complaintId", complaint.getComplaintId());
        payload.put("complaintNumber", complaint.getComplaintNo());
        payload.put("department", department);
        payload.put("departmentId", complaint.getDepartment() != null ? complaint.getDepartment().getDepartmentId() : null);
        payload.put("category", category);
        payload.put("categoryId", complaint.getCategory() != null ? complaint.getCategory().getCategoryId() : null);
        payload.put("scope", scope.name());
        payload.put("resourceType", resource.type());
        payload.put("resourceKey", resource.key());
        payload.put("areaKey", areaKey);
        payload.put("status", status);
        payload.put("ward", ward);
        payload.put("updatedAt", LocalDate.now().toString());

        qdrantClient.upsertPoints(COLLECTION_COMPLAINT_HISTORY, List.of(QdrantPoint.of(pointId, embedding, payload)));
        log.debug("Synced Complaint id={} to Qdrant complaint_history", complaint.getComplaintId());
    }

    private ComplaintScope inferScope(Complaint complaint, String text) {
        String lower = text == null ? "" : text.toLowerCase();
        if (lower.contains("meter") || lower.contains("connection id") || lower.contains("property id") || lower.contains("mere ghar")) {
            return ComplaintScope.INDIVIDUAL;
        }
        if (lower.contains("main road") || lower.contains("pipeline") || lower.contains("pothole") || lower.contains("street light")) {
            return ComplaintScope.PUBLIC_INFRASTRUCTURE;
        }
        return ComplaintScope.LOCAL_AREA;
    }

    /**
     * Reindexes all non-deleted complaints from MySQL into the {@code complaint_history} collection.
     * Intended for admin-triggered migration or recovery after Qdrant data loss.
     *
     * @return count of complaints successfully indexed
     */
    public int reindexAllComplaints() {
        List<Complaint> complaints = complaintRepository.findByDeletedFalse();
        int indexed = 0;
        int failed = 0;
        for (Complaint complaint : complaints) {
            try {
                syncComplaint(complaint);
                indexed++;
            } catch (Exception ex) {
                failed++;
                log.warn("Failed to reindex complaint id={}: {}", complaint.getComplaintId(), ex.getMessage());
            }
        }
        log.info("Complaint reindex complete: indexed={}, failed={}, total={}", indexed, failed, complaints.size());
        return indexed;
    }

    /**
     * Removes a complaint's vector from Qdrant (e.g., if complaint is deleted or test data cleanup).
     *
     * @param complaintId the ID of the complaint to remove
     */
    public void deleteComplaintVector(Long complaintId) {
        long pointId = QdrantPointId.entity(complaintId);
        qdrantIndexService.deleteChunk(COLLECTION_COMPLAINT_HISTORY, pointId);
        log.debug("Deleted Complaint vector for id={} from Qdrant", complaintId);
    }

    // ──────────────────────────────────────────
    // Text Builders
    // ──────────────────────────────────────────

    private String buildDepartmentText(Department department) {
        StringBuilder sb = new StringBuilder();
        sb.append("Department: ").append(department.getDepartmentName()).append(". ");
        if (department.getDescription() != null && !department.getDescription().isBlank()) {
            sb.append(department.getDescription());
        }
        return sb.toString();
    }

    private String buildCategoryText(Category category) {
        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(category.getCategoryName()).append(". ");
        if (category.getDepartment() != null) {
            sb.append("Department: ").append(category.getDepartment().getDepartmentName()).append(". ");
        }
        if (category.getDescription() != null && !category.getDescription().isBlank()) {
            sb.append(category.getDescription());
        }
        return sb.toString();
    }
}
