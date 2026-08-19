package com.SIH.mark1.service;

import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.NotificationType;
import com.SIH.mark1.model.PriorityMaster;
import com.SIH.mark1.model.SlaStatus;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Complaint-level SLA engine.
 *
 * <p><b>Rules (v1, priority-based):</b></p>
 * <pre>
 * HIGH    → 24 hours
 * MEDIUM  → 72 hours
 * LOW     → 120 hours
 * unknown → 72 hours (MEDIUM fallback)
 * </pre>
 *
 * <p>The SLA timer starts at complaint <b>creation</b> time (not assignment time).
 * Hours are read from {@code system_settings} so admins can tune them without a
 * redeploy; hardcoded constants act as the safety net when a row is missing.</p>
 *
 * <p>Note: {@code ComplaintAssignment.dueDate} remains the officer's internal task
 * deadline. {@code Complaint.slaDueAt} is the official SLA deadline — the two are
 * intentionally independent.</p>
 */
@Service
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    // Hardcoded fallbacks — used when the system_settings row is absent/invalid.
    static final int DEFAULT_HIGH_HOURS = 24;
    static final int DEFAULT_MEDIUM_HOURS = 72;
    static final int DEFAULT_LOW_HOURS = 120;
    static final int DEFAULT_NEAR_BREACH_PERCENT = 80;

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final NotificationService notificationService;

    public SlaService(ComplaintRepository complaintRepository,
                      UserRepository userRepository,
                      SystemSettingService systemSettingService,
                      NotificationService notificationService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.systemSettingService = systemSettingService;
        this.notificationService = notificationService;
    }

    // ─────────────────────────────────────────────────────────────
    // Calculation
    // ─────────────────────────────────────────────────────────────

    /**
     * Resolves the SLA window (in hours) for the given priority.
     * Null / blank / unrecognised priority codes fall back to the MEDIUM window.
     */
    public int resolveSlaHours(PriorityMaster priority) {
        String code = priority != null && priority.getPriorityCode() != null
                ? priority.getPriorityCode().trim().toUpperCase(Locale.ROOT)
                : null;

        if (code == null || code.isEmpty()) {
            return mediumHours();
        }
        return switch (code) {
            case "HIGH" -> systemSettingService.getIntSetting(
                    SystemSettingService.KEY_SLA_HIGH_HOURS, DEFAULT_HIGH_HOURS);
            case "LOW" -> systemSettingService.getIntSetting(
                    SystemSettingService.KEY_SLA_LOW_HOURS, DEFAULT_LOW_HOURS);
            // MEDIUM and any unknown/custom priority code
            default -> mediumHours();
        };
    }

    /** Returns {@code start + SLA hours} for the given priority. */
    public LocalDateTime calculateDueAt(PriorityMaster priority, LocalDateTime start) {
        LocalDateTime base = start != null ? start : LocalDateTime.now();
        return base.plusHours(resolveSlaHours(priority));
    }

    /**
     * Stamps the initial SLA fields on a brand-new complaint.
     * Must be called BEFORE the complaint is first persisted.
     */
    public void applyInitialSla(Complaint complaint) {
        if (complaint == null) {
            return;
        }
        LocalDateTime start = complaint.getCreatedAt() != null ? complaint.getCreatedAt() : LocalDateTime.now();
        complaint.setSlaDueAt(calculateDueAt(complaint.getPriority(), start));
        complaint.setSlaStatus(SlaStatus.ON_TRACK);
        complaint.setSlaBreachedAt(null);
        complaint.setSlaReminderSentAt(null);
    }

    // ─────────────────────────────────────────────────────────────
    // Open-complaint evaluation
    // ─────────────────────────────────────────────────────────────

    /**
     * Re-evaluates the SLA state of an OPEN complaint against the current clock.
     *
     * <p>Also back-fills {@code slaDueAt} from {@code createdAt} for legacy rows
     * created before this feature existed.</p>
     *
     * <p>Does NOT persist — the caller decides when to save.</p>
     *
     * @return true when any SLA field changed
     */
    public boolean refreshOpenComplaintSla(Complaint complaint) {
        return refreshOpenComplaintSla(complaint, LocalDateTime.now());
    }

    /** Testable variant with an injectable "now". */
    boolean refreshOpenComplaintSla(Complaint complaint, LocalDateTime now) {
        if (complaint == null || isSlaFinalised(complaint)) {
            return false;
        }

        boolean changed = false;

        // Back-fill legacy rows: derive the deadline from creation time.
        if (complaint.getSlaDueAt() == null) {
            LocalDateTime start = complaint.getCreatedAt() != null ? complaint.getCreatedAt() : now;
            complaint.setSlaDueAt(calculateDueAt(complaint.getPriority(), start));
            changed = true;
        }

        String previousStatus = complaint.getSlaStatus();
        String nextStatus = evaluateOpenStatus(complaint, now);

        if (!nextStatus.equals(previousStatus)) {
            complaint.setSlaStatus(nextStatus);
            changed = true;
        }

        // slaBreachedAt is write-once — records the FIRST breach moment.
        if (SlaStatus.BREACHED.equals(nextStatus) && complaint.getSlaBreachedAt() == null) {
            complaint.setSlaBreachedAt(now);
            changed = true;
        }

        return changed;
    }

    /**
     * Computes the SLA status of an open complaint: ON_TRACK → NEAR_BREACH → BREACHED.
     */
    private String evaluateOpenStatus(Complaint complaint, LocalDateTime now) {
        LocalDateTime dueAt = complaint.getSlaDueAt();
        if (dueAt == null) {
            return SlaStatus.ON_TRACK;
        }
        if (now.isAfter(dueAt)) {
            return SlaStatus.BREACHED;
        }

        LocalDateTime start = complaint.getCreatedAt();
        if (start == null || !start.isBefore(dueAt)) {
            return SlaStatus.ON_TRACK;
        }

        long windowMinutes = Duration.between(start, dueAt).toMinutes();
        if (windowMinutes <= 0) {
            return SlaStatus.ON_TRACK;
        }
        long elapsedMinutes = Duration.between(start, now).toMinutes();
        if (elapsedMinutes < 0) {
            return SlaStatus.ON_TRACK;
        }

        double elapsedPercent = (elapsedMinutes * 100.0) / windowMinutes;
        return elapsedPercent >= nearBreachPercent() ? SlaStatus.NEAR_BREACH : SlaStatus.ON_TRACK;
    }

    // ─────────────────────────────────────────────────────────────
    // Resolution
    // ─────────────────────────────────────────────────────────────

    /**
     * Finalises the SLA outcome when a complaint is resolved.
     * <ul>
     *   <li>{@code resolvedAt <= slaDueAt} → {@code RESOLVED_WITHIN_SLA}</li>
     *   <li>{@code resolvedAt >  slaDueAt} → {@code RESOLVED_AFTER_SLA}</li>
     * </ul>
     * Missing deadlines are back-filled from {@code createdAt} first, so legacy
     * complaints still receive a meaningful verdict. Does NOT persist.
     */
    public void markResolvedSla(Complaint complaint, LocalDateTime resolvedAt) {
        if (complaint == null) {
            return;
        }
        LocalDateTime effectiveResolvedAt = resolvedAt != null ? resolvedAt : LocalDateTime.now();

        if (complaint.getSlaDueAt() == null) {
            LocalDateTime start = complaint.getCreatedAt() != null
                    ? complaint.getCreatedAt()
                    : effectiveResolvedAt;
            complaint.setSlaDueAt(calculateDueAt(complaint.getPriority(), start));
        }

        boolean withinSla = !effectiveResolvedAt.isAfter(complaint.getSlaDueAt());
        complaint.setSlaStatus(withinSla ? SlaStatus.RESOLVED_WITHIN_SLA : SlaStatus.RESOLVED_AFTER_SLA);

        // Preserve the historic breach timestamp; stamp it if the miss is detected here first.
        if (!withinSla && complaint.getSlaBreachedAt() == null) {
            complaint.setSlaBreachedAt(complaint.getSlaDueAt());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Scheduler entry point
    // ─────────────────────────────────────────────────────────────

    /**
     * Scans every open complaint, refreshes SLA state, persists changes and
     * dispatches near-breach / breach notifications.
     *
     * <p>Called by {@code SlaScheduler}; also directly invocable from tests.</p>
     *
     * @return number of complaints whose SLA state changed
     */
    @Transactional
    public int scanAndUpdateOpenComplaints() {
        LocalDateTime now = LocalDateTime.now();
        List<Complaint> openComplaints;
        try {
            openComplaints = complaintRepository.findOpenComplaintsForSlaScan(
                    SlaStatus.TERMINAL_COMPLAINT_STATUSES, SlaStatus.TERMINAL_SLA_STATUSES);
        } catch (Exception ex) {
            log.error("SLA scan aborted — could not load open complaints: {}", ex.getMessage());
            return 0;
        }

        int updated = 0;
        for (Complaint complaint : openComplaints) {
            try {
                String previousStatus = complaint.getSlaStatus();
                if (!refreshOpenComplaintSla(complaint, now)) {
                    continue;
                }
                complaintRepository.save(complaint);
                updated++;
                notifySlaTransition(complaint, previousStatus, now);
            } catch (Exception ex) {
                // One bad row must never abort the whole scan.
                log.warn("SLA refresh failed for complaint id={}: {}",
                        complaint.getComplaintId(), ex.getMessage());
            }
        }

        if (updated > 0) {
            log.info("SLA scan complete — {} of {} open complaints updated", updated, openComplaints.size());
        } else {
            log.debug("SLA scan complete — no changes across {} open complaints", openComplaints.size());
        }
        return updated;
    }

    // ─────────────────────────────────────────────────────────────
    // Notifications
    // ─────────────────────────────────────────────────────────────

    /**
     * Sends one notification per SLA transition. Guarded by {@code slaReminderSentAt}
     * (near-breach) and {@code slaBreachedAt} (breach) so repeated scheduler passes
     * never spam the same recipients.
     */
    private void notifySlaTransition(Complaint complaint, String previousStatus, LocalDateTime now) {
        try {
            String status = complaint.getSlaStatus();

            if (SlaStatus.BREACHED.equals(status) && !SlaStatus.BREACHED.equals(previousStatus)) {
                dispatch(complaint, NotificationType.SLA_BREACHED,
                        "SLA Breached",
                        "SLA deadline missed for complaint " + complaint.getComplaintNo()
                                + " (\"" + complaint.getTitle() + "\"). Due at: " + complaint.getSlaDueAt()
                                + ". Immediate action required.");
                return;
            }

            if (SlaStatus.NEAR_BREACH.equals(status) && complaint.getSlaReminderSentAt() == null) {
                dispatch(complaint, NotificationType.SLA_NEAR_BREACH,
                        "SLA Deadline Approaching",
                        "Complaint " + complaint.getComplaintNo() + " (\"" + complaint.getTitle()
                                + "\") is nearing its SLA deadline (" + complaint.getSlaDueAt()
                                + "). Please prioritise it.");
                complaint.setSlaReminderSentAt(now);
                complaintRepository.save(complaint);
            }
        } catch (Exception ex) {
            // Notification problems must never fail the SLA update itself.
            log.warn("SLA notification failed for complaint id={}: {}",
                    complaint.getComplaintId(), ex.getMessage());
        }
    }

    /** Notifies the assigned officer; falls back to all admins when unassigned. */
    private void dispatch(Complaint complaint, NotificationType type, String title, String message) {
        User officer = complaint.getOfficer();
        if (officer != null) {
            notificationService.sendNotification(officer, complaint, type, title, message);
            return;
        }
        userRepository.findByRoleAndDeletedFalse(UserRole.ADMIN).forEach(admin ->
                notificationService.sendNotification(admin, complaint, type, title,
                        message + " (No officer assigned yet.)"));
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /** True when the complaint's SLA outcome is already final and must not be touched. */
    private boolean isSlaFinalised(Complaint complaint) {
        if (SlaStatus.isTerminal(complaint.getSlaStatus())) {
            return true;
        }
        return complaint.getCurrentStatus() != null
                && SlaStatus.isTerminalComplaintStatus(complaint.getCurrentStatus().getStatusCode());
    }

    private int mediumHours() {
        return systemSettingService.getIntSetting(
                SystemSettingService.KEY_SLA_MEDIUM_HOURS, DEFAULT_MEDIUM_HOURS);
    }

    private int nearBreachPercent() {
        int percent = systemSettingService.getIntSetting(
                SystemSettingService.KEY_SLA_NEAR_BREACH_PERCENT, DEFAULT_NEAR_BREACH_PERCENT);
        return (percent > 0 && percent <= 100) ? percent : DEFAULT_NEAR_BREACH_PERCENT;
    }
}
