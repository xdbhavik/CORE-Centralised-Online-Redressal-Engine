package com.SIH.mark1.service;

import com.SIH.mark1.dto.request.ComplaintAssignRequest;
import com.SIH.mark1.model.AiAnalysis;
import com.SIH.mark1.model.AssignedByType;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.Department;
import com.SIH.mark1.model.NotificationType;
import com.SIH.mark1.model.OfficerDepartment;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.repository.AiAnalysisRepository;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.OfficerDepartmentRepository;
import com.SIH.mark1.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * AI auto-assignment engine.
 *
 * When the admin enables AI mode (AUTO_ASSIGNMENT_ENABLED = true), every new
 * complaint is automatically assigned to the best-matched officer of the
 * AI-detected department. Officer selection is strictly department-scoped —
 * only officers mapped to that department via officer_department (active)
 * are considered. Scoring:
 *   +50  officer works in the complaint's ward
 *   -3 per pending case (capped at -30)  → workload balancing
 *   +20 / +10  for fast average resolvers (≤2 / ≤5 days)
 *
 * Every outcome is audited: assignment history rows carry the AI reason, and
 * all admins are notified on success, skip and failure. The engine NEVER
 * breaks complaint registration — any error is logged, admins are alerted,
 * and the complaint simply stays in the manual queue.
 */
@Service
public class AutoAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AutoAssignmentService.class);

    /** Complaint statuses eligible for auto-assignment. */
    private static final Set<String> ELIGIBLE_STATUSES = Set.of("AI_ANALYZED", "REGISTERED");

    private final SystemSettingService systemSettingService;
    private final AssignmentManagementService assignmentManagementService;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final OfficerDepartmentRepository officerDepartmentRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AutoAssignmentService(SystemSettingService systemSettingService,
                                 AssignmentManagementService assignmentManagementService,
                                 AiAnalysisRepository aiAnalysisRepository,
                                 OfficerDepartmentRepository officerDepartmentRepository,
                                 ComplaintRepository complaintRepository,
                                 UserRepository userRepository,
                                 NotificationService notificationService) {
        this.systemSettingService = systemSettingService;
        this.assignmentManagementService = assignmentManagementService;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.officerDepartmentRepository = officerDepartmentRepository;
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Auto-assigns the complaint to the best officer of its department when AI
     * mode is ON. No-op (returns false) when the mode is OFF.
     *
     * @return true when the complaint was auto-assigned
     */
    @Transactional
    public boolean autoAssignIfEnabled(Complaint complaint) {
        if (!systemSettingService.isAutoAssignmentEnabled()) {
            return false;
        }
        if (complaint == null || Boolean.TRUE.equals(complaint.getDeleted())) {
            return false;
        }
        try {
            String statusCode = complaint.getCurrentStatus() != null
                    ? complaint.getCurrentStatus().getStatusCode() : "";
            if (!ELIGIBLE_STATUSES.contains(statusCode)) {
                return false; // e.g. DUPLICATE, ASSIGNED, RESOLVED — skip silently
            }
            if (complaint.getOfficer() != null) {
                return false; // already assigned
            }

            Department department = resolveDepartment(complaint);
            if (department == null) {
                notifyAdmins(complaint, "Auto-Assignment Skipped",
                        "AI could not determine a department for complaint " + complaint.getComplaintNo()
                                + " (\"" + complaint.getTitle() + "\"). Please assign it manually.");
                return false;
            }

            // Department-scoped officer pool — only active mappings, non-deleted officers
            List<OfficerDepartment> candidates = officerDepartmentRepository
                    .findByDepartmentDepartmentIdAndActiveTrue(department.getDepartmentId())
                    .stream()
                    .filter(mapping -> mapping.getOfficer() != null
                            && !Boolean.TRUE.equals(mapping.getOfficer().getDeleted()))
                    .toList();
            if (candidates.isEmpty()) {
                notifyAdmins(complaint, "Auto-Assignment Failed",
                        "No active officer is mapped to " + department.getDepartmentName()
                                + " for complaint " + complaint.getComplaintNo()
                                + ". Please assign it manually or map an officer to this department.");
                return false;
            }

            OfficerDepartment best = candidates.stream()
                    .max(Comparator.comparingDouble(mapping -> score(mapping, complaint)))
                    .orElse(candidates.get(0));
            User officer = best.getOfficer();
            String reason = buildReason(best, complaint, department);

            ComplaintAssignRequest request = new ComplaintAssignRequest();
            request.setComplaintId(complaint.getComplaintId());
            request.setOfficerId(officer.getUserId());
            request.setDepartmentId(department.getDepartmentId());
            request.setDueDate(dueDateFor(complaint));
            request.setReason("AI auto-assigned: " + reason);
            request.setInternalNote("Assigned by AI auto-assignment engine. " + reason);

            assignmentManagementService.assign(request, null, false, AssignedByType.AI);

            // Admin notification — AI assignment happened
            notifyAdmins(complaint, "AI Auto-Assignment",
                    "AI assigned complaint " + complaint.getComplaintNo() + " (\"" + complaint.getTitle()
                            + "\") to officer " + officer.getName() + " (" + department.getDepartmentName()
                            + "). Reason: " + reason);

            // Citizen notification — officer assigned
            notificationService.sendNotification(
                    complaint.getCitizen(), complaint,
                    NotificationType.STATUS_UPDATED,
                    "Officer Assigned",
                    "Your complaint " + complaint.getComplaintNo() + " has been assigned to officer "
                            + officer.getName() + " (" + department.getDepartmentName() + ").");

            log.info("AI auto-assigned complaint {} → officer {} ({})",
                    complaint.getComplaintNo(), officer.getName(), department.getDepartmentName());
            return true;
        } catch (Exception ex) {
            // Auto-assignment must never break complaint registration.
            log.error("AI auto-assignment failed for complaint id={}: {}", complaint.getComplaintId(), ex.getMessage(), ex);
            notifyAdmins(complaint, "Auto-Assignment Error",
                    "AI auto-assignment failed for complaint " + complaint.getComplaintNo()
                            + ": " + ex.getMessage() + ". Please assign it manually.");
            return false;
        }
    }

    /**
     * Runs auto-assignment over every complaint still waiting in the
     * AI_ANALYZED manual queue. Useful right after the admin switches from
     * MANUAL to AI mode.
     *
     * @return number of complaints successfully auto-assigned
     */
    @Transactional
    public int runForPendingQueue() {
        if (!systemSettingService.isAutoAssignmentEnabled()) {
            throw new IllegalStateException(
                    "Auto-assignment mode is OFF. Enable it first via PUT /api/v1/admin/settings/auto-assignment");
        }
        List<Complaint> pending = complaintRepository
                .findByCurrentStatusStatusCodeAndDeletedFalseOrderByCreatedAtDesc("AI_ANALYZED");
        int assigned = 0;
        for (Complaint complaint : pending) {
            if (autoAssignIfEnabled(complaint)) {
                assigned++;
            }
        }
        log.info("Run-pending completed: {} of {} queued complaints auto-assigned", assigned, pending.size());
        return assigned;
    }

    // ─────────────────────────────────────────────
    // Scoring
    // ─────────────────────────────────────────────

    private double score(OfficerDepartment mapping, Complaint complaint) {
        double score = 0;
        if (wardMatches(mapping, complaint)) {
            score += 50;
        }
        long pending = pendingComplaints(mapping.getOfficer());
        score -= Math.min(pending * 3, 30); // workload penalty, capped
        Double avgDays = averageResolutionDays(mapping.getOfficer());
        if (avgDays != null) {
            if (avgDays <= 2) {
                score += 20;
            } else if (avgDays <= 5) {
                score += 10;
            }
        }
        return score;
    }

    private String buildReason(OfficerDepartment selected, Complaint complaint, Department department) {
        List<String> reasons = new ArrayList<>();
        if (wardMatches(selected, complaint)) {
            reasons.add("works in ward " + complaint.getWard());
        }
        reasons.add(department.getDepartmentName() + " officer");
        reasons.add("pending cases: " + pendingComplaints(selected.getOfficer()));
        Double avg = averageResolutionDays(selected.getOfficer());
        if (avg != null) {
            reasons.add("avg resolution " + avg + " days");
        }
        return String.join("; ", reasons);
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    /** AI-detected department first, category-derived department as fallback. */
    private Department resolveDepartment(Complaint complaint) {
        AiAnalysis analysis = aiAnalysisRepository.findByComplaint(complaint).orElse(null);
        if (analysis != null && analysis.getDetectedDepartment() != null) {
            return analysis.getDetectedDepartment();
        }
        return complaint.getDepartment();
    }

    /** Priority-aware SLA due date: HIGH = +1 day, MEDIUM = +3, LOW = +5. */
    private LocalDate dueDateFor(Complaint complaint) {
        String priority = complaint.getPriority() != null && complaint.getPriority().getPriorityCode() != null
                ? complaint.getPriority().getPriorityCode().toUpperCase() : "MEDIUM";
        return switch (priority) {
            case "HIGH" -> LocalDate.now().plusDays(1);
            case "LOW" -> LocalDate.now().plusDays(5);
            default -> LocalDate.now().plusDays(3);
        };
    }

    private void notifyAdmins(Complaint complaint, String title, String message) {
        try {
            userRepository.findByRoleAndDeletedFalse(UserRole.ADMIN).forEach(admin ->
                    notificationService.sendNotification(admin, complaint,
                            NotificationType.AUTO_ASSIGNMENT, title, message));
        } catch (Exception ex) {
            log.warn("Failed to notify admins for complaint id={}: {}", complaint.getComplaintId(), ex.getMessage());
        }
    }

    private long pendingComplaints(User officer) {
        return complaintRepository.findByOfficer(officer).stream()
                .filter(c -> c.getCurrentStatus() != null)
                .filter(c -> !List.of("RESOLVED", "CLOSED").contains(c.getCurrentStatus().getStatusCode()))
                .count();
    }

    private Double averageResolutionDays(User officer) {
        List<Complaint> resolved = complaintRepository.findByOfficer(officer).stream()
                .filter(c -> c.getResolvedAt() != null && c.getCreatedAt() != null)
                .toList();
        if (resolved.isEmpty()) {
            return null;
        }
        double avg = resolved.stream()
                .mapToDouble(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toHours() / 24.0)
                .average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean wardMatches(OfficerDepartment mapping, Complaint complaint) {
        Long ward = parseLong(complaint.getWard());
        return ward != null && mapping.getWardId() != null && ward.equals(mapping.getWardId());
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
