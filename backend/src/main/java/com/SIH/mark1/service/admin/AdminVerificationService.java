package com.SIH.mark1.service.admin;

import com.SIH.mark1.dto.response.VerificationFlaggedItem;
import com.SIH.mark1.dto.response.VerificationSummaryResponse;
import com.SIH.mark1.ivr.service.CitizenVerificationService;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintStatusMaster;
import com.SIH.mark1.model.StatusHistory;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.model.VerificationStatus;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.ComplaintStatusMasterRepository;
import com.SIH.mark1.repository.StatusHistoryRepository;
import com.SIH.mark1.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Admin review of complaints the citizen denied lodging on the verification call.
 *
 * <p>A denial blocks all departmental action, which is right for spam but wrong for the
 * citizen who pressed the wrong key, handed the phone to a relative, or misheard the
 * question. This service is the human check on that automated decision: an admin either
 * releases the complaint or confirms it really is fake.</p>
 *
 * <p>Every override is recorded with <em>who</em> and <em>why</em> — a reason is mandatory.
 * An action that contradicts a citizen's explicit answer is precisely the one that must never
 * be untraceable later.</p>
 */
@Service
public class AdminVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminVerificationService.class);

    private final ComplaintRepository complaintRepository;
    private final ComplaintStatusMasterRepository statusMasterRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final CitizenVerificationService verificationService;

    public AdminVerificationService(ComplaintRepository complaintRepository,
                                    ComplaintStatusMasterRepository statusMasterRepository,
                                    StatusHistoryRepository statusHistoryRepository,
                                    UserRepository userRepository,
                                    CitizenVerificationService verificationService) {
        this.complaintRepository = complaintRepository;
        this.statusMasterRepository = statusMasterRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.userRepository = userRepository;
        this.verificationService = verificationService;
    }

    // ─────────────────────────────────────────────────────────────
    // Read
    // ─────────────────────────────────────────────────────────────

    /**
     * Complaints awaiting verification review, newest first.
     *
     * <p>Keyed on {@code verificationFlagged}, so a complaint drops out of this queue once an
     * admin has ruled on it even though its recorded denial stays intact.</p>
     */
    @Transactional(readOnly = true)
    public List<VerificationFlaggedItem> flaggedQueue() {
        return complaintRepository.findByVerificationFlaggedTrueAndDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toFlaggedItem)
                .toList();
    }

    /** Verification counters for the admin dashboard. */
    @Transactional(readOnly = true)
    public VerificationSummaryResponse summary() {
        long pending = complaintRepository.countByVerificationStatusAndDeletedFalse(VerificationStatus.PENDING);
        long verified = complaintRepository.countByVerificationStatusAndDeletedFalse(VerificationStatus.VERIFIED);
        long rejected = complaintRepository.countByVerificationStatusAndDeletedFalse(VerificationStatus.REJECTED);
        long failed = complaintRepository.countByVerificationStatusAndDeletedFalse(VerificationStatus.FAILED);
        long flagged = complaintRepository.countByVerificationFlaggedTrueAndDeletedFalse();
        long allowed = complaintRepository.countByActionAllowedTrueAndDeletedFalse();
        long total = complaintRepository.countByDeletedFalse();

        return VerificationSummaryResponse.builder()
                .pending(pending)
                .verified(verified)
                .rejected(rejected)
                .failed(failed)
                .flagged(flagged)
                .actionAllowed(allowed)
                .actionBlocked(Math.max(total - allowed, 0))
                .total(total)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Override
    // ─────────────────────────────────────────────────────────────

    /**
     * Releases a complaint despite the citizen's denial, and lets assignment proceed.
     *
     * @param reason mandatory justification; recorded on the complaint and in status history
     */
    @Transactional
    public VerificationFlaggedItem approve(Long complaintId, String adminMobile, String reason) {
        Complaint complaint = findComplaint(complaintId);
        User admin = findAdmin(adminMobile);
        String justification = requireReason(reason);

        // The state change itself belongs to the verification service, which also owns the
        // release-to-assignment path. Duplicating it here would let the admin route drift
        // from the phone route.
        verificationService.approveByAdmin(complaint, justification);
        complaint.setUpdatedBy(admin.getUserId());
        complaintRepository.save(complaint);

        recordHistory(complaint, admin,
                "Verification override (approved) by admin " + admin.getName() + ": " + justification);

        log.info("Verification approved by admin userId={} for complaintId={}",
                admin.getUserId(), complaintId);
        return toFlaggedItem(complaint);
    }

    /**
     * Confirms the complaint is fake: keeps action blocked and closes it.
     *
     * @param reason mandatory justification; recorded on the complaint and in status history
     */
    @Transactional
    public VerificationFlaggedItem reject(Long complaintId, String adminMobile, String reason) {
        Complaint complaint = findComplaint(complaintId);
        User admin = findAdmin(adminMobile);
        String justification = requireReason(reason);

        verificationService.rejectByAdmin(complaint, justification);

        // Close the lifecycle status too, so a complaint judged fake stops appearing in
        // dashboards and SLA scans as if it were still awaiting real work.
        Optional<ComplaintStatusMaster> closed = statusMasterRepository.findByStatusCode("CLOSED");
        if (closed.isPresent()) {
            complaint.setCurrentStatus(closed.get());
        } else {
            log.warn("CLOSED status master missing; complaintId={} left in {} after rejection",
                    complaintId, complaint.getCurrentStatus() != null
                            ? complaint.getCurrentStatus().getStatusCode() : "UNKNOWN");
        }
        complaint.setUpdatedBy(admin.getUserId());
        complaintRepository.save(complaint);

        recordHistory(complaint, admin,
                "Verification override (rejected as fake) by admin " + admin.getName() + ": " + justification);

        log.info("Verification rejected by admin userId={} for complaintId={}",
                admin.getUserId(), complaintId);
        return toFlaggedItem(complaint);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Writes the override into the complaint's status history.
     *
     * <p>Chosen as the audit record because it is queryable per complaint and already feeds
     * the citizen-visible timeline. {@code ivr_call_log} holds no complaint FK, so it cannot
     * answer "who overrode this complaint, and why?".</p>
     */
    private void recordHistory(Complaint complaint, User admin, String remarks) {
        if (complaint.getCurrentStatus() == null) {
            return;
        }
        StatusHistory history = StatusHistory.builder()
                .complaint(complaint)
                .status(complaint.getCurrentStatus())
                .remarks(remarks)
                .changedBy(admin)
                .build();
        history.setCreatedBy(admin.getUserId());
        statusHistoryRepository.save(history);
    }

    private String requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required for a verification override");
        }
        return reason.trim();
    }

    private Complaint findComplaint(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));
        if (Boolean.TRUE.equals(complaint.getDeleted())) {
            throw new IllegalArgumentException("Complaint not found: " + complaintId);
        }
        return complaint;
    }

    private User findAdmin(String adminMobile) {
        return userRepository.findByMobile(adminMobile)
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found: " + adminMobile));
    }

    private VerificationFlaggedItem toFlaggedItem(Complaint complaint) {
        User citizen = complaint.getCitizen();
        return VerificationFlaggedItem.builder()
                .complaintId(complaint.getComplaintId())
                .complaintNumber(complaint.getComplaintNo())
                .title(complaint.getTitle())
                .description(complaint.getDescription())
                .citizenName(citizen != null ? citizen.getName() : null)
                .citizenMobile(citizen != null ? citizen.getMobile() : null)
                .department(complaint.getDepartment() != null
                        ? complaint.getDepartment().getDepartmentName() : null)
                .category(complaint.getCategory() != null
                        ? complaint.getCategory().getCategoryName() : null)
                .priority(complaint.getPriority() != null
                        ? complaint.getPriority().getPriorityCode() : null)
                .status(complaint.getCurrentStatus() != null
                        ? complaint.getCurrentStatus().getStatusCode() : null)
                .verificationStatus(complaint.getVerificationStatus())
                .actionAllowed(verificationService.isActionAllowed(complaint))
                .flagged(Boolean.TRUE.equals(complaint.getVerificationFlagged()))
                .attempts(complaint.getVerificationAttempts() == null
                        ? 0 : complaint.getVerificationAttempts())
                .lastAttemptAt(complaint.getVerificationLastAttemptAt())
                .verifiedAt(complaint.getVerifiedAt())
                .remarks(complaint.getVerificationRemarks())
                .createdAt(complaint.getCreatedAt())
                .slaDueAt(complaint.getSlaDueAt())
                .slaStatus(complaint.getSlaStatus())
                .build();
    }
}
