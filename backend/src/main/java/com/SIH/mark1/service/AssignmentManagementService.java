package com.SIH.mark1.service;

import com.SIH.mark1.dto.request.ComplaintAssignRequest;
import com.SIH.mark1.dto.request.OfficerResolutionRequest;
import com.SIH.mark1.dto.request.OfficerUpdateRequest;
import com.SIH.mark1.dto.response.AssignmentComplaintDetailResponse;
import com.SIH.mark1.dto.response.AssignmentHistoryResponse;
import com.SIH.mark1.dto.response.AssignmentOfficerResponse;
import com.SIH.mark1.dto.response.AssignmentQueueResponse;
import com.SIH.mark1.dto.response.AssignmentRecommendationResponse;
import com.SIH.mark1.dto.response.AutoAssignmentStatsResponse;
import com.SIH.mark1.dto.response.OfficerDashboardResponse;
import com.SIH.mark1.dto.response.OfficerNoteResponse;
import com.SIH.mark1.ivr.service.CitizenVerificationService;
import com.SIH.mark1.model.AiAnalysis;
import com.SIH.mark1.model.AssignedByType;
import com.SIH.mark1.model.AssignmentStatus;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintAssignment;
import com.SIH.mark1.model.ComplaintAssignmentHistory;
import com.SIH.mark1.model.ComplaintMedia;
import com.SIH.mark1.model.ComplaintStatusMaster;
import com.SIH.mark1.model.Department;
import com.SIH.mark1.model.NotificationType;
import com.SIH.mark1.model.OfficerDepartment;
import com.SIH.mark1.model.OfficerNote;
import com.SIH.mark1.model.StatusHistory;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.repository.AiAnalysisRepository;
import com.SIH.mark1.repository.ComplaintAssignmentHistoryRepository;
import com.SIH.mark1.repository.ComplaintAssignmentRepository;
import com.SIH.mark1.repository.ComplaintMediaRepository;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.ComplaintStatusMasterRepository;
import com.SIH.mark1.repository.DepartmentRepository;
import com.SIH.mark1.repository.OfficerDepartmentRepository;
import com.SIH.mark1.repository.OfficerNoteRepository;
import com.SIH.mark1.repository.PriorityMasterRepository;
import com.SIH.mark1.repository.StatusHistoryRepository;
import com.SIH.mark1.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AssignmentManagementService {

    private static final String STATUS_AI_ANALYZED = "AI_ANALYZED";
    private static final String STATUS_ASSIGNED = "ASSIGNED";

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PriorityMasterRepository priorityMasterRepository;
    private final ComplaintStatusMasterRepository statusMasterRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final OfficerDepartmentRepository officerDepartmentRepository;
    private final ComplaintAssignmentRepository assignmentRepository;
    private final ComplaintAssignmentHistoryRepository assignmentHistoryRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final ComplaintMediaRepository mediaRepository;
    private final OfficerNoteRepository officerNoteRepository;
    private final NotificationService notificationService;
    private final SlaService slaService;

    /**
     * Verification gate for departmental action.
     *
     * <p>Injected lazily via {@link ObjectProvider} because the IVR module reaches back into
     * assignment when a citizen confirms, so a hard constructor dependency in both directions
     * would fail context startup.</p>
     */
    private final ObjectProvider<CitizenVerificationService> verificationServiceProvider;

    /** Upload dir — media file paths ko web URLs me normalize karne ke liye. */

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Converts a stored file path (may be absolute like /app/uploads/8/x.jpg or
     * relative like 8/x.jpg) into a web URL path served by WebConfig: /media/8/x.jpg
     */
    private String toMediaUrl(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        String s = stored.replace('\\', '/');
        String dir = uploadDir.replace('\\', '/');
        if (!dir.isEmpty() && s.startsWith(dir + "/")) {
            s = s.substring(dir.length() + 1);
        }
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return "/media/" + s;
    }

    public AssignmentManagementService(ComplaintRepository complaintRepository,
                                       UserRepository userRepository,
                                       DepartmentRepository departmentRepository,
                                       PriorityMasterRepository priorityMasterRepository,
                                       ComplaintStatusMasterRepository statusMasterRepository,
                                       StatusHistoryRepository statusHistoryRepository,
                                       OfficerDepartmentRepository officerDepartmentRepository,
                                       ComplaintAssignmentRepository assignmentRepository,
                                       ComplaintAssignmentHistoryRepository assignmentHistoryRepository,
                                       AiAnalysisRepository aiAnalysisRepository,
                                       ComplaintMediaRepository mediaRepository,
                                       OfficerNoteRepository officerNoteRepository,
                                       NotificationService notificationService,
                                       SlaService slaService,
                                       ObjectProvider<CitizenVerificationService> verificationServiceProvider) {

        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.priorityMasterRepository = priorityMasterRepository;
        this.statusMasterRepository = statusMasterRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.officerDepartmentRepository = officerDepartmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentHistoryRepository = assignmentHistoryRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.mediaRepository = mediaRepository;
        this.officerNoteRepository = officerNoteRepository;
        this.notificationService = notificationService;
        this.slaService = slaService;
        this.verificationServiceProvider = verificationServiceProvider;
    }

    /**
     * Blocks departmental action on a complaint the citizen has not confirmed.
     *
     * <p>Auto-assignment already honours the verification gate, but an admin acting manually
     * bypassed it entirely — so a complaint the citizen explicitly denied could still be
     * pushed to an officer. Enforcing the check here closes that path, keeping the rule at
     * the service layer where every caller (admin UI, API client, scheduler) must pass it.</p>
     *
     * <p>Note this gates <em>action</em> only. The SLA clock still starts at complaint
     * creation, so our call-back latency never eats into the citizen's redressal window.</p>
     *
     * @throws IllegalStateException with a citizen-facing reason when action is not allowed
     */
    private void assertActionAllowed(Complaint complaint) {
        CitizenVerificationService verificationService = verificationServiceProvider.getIfAvailable();
        if (verificationService == null || verificationService.isActionAllowed(complaint)) {
            return;
        }
        throw new IllegalStateException(verificationService.blockedReason(complaint));
    }

    /**
     * Gate state for response DTOs.
     *
     * <p>Defaults to allowed when the IVR module is absent, matching {@link #assertActionAllowed}:
     * a UI must never show "on hold" for a gate that is not actually enforcing anything.</p>
     */
    private boolean verificationActionAllowed(Complaint complaint) {
        CitizenVerificationService verificationService = verificationServiceProvider.getIfAvailable();
        return verificationService == null || verificationService.isActionAllowed(complaint);
    }

    /** Blocked-action reason for response DTOs; null when action is allowed. */
    private String verificationBlockedReason(Complaint complaint) {
        CitizenVerificationService verificationService = verificationServiceProvider.getIfAvailable();
        return verificationService == null ? null : verificationService.blockedReasonOrNull(complaint);
    }


    public List<AssignmentQueueResponse> pendingQueue() {
        return complaintRepository.findByCurrentStatusStatusCodeAndDeletedFalseOrderByCreatedAtDesc(STATUS_AI_ANALYZED)
                .stream()
                .map(this::toQueueResponse)
                .toList();
    }

    /** Active assignment statuses (work not finished yet). */
    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
            List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACCEPTED, AssignmentStatus.IN_PROGRESS, AssignmentStatus.REASSIGNED);

    /** Overdue active assignments (dueDate before today) — admin escalation view. */
    public List<AssignmentQueueResponse> overdueAssignments() {
        return assignmentRepository.findByDueDateBeforeAndAssignmentStatusIn(LocalDate.now(), ACTIVE_ASSIGNMENT_STATUSES)
                .stream()
                .filter(a -> a.getComplaint() != null && !Boolean.TRUE.equals(a.getComplaint().getDeleted()))
                .sorted(Comparator.comparing(ComplaintAssignment::getDueDate))
                .map(this::toQueueResponse)
                .toList();
    }

    /** Assignments due today. */
    public List<AssignmentQueueResponse> dueTodayAssignments() {
        return assignmentRepository.findByDueDateAndAssignmentStatusIn(LocalDate.now(), ACTIVE_ASSIGNMENT_STATUSES)
                .stream()
                .filter(a -> a.getComplaint() != null && !Boolean.TRUE.equals(a.getComplaint().getDeleted()))
                .map(this::toQueueResponse)
                .toList();
    }

    /** Paginated pending queue for the admin panel. */
    public Page<AssignmentQueueResponse> pendingQueuePaginated(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return complaintRepository.findByCurrentStatusStatusCodeAndDeletedFalse(STATUS_AI_ANALYZED,
                        PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending()))
                .map(this::toQueueResponse);
    }

    public AssignmentComplaintDetailResponse detail(Long complaintId) {
        Complaint complaint = findComplaint(complaintId);
        AiAnalysis analysis = aiAnalysisRepository.findByComplaint(complaint).orElse(null);
        Department suggestedDepartment = analysis != null && analysis.getDetectedDepartment() != null
                ? analysis.getDetectedDepartment()
                : complaint.getDepartment();
        return AssignmentComplaintDetailResponse.builder()
                .complaintId(complaint.getComplaintId())
                .complaintNo(complaint.getComplaintNo())
                .citizenName(complaint.getCitizen().getName())
                .citizenMobile(complaint.getCitizen().getMobile())
                .citizenAddress(complaint.getCitizen().getAddress())
                .ward(complaint.getWard())
                .complaintDate(complaint.getCreatedAt())
                .currentStatus(complaint.getCurrentStatus().getStatusCode())
                .title(complaint.getTitle())
                .description(complaint.getDescription())
                .locationAddress(complaint.getLocationAddress())
                .latitude(complaint.getLatitude())
                .longitude(complaint.getLongitude())
                .mediaUrls(mediaRepository.findByComplaint(complaint).stream().map(ComplaintMedia::getFileUrl).map(this::toMediaUrl).toList())
                .aiSummary(analysis != null ? analysis.getAiSummary() : null)
                .aiCategory(analysis != null && analysis.getDetectedCategory() != null ? analysis.getDetectedCategory().getCategoryName() : complaint.getCategory().getCategoryName())
                .aiPriority(analysis != null && analysis.getDetectedPriority() != null ? analysis.getDetectedPriority().getPriorityCode() : complaint.getPriority().getPriorityCode())
                .urgency(urgency(complaint, analysis))
                .suggestedDepartmentId(suggestedDepartment != null ? suggestedDepartment.getDepartmentId() : null)
                .suggestedDepartment(suggestedDepartment != null ? suggestedDepartment.getDepartmentName() : null)
                .confidence(analysis != null && analysis.getConfidenceScore() != null ? analysis.getConfidenceScore().intValue() : null)
                .keywords(extractKeywords(complaint))
                .suggestedAction(suggestedAction(complaint))
                .verificationStatus(complaint.getVerificationStatus())
                .actionAllowed(verificationActionAllowed(complaint))
                .verificationFlagged(Boolean.TRUE.equals(complaint.getVerificationFlagged()))
                .verificationBlockedReason(verificationBlockedReason(complaint))
                .build();

    }

    public List<AssignmentOfficerResponse> officersByDepartment(Long departmentId, Long complaintId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        AssignmentRecommendationResponse recommendation = complaintId != null ? recommend(complaintId, departmentId) : null;
        Complaint complaint = complaintId != null ? findComplaint(complaintId) : null;
        return officerDepartmentRepository.findByDepartmentDepartmentIdAndActiveTrue(department.getDepartmentId())
                .stream()
                .map(mapping -> toOfficerResponse(mapping, complaint, recommendation))
                .toList();
    }

    public AssignmentRecommendationResponse recommend(Long complaintId, Long departmentId) {
        Complaint complaint = findComplaint(complaintId);
        Department department = departmentId != null
                ? departmentRepository.findById(departmentId).orElseThrow(() -> new IllegalArgumentException("Department not found"))
                : complaint.getDepartment();
        List<OfficerDepartment> officers = officerDepartmentRepository.findByDepartmentDepartmentIdAndActiveTrue(department.getDepartmentId());
        if (officers.isEmpty()) {
            return AssignmentRecommendationResponse.builder().reason("No active officer mapped to selected department").build();
        }
        OfficerDepartment selected = officers.stream()
                .min(Comparator.comparingInt((OfficerDepartment m) -> wardMatches(m, complaint) ? 0 : 1)
                        .thenComparingLong(m -> pendingComplaints(m.getOfficer())))
                .orElse(officers.get(0));
        User officer = selected.getOfficer();
        List<String> reasons = new ArrayList<>();
        if (wardMatches(selected, complaint)) {
            reasons.add("Works in ward " + complaint.getWard());
        }
        reasons.add(department.getDepartmentName() + " officer");
        reasons.add("Pending cases " + pendingComplaints(officer));
        Double avg = averageResolutionDays(officer);
        if (avg != null) {
            reasons.add("Average resolution " + avg + " days");
        }
        return AssignmentRecommendationResponse.builder()
                .officerId(officer.getUserId())
                .officerName(officer.getName())
                .reason(String.join("; ", reasons))
                .pendingCases(pendingComplaints(officer))
                .averageResolutionDays(avg)
                .build();
    }

    @Transactional
    public AssignmentComplaintDetailResponse assign(ComplaintAssignRequest request, String adminUsername, boolean reassignment) {
        return assign(request, adminUsername, reassignment, AssignedByType.MANUAL);
    }

    /**
     * Core assignment routine shared by admin (MANUAL) and the AI auto-assignment
     * engine (AI). {@code adminUsername} may be null for AI assignments.
     */
    @Transactional
    public AssignmentComplaintDetailResponse assign(ComplaintAssignRequest request, String adminUsername, boolean reassignment, AssignedByType assignedByType) {
        if (reassignment && isBlank(firstNonBlank(request.getReason(), request.getRemarks()))) {
            throw new IllegalArgumentException("Reassignment reason is mandatory");
        }
        Complaint complaint = findComplaint(request.getComplaintId());
        assertActionAllowed(complaint);
        User officer = userRepository.findById(request.getOfficerId())
                .orElseThrow(() -> new IllegalArgumentException("Officer not found"));
        if (officer.getRole() != UserRole.OFFICER) {
            throw new IllegalArgumentException("Selected user is not an officer");
        }
        Department department = request.getDepartmentId() != null
                ? departmentRepository.findById(request.getDepartmentId()).orElseThrow(() -> new IllegalArgumentException("Department not found"))
                : complaint.getDepartment();
        officerDepartmentRepository.findByOfficerUserIdAndDepartmentDepartmentIdAndActiveTrue(officer.getUserId(), department.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Officer is not mapped to selected department"));

        User admin = !isBlank(adminUsername) ? resolveUser(adminUsername).orElse(null) : null;
        User oldOfficer = complaint.getOfficer();
        ComplaintStatusMaster assignedStatus = statusMasterRepository.findByStatusCode(STATUS_ASSIGNED)
                .orElseThrow(() -> new IllegalStateException("ASSIGNED status not found in complaint_status table"));
        if (request.getPriorityId() != null) {
            complaint.setPriority(priorityMasterRepository.findById(request.getPriorityId()).orElseThrow(() -> new IllegalArgumentException("Priority not found")));
        }
        complaint.setDepartment(department);
        complaint.setOfficer(officer);
        complaint.setAssignedAt(LocalDateTime.now());
        complaint.setCurrentStatus(assignedStatus);
        complaint.setUpdatedBy(admin != null ? admin.getUserId() : null);
        complaintRepository.save(complaint);

        ComplaintAssignment assignment = assignmentRepository.findByComplaint(complaint)
                .orElseGet(() -> ComplaintAssignment.builder().complaint(complaint).build());
        assignment.setOfficer(officer);
        assignment.setAssignedByAdmin(admin);
        assignment.setAssignedDepartment(department);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setDueDate(request.getDueDate() != null ? request.getDueDate() : LocalDate.now().plusDays(1));
        assignment.setInternalNote(firstNonBlank(request.getInternalNote(), request.getRemarks()));
        assignment.setAssignmentStatus(AssignmentStatus.ASSIGNED);
        assignment.setAssignedByType(assignedByType != null ? assignedByType : AssignedByType.MANUAL);
        assignment.setUpdatedBy(admin != null ? admin.getUserId() : null);
        if (assignment.getId() == null && admin != null) {
            assignment.setCreatedBy(admin.getUserId());
        }
        try {
            // saveAndFlush forces the INSERT inside this try-block so a concurrent
            // assign (admin + AI at the same moment) surfaces here, not as a 500 at commit.
            assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(
                    "Complaint is already assigned to an officer. Refresh and use reassignment instead.");
        }
        ComplaintAssignmentHistory history = ComplaintAssignmentHistory.builder()
                .complaint(complaint)
                .oldOfficer(oldOfficer)
                .newOfficer(officer)
                .changedBy(admin)
                .reason(firstNonBlank(request.getReason(), request.getRemarks(), request.getInternalNote(), reassignment ? "Complaint reassigned" : "Complaint assigned"))
                .changedAt(LocalDateTime.now())
                .build();
        if (admin != null) {
            history.setCreatedBy(admin.getUserId());
        }
        assignmentHistoryRepository.save(history);

        StatusHistory statusHistory = StatusHistory.builder()
                .complaint(complaint)
                .status(assignedStatus)
                .remarks("Assigned to " + officer.getName())
                .changedBy(admin)
                .changedAt(LocalDateTime.now())
                .build();
        if (admin != null) {
            statusHistory.setCreatedBy(admin.getUserId());
        }
        statusHistoryRepository.save(statusHistory);

        // Notify officer about the new assignment (both AI and manual paths)
        notificationService.sendNotification(
                officer, complaint,
                NotificationType.OFFICER_ASSIGNED,
                "New Complaint Assigned",
                "Complaint " + complaint.getComplaintNo() + " (\"" + complaint.getTitle() + "\") has been assigned to you"
                        + (assignedByType == AssignedByType.AI ? " by the AI system." : " by admin.")
                        + " Department: " + department.getDepartmentName()
                        + ". Due date: " + assignment.getDueDate());

        return detail(complaint.getComplaintId());
    }

    /**
     * Returns every complaint currently assigned by the AI auto-assignment
     * engine (assignedByType = AI), newest first — for the admin panel.
     */
    public List<AssignmentQueueResponse> autoAssignedComplaints() {
        return assignmentRepository.findByAssignedByTypeOrderByAssignedAtDesc(AssignedByType.AI)
                .stream()
                .filter(assignment -> assignment.getComplaint() != null
                        && !Boolean.TRUE.equals(assignment.getComplaint().getDeleted()))
                .map(this::toQueueResponse)
                .toList();
    }

    /**
     * AI vs MANUAL assignment summary for the admin dashboard.
     */
    public AutoAssignmentStatsResponse autoAssignmentStats() {
        // Cancelled assignments (citizen cancelled the complaint) must not inflate stats
        long aiCount = assignmentRepository.countByAssignedByTypeAndAssignmentStatusNot(
                AssignedByType.AI, AssignmentStatus.CANCELLED);
        long manualCount = assignmentRepository.countByAssignedByTypeAndAssignmentStatusNot(
                AssignedByType.MANUAL, AssignmentStatus.CANCELLED);
        long aiLast7Days = assignmentRepository.countByAssignedByTypeAndAssignedAtAfterAndAssignmentStatusNot(
                AssignedByType.AI, LocalDateTime.now().minusDays(7), AssignmentStatus.CANCELLED);
        java.util.Map<String, Long> byDepartment = assignmentRepository
                .findByAssignedByTypeOrderByAssignedAtDesc(AssignedByType.AI)
                .stream()
                .filter(assignment -> assignment.getAssignmentStatus() != AssignmentStatus.CANCELLED)
                .filter(assignment -> assignment.getAssignedDepartment() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        assignment -> assignment.getAssignedDepartment().getDepartmentName(),
                        java.util.stream.Collectors.counting()));
        return AutoAssignmentStatsResponse.builder()
                .totalAssignments(aiCount + manualCount)
                .aiAssignments(aiCount)
                .manualAssignments(manualCount)
                .aiAssignmentsLast7Days(aiLast7Days)
                .aiAssignmentsByDepartment(byDepartment)
                .build();
    }

    public List<AssignmentHistoryResponse> history(Long complaintId) {
        Complaint complaint = findComplaint(complaintId);
        return assignmentHistoryRepository.findByComplaintOrderByChangedAtDesc(complaint)
                .stream()
                .map(history -> AssignmentHistoryResponse.builder()
                        .id(history.getId())
                        .complaintId(complaint.getComplaintId())
                        .oldOfficerName(history.getOldOfficer() != null ? history.getOldOfficer().getName() : null)
                        .newOfficerName(history.getNewOfficer().getName())
                        .changedByName(history.getChangedBy() != null ? history.getChangedBy().getName() : null)
                        .reason(history.getReason())
                        .changedAt(history.getChangedAt())
                        .build())
                .toList();
    }

    public OfficerDashboardResponse officerDashboard(String username) {
        User officer = findOfficer(username);
        List<ComplaintAssignment> assignments = assignmentRepository.findByOfficer(officer);
        LocalDate today = LocalDate.now();
        return OfficerDashboardResponse.builder()
                .totalAssigned(assignments.size())
                .pending(assignments.stream().filter(a -> a.getAssignmentStatus() == AssignmentStatus.ASSIGNED).count())
                .accepted(assignments.stream().filter(a -> a.getAssignmentStatus() == AssignmentStatus.ACCEPTED).count())
                .inProgress(assignments.stream().filter(a -> a.getAssignmentStatus() == AssignmentStatus.IN_PROGRESS).count())
                .resolved(assignments.stream().filter(a -> isComplaintStatus(a.getComplaint(), "RESOLVED")).count())
                .highPriority(assignments.stream().filter(a -> isPriority(a.getComplaint(), "HIGH")).count())
                .dueToday(assignments.stream().filter(a -> a.getDueDate() != null && a.getDueDate().equals(today)).count())
                .overdue(assignments.stream().filter(this::isOverdue).count())
                .assignedToday(assignments.stream().filter(a -> a.getAssignedAt() != null && a.getAssignedAt().toLocalDate().equals(today)).count())
                .urgent(assignments.stream().filter(a -> isPriority(a.getComplaint(), "HIGH")).count())
                .completed(assignments.stream().filter(a -> a.getComplaint() != null && a.getComplaint().getResolvedAt() != null).count())
                .build();
    }

    public List<AssignmentQueueResponse> officerComplaints(String username) {
        return officerComplaints(username, null, null, null, null, false);
    }

    public List<AssignmentQueueResponse> officerComplaints(String username,
                                                           String status,
                                                           String priority,
                                                           String category,
                                                           LocalDate date,
                                                           boolean overdue) {
        User officer = findOfficer(username);
        return assignmentRepository.findByOfficer(officer).stream()
                .filter(assignment -> matchesOfficerFilters(assignment, status, priority, category, date, overdue))
                .sorted(Comparator.comparing(ComplaintAssignment::getAssignedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toQueueResponse)
                .toList();
    }

    public AssignmentComplaintDetailResponse officerComplaintDetail(String username, Long complaintId) {
        User officer = findOfficer(username);
        ComplaintAssignment assignment = findAssignedToOfficer(officer, complaintId);
        return detail(assignment.getComplaint().getComplaintId());
    }

    @Transactional
    public AssignmentComplaintDetailResponse acceptAssignment(String username, Long complaintId, String remarks) {
        User officer = findOfficer(username);
        ComplaintAssignment assignment = findAssignedToOfficerForAction(officer, complaintId);
        if (assignment.getAssignmentStatus() == AssignmentStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled assignment cannot be accepted");
        }
        if (assignment.getAssignmentStatus() == AssignmentStatus.ACCEPTED) {
            throw new IllegalStateException("Assignment is already accepted");
        }
        if (assignment.getAssignmentStatus() != AssignmentStatus.ASSIGNED) {
            throw new IllegalStateException("Only assigned complaints can be accepted");
        }
        return transitionOfficerAssignment(assignment, AssignmentStatus.ACCEPTED, "ACCEPTED", firstNonBlank(remarks, "Officer accepted assignment"), officer);
    }

    @Transactional
    public AssignmentComplaintDetailResponse startWork(String username, Long complaintId, String remarks) {
        User officer = findOfficer(username);
        ComplaintAssignment assignment = findAssignedToOfficerForAction(officer, complaintId);
        if (assignment.getAssignmentStatus() != AssignmentStatus.ACCEPTED) {
            throw new IllegalStateException("Only accepted complaints can be started");
        }
        return transitionOfficerAssignment(assignment, AssignmentStatus.IN_PROGRESS, "IN_PROGRESS", firstNonBlank(remarks, "Officer started working"), officer);
    }

    private AssignmentQueueResponse toQueueResponse(Complaint complaint) {
        ComplaintAssignment assignment = assignmentRepository.findByComplaint(complaint).orElse(null);
        return toQueueResponse(complaint, assignment);
    }

    private AssignmentQueueResponse toQueueResponse(ComplaintAssignment assignment) {
        return toQueueResponse(assignment.getComplaint(), assignment);
    }

    private AssignmentQueueResponse toQueueResponse(Complaint complaint, ComplaintAssignment assignment) {
        AiAnalysis analysis = aiAnalysisRepository.findByComplaint(complaint).orElse(null);
        Department suggestedDepartment = analysis != null && analysis.getDetectedDepartment() != null ? analysis.getDetectedDepartment() : complaint.getDepartment();
        return AssignmentQueueResponse.builder()
                .complaintId(complaint.getComplaintId())
                .complaintNo(complaint.getComplaintNo())
                .title(complaint.getTitle())
                .citizenName(complaint.getCitizen().getName())
                .location(firstNonBlank(complaint.getLocationAddress(), complaint.getWard(), complaint.getCity(), complaint.getState()))
                .category(complaint.getCategory() != null ? complaint.getCategory().getCategoryName() : null)
                .department(complaint.getDepartment() != null ? complaint.getDepartment().getDepartmentName() : null)
                .priority(complaint.getPriority() != null ? complaint.getPriority().getPriorityCode() : null)
                .status(complaint.getCurrentStatus() != null ? complaint.getCurrentStatus().getStatusCode() : null)
                .assignmentStatus(assignment != null && assignment.getAssignmentStatus() != null ? assignment.getAssignmentStatus().name() : null)
                .suggestedDepartment(suggestedDepartment != null ? suggestedDepartment.getDepartmentName() : null)
                .aiConfidence(analysis != null && analysis.getConfidenceScore() != null ? analysis.getConfidenceScore().intValue() : null)
                .submittedTime(complaint.getCreatedAt())
                .assignedDate(assignment != null ? assignment.getAssignedAt() : complaint.getAssignedAt())
                .dueDate(assignment != null ? assignment.getDueDate() : null)
                .overdue(assignment != null && isOverdue(assignment))
                .officerName(assignment != null && assignment.getOfficer() != null
                        ? assignment.getOfficer().getName()
                        : (complaint.getOfficer() != null ? complaint.getOfficer().getName() : null))
                .assignedByType(assignment != null && assignment.getAssignedByType() != null
                        ? assignment.getAssignedByType().name() : null)
                .slaDueAt(complaint.getSlaDueAt())
                .slaStatus(complaint.getSlaStatus())
                .verificationStatus(complaint.getVerificationStatus())
                .actionAllowed(verificationActionAllowed(complaint))
                .verificationFlagged(Boolean.TRUE.equals(complaint.getVerificationFlagged()))
                .build();


    }

    private AssignmentOfficerResponse toOfficerResponse(OfficerDepartment mapping, Complaint complaint, AssignmentRecommendationResponse recommendation) {
        User officer = mapping.getOfficer();
        boolean recommended = recommendation != null && recommendation.getOfficerId() != null && recommendation.getOfficerId().equals(officer.getUserId());
        return AssignmentOfficerResponse.builder()
                .officerId(officer.getUserId())
                .name(officer.getName())
                .mobile(officer.getMobile())
                .email(officer.getEmail())
                .departmentId(mapping.getDepartment().getDepartmentId())
                .departmentName(mapping.getDepartment().getDepartmentName())
                .wardId(mapping.getWardId())
                .pendingComplaints(pendingComplaints(officer))
                .resolvedComplaints(resolvedComplaints(officer))
                .averageResolutionDays(averageResolutionDays(officer))
                .availability(Boolean.TRUE.equals(mapping.getActive()) && !Boolean.TRUE.equals(officer.getDeleted()) ? "Available" : "Unavailable")
                .recommended(recommended)
                .recommendationReason(recommended ? recommendation.getReason() : null)
                .build();
    }

    private AssignmentComplaintDetailResponse transitionOfficerAssignment(ComplaintAssignment assignment,
                                                                          AssignmentStatus assignmentStatus,
                                                                          String complaintStatusCode,
                                                                          String remarks,
                                                                          User officer) {
        Complaint complaint = assignment.getComplaint();
        ComplaintStatusMaster status = statusMasterRepository.findByStatusCode(complaintStatusCode)
                .orElseThrow(() -> new IllegalStateException(complaintStatusCode + " status not found in complaint_status table"));

        assignment.setAssignmentStatus(assignmentStatus);
        assignment.setUpdatedBy(officer.getUserId());
        assignmentRepository.save(assignment);

        complaint.setCurrentStatus(status);
        complaint.setUpdatedBy(officer.getUserId());
        if (assignmentStatus == AssignmentStatus.IN_PROGRESS && complaint.getAssignedAt() == null) {
            complaint.setAssignedAt(LocalDateTime.now());
        }
        complaintRepository.save(complaint);

        StatusHistory history = StatusHistory.builder()
                .complaint(complaint)
                .status(status)
                .remarks(remarks)
                .changedBy(officer)
                .changedAt(LocalDateTime.now())
                .build();
        history.setCreatedBy(officer.getUserId());
        statusHistoryRepository.save(history);

        return detail(complaint.getComplaintId());
    }

    private ComplaintAssignment findAssignedToOfficer(User officer, Long complaintId) {
        Complaint complaint = findComplaint(complaintId);
        ComplaintAssignment assignment = assignmentRepository.findByComplaint(complaint)
                .orElseThrow(() -> new AccessDeniedException("Complaint is not assigned to this officer"));
        if (assignment.getOfficer() == null || !assignment.getOfficer().getUserId().equals(officer.getUserId())) {
            throw new AccessDeniedException("Complaint is not assigned to this officer");
        }
        return assignment;
    }

    /**
     * Ownership check plus the verification gate, for officer paths that <em>change</em> a case.
     *
     * <p>Separate from {@link #findAssignedToOfficer} so read-only views stay open: an officer
     * must still be able to open a flagged complaint to understand why it is on hold. It is the
     * acting — accepting, progressing, resolving — that has to wait for the citizen's
     * confirmation, including on complaints assigned before the citizen denied them.</p>
     */
    private ComplaintAssignment findAssignedToOfficerForAction(User officer, Long complaintId) {
        ComplaintAssignment assignment = findAssignedToOfficer(officer, complaintId);
        assertActionAllowed(assignment.getComplaint());
        return assignment;
    }

    private boolean matchesOfficerFilters(ComplaintAssignment assignment,
                                          String status,
                                          String priority,
                                          String category,
                                          LocalDate date,
                                          boolean overdue) {
        Complaint complaint = assignment.getComplaint();
        if (complaint == null || Boolean.TRUE.equals(complaint.getDeleted())) {
            return false;
        }
        if (!isBlank(status)) {
            String normalized = status.trim();
            boolean complaintStatusMatches = complaint.getCurrentStatus() != null
                    && normalized.equalsIgnoreCase(complaint.getCurrentStatus().getStatusCode());
            boolean assignmentStatusMatches = assignment.getAssignmentStatus() != null
                    && normalized.equalsIgnoreCase(assignment.getAssignmentStatus().name());
            if (!complaintStatusMatches && !assignmentStatusMatches) {
                return false;
            }
        }
        if (!isBlank(priority) && !isPriority(complaint, priority)) {
            return false;
        }
        if (!isBlank(category) && (complaint.getCategory() == null
                || !category.trim().equalsIgnoreCase(complaint.getCategory().getCategoryName()))) {
            return false;
        }
        if (date != null && (assignment.getAssignedAt() == null || !assignment.getAssignedAt().toLocalDate().equals(date))) {
            return false;
        }
        return !overdue || isOverdue(assignment);
    }

    private boolean isOverdue(ComplaintAssignment assignment) {
        if (assignment.getDueDate() == null || assignment.getComplaint() == null) {
            return false;
        }
        if (isComplaintStatus(assignment.getComplaint(), "RESOLVED") || isComplaintStatus(assignment.getComplaint(), "CLOSED")) {
            return false;
        }
        return assignment.getDueDate().isBefore(LocalDate.now());
    }

    private boolean isComplaintStatus(Complaint complaint, String statusCode) {
        return complaint != null && complaint.getCurrentStatus() != null
                && statusCode.equalsIgnoreCase(complaint.getCurrentStatus().getStatusCode());
    }

    private boolean isPriority(Complaint complaint, String priorityCode) {
        return complaint != null && complaint.getPriority() != null
                && priorityCode.equalsIgnoreCase(complaint.getPriority().getPriorityCode());
    }
    private Complaint findComplaint(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow(() -> new IllegalArgumentException("Complaint not found"));
        if (Boolean.TRUE.equals(complaint.getDeleted())) {
            throw new IllegalArgumentException("Complaint not found");
        }
        return complaint;
    }

    private User findOfficer(String username) {
        User user = resolveUser(username).orElseThrow(() -> new IllegalArgumentException("Officer not found"));
        if (user.getRole() != UserRole.OFFICER) {
            throw new IllegalArgumentException("User is not an officer");
        }
        return user;
    }

    private Optional<User> resolveUser(String username) {
        Optional<User> user = userRepository.findByMobile(username);
        if (user.isEmpty()) {
            user = userRepository.findByEmail(username);
        }
        return user;
    }

    private String urgency(Complaint complaint, AiAnalysis analysis) {
        if (analysis != null && analysis.getPriorityScore() != null && analysis.getPriorityScore().compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "HIGH";
        }
        return complaint.getPriority() != null ? complaint.getPriority().getPriorityCode() : "MEDIUM";
    }

    private List<String> extractKeywords(Complaint complaint) {
        List<String> keywords = new ArrayList<>();
        addKeyword(keywords, complaint.getCategory() != null ? complaint.getCategory().getCategoryName() : null);
        addKeyword(keywords, complaint.getDepartment() != null ? complaint.getDepartment().getDepartmentName() : null);
        String text = ((complaint.getTitle() == null ? "" : complaint.getTitle()) + " " + (complaint.getDescription() == null ? "" : complaint.getDescription())).toLowerCase();
        for (String token : List.of("water", "pani", "pipeline", "leakage", "electricity", "road", "garbage", "drainage", "school")) {
            if (text.contains(token)) {
                addKeyword(keywords, token);
            }
        }
        return keywords.stream().limit(8).toList();
    }

    private String suggestedAction(Complaint complaint) {
        String priority = complaint.getPriority() != null ? complaint.getPriority().getPriorityCode() : "MEDIUM";
        return switch (priority.toUpperCase()) {
            case "HIGH" -> "Assign officer for urgent inspection and resolution.";
            case "LOW" -> "Assign officer for routine verification.";
            default -> "Assign officer for field verification.";
        };
    }

    private long pendingComplaints(User officer) {
        return complaintRepository.findByOfficer(officer).stream()
                .filter(c -> c.getCurrentStatus() != null)
                .filter(c -> !List.of("RESOLVED", "CLOSED").contains(c.getCurrentStatus().getStatusCode()))
                .count();
    }

    private long resolvedComplaints(User officer) {
        return complaintRepository.findByOfficer(officer).stream().filter(c -> c.getResolvedAt() != null).count();
    }

    private Double averageResolutionDays(User officer) {
        List<Complaint> resolved = complaintRepository.findByOfficer(officer).stream()
                .filter(c -> c.getResolvedAt() != null && c.getCreatedAt() != null)
                .toList();
        if (resolved.isEmpty()) {
            return null;
        }
        double avg = resolved.stream().mapToDouble(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toHours() / 24.0).average().orElse(0);
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

    private void addKeyword(List<String> keywords, String value) {
        if (!isBlank(value) && keywords.stream().noneMatch(existing -> existing.equalsIgnoreCase(value))) {
            keywords.add(value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ══════════════════════════════════════════════
    // P3 — Officer Completion: Resolve, Update, Notes, Reassign
    // ══════════════════════════════════════════════

    /**
     * Officer submits resolution for a complaint in IN_PROGRESS state.
     * Transitions complaint to RESOLVED and sets resolvedAt timestamp.
     */
    @Transactional
    public AssignmentComplaintDetailResponse resolveComplaint(String username, Long complaintId,
                                                              OfficerResolutionRequest request) {
        User officer = findOfficer(username);
        ComplaintAssignment assignment = findAssignedToOfficerForAction(officer, complaintId);
        if (assignment.getAssignmentStatus() != AssignmentStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only IN_PROGRESS complaints can be resolved");
        }

        // Transition assignment + complaint status
        AssignmentComplaintDetailResponse response = transitionOfficerAssignment(
                assignment, AssignmentStatus.COMPLETED, "RESOLVED",
                firstNonBlank(request.getRemarks(), request.getResolutionSummary(), "Complaint resolved"),
                officer);

        // Set resolvedAt + finalise the SLA verdict (RESOLVED_WITHIN_SLA / RESOLVED_AFTER_SLA)
        Complaint complaint = assignment.getComplaint();
        LocalDateTime resolvedAt = LocalDateTime.now();
        complaint.setResolvedAt(resolvedAt);
        slaService.markResolvedSla(complaint, resolvedAt);
        complaintRepository.save(complaint);

        // Notify citizen
        notificationService.sendNotification(
                complaint.getCitizen(), complaint,
                NotificationType.COMPLAINT_RESOLVED,
                "Complaint Resolved",
                "Your complaint " + complaint.getComplaintNo() + " has been resolved. " +
                "Summary: " + request.getResolutionSummary() +
                ". Please provide your feedback.");

        // Feedback request notification
        notificationService.sendNotification(
                complaint.getCitizen(), complaint,
                NotificationType.FEEDBACK_REQUESTED,
                "Share Your Feedback",
                "How was our service for complaint " + complaint.getComplaintNo() + "? Rate us 1-5.");

        return response;
    }

    /**
     * Officer posts a public status update visible to the citizen.
     * Optionally also saves an internal note.
     */
    @Transactional
    public AssignmentComplaintDetailResponse addPublicUpdate(String username, Long complaintId,
                                                             OfficerUpdateRequest request) {
        User officer = findOfficer(username);
        ComplaintAssignment assignment = findAssignedToOfficerForAction(officer, complaintId);
        Complaint complaint = assignment.getComplaint();

        // Record as status history entry (public, citizen-visible)
        ComplaintStatusMaster currentStatus = complaint.getCurrentStatus();
        StatusHistory history = StatusHistory.builder()
                .complaint(complaint)
                .status(currentStatus)
                .remarks(request.getMessage())
                .changedBy(officer)
                .changedAt(LocalDateTime.now())
                .build();
        history.setCreatedBy(officer.getUserId());
        statusHistoryRepository.save(history);

        // Save internal note if provided
        if (request.getInternalNote() != null && !request.getInternalNote().isBlank()) {
            OfficerNote note = OfficerNote.builder()
                    .complaint(complaint)
                    .officer(officer)
                    .note(request.getInternalNote())
                    .build();
            note.setCreatedBy(officer.getUserId());
            officerNoteRepository.save(note);
        }

        // Notify citizen of update
        notificationService.sendNotification(
                complaint.getCitizen(), complaint,
                NotificationType.STATUS_UPDATED,
                "Complaint Update",
                "Update on complaint " + complaint.getComplaintNo() + ": " + request.getMessage());

        return detail(complaintId);
    }

    /**
     * Officer adds an internal note (not visible to citizen).
     */
    @Transactional
    public OfficerNoteResponse addInternalNote(String username, Long complaintId, String noteText) {
        User officer = findOfficer(username);
        ComplaintAssignment assignment = findAssignedToOfficerForAction(officer, complaintId);
        Complaint complaint = assignment.getComplaint();

        OfficerNote note = OfficerNote.builder()
                .complaint(complaint)
                .officer(officer)
                .note(noteText)
                .build();
        note.setCreatedBy(officer.getUserId());
        OfficerNote saved = officerNoteRepository.save(note);

        return OfficerNoteResponse.builder()
                .noteId(saved.getNoteId())
                .note(saved.getNote())
                .officerName(officer.getName())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    /**
     * Returns all internal notes for a complaint (officer-only).
     */
    @Transactional(readOnly = true)
    public java.util.List<OfficerNoteResponse> getInternalNotes(String username, Long complaintId) {
        User officer = findOfficer(username);
        ComplaintAssignment assignment = findAssignedToOfficer(officer, complaintId);
        return officerNoteRepository.findByComplaintOrderByCreatedAtDesc(assignment.getComplaint())
                .stream()
                .map(n -> OfficerNoteResponse.builder()
                        .noteId(n.getNoteId())
                        .note(n.getNote())
                        .officerName(n.getOfficer().getName())
                        .createdAt(n.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * Officer requests admin to reassign the complaint.
     * Creates a status history entry and notifies admin users.
     */
    @Transactional
    public AssignmentComplaintDetailResponse requestReassignment(String username, Long complaintId, String reason) {
        User officer = findOfficer(username);
        ComplaintAssignment assignment = findAssignedToOfficer(officer, complaintId);
        Complaint complaint = assignment.getComplaint();

        // Add internal note with reason
        OfficerNote note = OfficerNote.builder()
                .complaint(complaint)
                .officer(officer)
                .note("[REASSIGN REQUEST] " + reason)
                .build();
        note.setCreatedBy(officer.getUserId());
        officerNoteRepository.save(note);

        // Notify all admin users
        userRepository.findByRole(com.SIH.mark1.model.UserRole.ADMIN).forEach(admin ->
                notificationService.sendNotification(
                        admin, complaint,
                        NotificationType.STATUS_UPDATED,
                        "Reassignment Requested",
                        "Officer " + officer.getName() + " requested reassignment for complaint " +
                        complaint.getComplaintNo() + ". Reason: " + reason));

        return detail(complaintId);
    }
}

