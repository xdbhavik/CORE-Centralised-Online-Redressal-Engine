package com.SIH.mark1.service.admin;

import com.SIH.mark1.dto.response.DuplicateReviewItem;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintStatusMaster;
import com.SIH.mark1.model.DuplicateComplaint;
import com.SIH.mark1.model.DuplicateDetectionRecord;
import com.SIH.mark1.model.DuplicateReviewStatus;
import com.SIH.mark1.model.NotificationType;
import com.SIH.mark1.model.StatusHistory;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.ComplaintStatusMasterRepository;
import com.SIH.mark1.repository.DuplicateComplaintRepository;
import com.SIH.mark1.repository.DuplicateDetectionRecordRepository;
import com.SIH.mark1.repository.StatusHistoryRepository;
import com.SIH.mark1.repository.UserRepository;
import com.SIH.mark1.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Admin review workflow for complaints flagged as POSSIBLE_DUPLICATE /
 * POSSIBLE_RECURRING_ISSUE by the AI duplicate-detection engine.
 */
@Service
public class AdminDuplicateService {

    private static final Logger log = LoggerFactory.getLogger(AdminDuplicateService.class);

    private final DuplicateDetectionRecordRepository recordRepository;
    private final ComplaintRepository complaintRepository;
    private final DuplicateComplaintRepository duplicateComplaintRepository;
    private final ComplaintStatusMasterRepository statusMasterRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AdminDuplicateService(DuplicateDetectionRecordRepository recordRepository,
                                 ComplaintRepository complaintRepository,
                                 DuplicateComplaintRepository duplicateComplaintRepository,
                                 ComplaintStatusMasterRepository statusMasterRepository,
                                 StatusHistoryRepository statusHistoryRepository,
                                 UserRepository userRepository,
                                 NotificationService notificationService) {
        this.recordRepository = recordRepository;
        this.complaintRepository = complaintRepository;
        this.duplicateComplaintRepository = duplicateComplaintRepository;
        this.statusMasterRepository = statusMasterRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // ─────────────────────────────────────────────────────────────
    // REVIEW QUEUE
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns all complaints awaiting admin duplicate review, newest first.
     */
    @Transactional(readOnly = true)
    public List<DuplicateReviewItem> reviewQueue() {
        return recordRepository.findByReviewStatusOrderByCreatedAtDesc(DuplicateReviewStatus.PENDING)
                .stream()
                .map(this::toItem)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────
    // CONFIRM DUPLICATE
    // ─────────────────────────────────────────────────────────────

    /**
     * Admin confirms the flagged complaint is a duplicate of the matched complaint.
     * Links the two complaints in {@code duplicate_complaints}, records a status
     * history entry, and marks the review as CONFIRMED_DUPLICATE.
     */
    @Transactional
    public DuplicateReviewItem confirmDuplicate(Long complaintId, String adminMobile) {
        DuplicateDetectionRecord record = findPendingRecord(complaintId);
        Complaint complaint = record.getComplaint();
        Complaint matched = record.getMatchedComplaint();
        User admin = findAdmin(adminMobile);

        // Guard against missing matched complaint reference.
        if (matched == null) {
            throw new IllegalStateException(
                    "Matched complaint reference is missing for complaint " + complaintId);
        }

        // Guard against missing similarity value.
        if (record.getSimilarity() == null) {
            throw new IllegalStateException(
                    "Similarity value is missing for complaint " + complaintId);
        }

        // Link the duplicate complaint pair.
        DuplicateComplaint link = DuplicateComplaint.builder()
                .parentComplaint(matched)
                .duplicateComplaint(complaint)
                .similarity(BigDecimal.valueOf(record.getSimilarity()))
                .build();
        link.setCreatedBy(admin.getUserId());
        duplicateComplaintRepository.save(link);

        // Transition the flagged complaint to DUPLICATE status (fallback REJECTED if not configured).
        ComplaintStatusMaster duplicateStatus = statusMasterRepository.findByStatusCode("DUPLICATE")
                .orElseGet(() -> {
                    log.warn("DUPLICATE status not configured in status master; falling back to REJECTED");
                    return statusMasterRepository.findByStatusCode("REJECTED")
                            .orElse(null);
                });
        if (duplicateStatus != null) {
            complaint.setCurrentStatus(duplicateStatus);
            complaint.setUpdatedBy(admin.getUserId());
            complaintRepository.save(complaint);

            StatusHistory history = StatusHistory.builder()
                    .complaint(complaint)
                    .status(duplicateStatus)
                    .remarks("Admin confirmed duplicate of " + matched.getComplaintNo())
                    .changedBy(admin)
                    .build();
            history.setCreatedBy(admin.getUserId());
            statusHistoryRepository.save(history);
        } else {
            log.warn("Neither DUPLICATE nor REJECTED status configured; complaint status left unchanged");
        }

        // Update review state.
        record.setReviewStatus(DuplicateReviewStatus.CONFIRMED_DUPLICATE);
        record.setUpdatedBy(admin.getUserId());
        recordRepository.save(record);

        // Notify the citizen.
        if (complaint.getCitizen() != null) {
            notificationService.sendNotification(
                    complaint.getCitizen(), complaint,
                    NotificationType.STATUS_UPDATED,
                    "Duplicate Complaint",
                    "Your complaint " + complaint.getComplaintNo()
                            + " has been identified as a duplicate of " + matched.getComplaintNo() + ".");
        }

        return toItem(record);
    }

    // ─────────────────────────────────────────────────────────────
    // REJECT DUPLICATE
    // ─────────────────────────────────────────────────────────────

    /**
     * Admin reviews and determines the complaint is NOT a duplicate.
     * The complaint continues its normal lifecycle.
     */
    @Transactional
    public DuplicateReviewItem rejectDuplicate(Long complaintId, String adminMobile) {
        DuplicateDetectionRecord record = findPendingRecord(complaintId);
        Complaint complaint = record.getComplaint();
        User admin = findAdmin(adminMobile);

        record.setReviewStatus(DuplicateReviewStatus.NOT_DUPLICATE);
        record.setUpdatedBy(admin.getUserId());
        recordRepository.save(record);

        // Only record status history if the complaint has a current status.
        if (complaint.getCurrentStatus() != null) {
            StatusHistory history = StatusHistory.builder()
                    .complaint(complaint)
                    .status(complaint.getCurrentStatus())
                    .remarks("Admin reviewed and determined complaint is NOT a duplicate")
                    .changedBy(admin)
                    .build();
            history.setCreatedBy(admin.getUserId());
            statusHistoryRepository.save(history);
        }

        return toItem(record);
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    private DuplicateDetectionRecord findPendingRecord(Long complaintId) {
        return recordRepository.findFirstByComplaintComplaintIdAndReviewStatus(
                        complaintId, DuplicateReviewStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No pending duplicate review found for complaint: " + complaintId));
    }

    private User findAdmin(String adminMobile) {
        Optional<User> user = userRepository.findByMobile(adminMobile);
        if (user.isEmpty()) {
            user = userRepository.findByEmail(adminMobile);
        }
        return user
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found: " + adminMobile));
    }

    private DuplicateReviewItem toItem(DuplicateDetectionRecord record) {
        Complaint complaint = record.getComplaint();
        Complaint matched = record.getMatchedComplaint();

        return DuplicateReviewItem.builder()
                .resultId(record.getResultId())
                .complaintId(complaint.getComplaintId())
                .complaintNumber(complaint.getComplaintNo())
                .title(complaint.getTitle())
                .description(truncate(complaint.getDescription(), 300))
                .department(complaint.getDepartment() != null ? complaint.getDepartment().getDepartmentName() : null)
                .category(complaint.getCategory() != null ? complaint.getCategory().getCategoryName() : null)
                .status(complaint.getCurrentStatus() != null ? complaint.getCurrentStatus().getStatusCode() : null)
                .createdAt(complaint.getCreatedAt())
                .matchedComplaintId(matched != null ? matched.getComplaintId() : null)
                .matchedComplaintNumber(matched != null ? matched.getComplaintNo() : null)
                .matchedStatus(matched != null && matched.getCurrentStatus() != null
                        ? matched.getCurrentStatus().getStatusCode() : null)
                .similarity(record.getSimilarity())
                .decision(record.getDecision())
                .scope(record.getScope())
                .resourceMatch(record.isResourceMatch())
                .locationMatch(record.isLocationMatch())
                .reasons(splitReasons(record.getReasons()))
                .reviewStatus(record.getReviewStatus() != null ? record.getReviewStatus().name() : null)
                .build();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private List<String> splitReasons(String reasons) {
        if (reasons == null || reasons.isBlank()) {
            return List.of();
        }
        return Arrays.stream(reasons.split("\\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}