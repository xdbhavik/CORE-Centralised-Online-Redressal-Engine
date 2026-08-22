package com.SIH.mark1.ai.qdrant;

import com.SIH.mark1.model.Category;
import com.SIH.mark1.model.Department;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Spring Event Listener that syncs Qdrant when Departments or Categories change.
 * <p>
 * Any service that creates/updates/deletes a Department or Category should publish
 * a {@link DepartmentChangedEvent} or {@link CategoryChangedEvent} via
 * {@code ApplicationEventPublisher.publishEvent(...)}.
 * <p>
 * This listener picks it up asynchronously — the main transaction is not blocked.
 * <p>
 * Usage in a service:
 * <pre>
 *   // On save:
 *   eventPublisher.publishEvent(new KnowledgeEventListener.DepartmentChangedEvent(savedDept, ChangeType.UPSERT));
 *   // On delete:
 *   eventPublisher.publishEvent(new KnowledgeEventListener.DepartmentChangedEvent(dept, ChangeType.DELETE));
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "ai.qdrant.base-url", matchIfMissing = false)
public class KnowledgeEventListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEventListener.class);

    private final KnowledgeSyncService knowledgeSyncService;

    public KnowledgeEventListener(KnowledgeSyncService knowledgeSyncService) {
        this.knowledgeSyncService = knowledgeSyncService;
    }

    // ──────────────────────────────────────────
    // Department Events
    // ──────────────────────────────────────────

    @Async
    @EventListener
    public void onDepartmentChanged(DepartmentChangedEvent event) {
        log.debug("DepartmentChangedEvent received: type={}, dept={}",
                event.changeType(), event.department().getDepartmentName());
        try {
            if (event.changeType() == ChangeType.DELETE) {
                knowledgeSyncService.deleteDepartmentVector(event.department().getDepartmentId());
            } else {
                knowledgeSyncService.syncDepartment(event.department());
            }
        } catch (Exception ex) {
            log.error("Failed to sync Department to Qdrant: {}", ex.getMessage(), ex);
        }
    }

    // ──────────────────────────────────────────
    // Category Events
    // ──────────────────────────────────────────

    @Async
    @EventListener
    public void onCategoryChanged(CategoryChangedEvent event) {
        log.debug("CategoryChangedEvent received: type={}, category={}",
                event.changeType(), event.category().getCategoryName());
        try {
            if (event.changeType() == ChangeType.DELETE) {
                knowledgeSyncService.deleteCategoryVector(event.category().getCategoryId());
            } else {
                knowledgeSyncService.syncCategory(event.category());
            }
        } catch (Exception ex) {
            log.error("Failed to sync Category to Qdrant: {}", ex.getMessage(), ex);
        }
    }

    // ──────────────────────────────────────────
    // Event Types & Records
    // ──────────────────────────────────────────

    /**
     * Enum indicating whether the change is an upsert (create/update) or delete.
     */
    public enum ChangeType {
        UPSERT,
        DELETE
    }

    /**
     * Publish this event when a Department is created or updated.
     */
    public record DepartmentChangedEvent(Department department, ChangeType changeType) {
    }

    /**
     * Publish this event when a Category is created or updated.
     */
    public record CategoryChangedEvent(Category category, ChangeType changeType) {
    }
}
