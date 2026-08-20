package com.SIH.mark1.controller;

import com.SIH.mark1.ai.qdrant.KnowledgeSyncService;
import com.SIH.mark1.dto.request.AutoAssignmentSettingRequest;
import com.SIH.mark1.dto.request.CategoryRequest;
import com.SIH.mark1.dto.request.ComplaintAssignRequest;
import com.SIH.mark1.dto.request.DepartmentRequest;
import com.SIH.mark1.dto.request.OfficerRequest;
import com.SIH.mark1.dto.response.AnalyticsResponse;
import com.SIH.mark1.dto.response.AutoAssignmentSettingResponse;
import com.SIH.mark1.dto.response.AutoAssignmentStatsResponse;
import com.SIH.mark1.dto.response.AssignmentComplaintDetailResponse;
import com.SIH.mark1.dto.response.AssignmentHistoryResponse;
import com.SIH.mark1.dto.response.AssignmentOfficerResponse;
import com.SIH.mark1.dto.response.AssignmentQueueResponse;
import com.SIH.mark1.dto.response.AssignmentRecommendationResponse;
import com.SIH.mark1.dto.response.CategoryResponse;
import com.SIH.mark1.dto.response.ComplaintAdminResponse;
import com.SIH.mark1.dto.response.DashboardResponse;
import com.SIH.mark1.dto.response.DepartmentResponse;
import com.SIH.mark1.dto.response.DuplicateReviewItem;
import com.SIH.mark1.dto.response.OfficerResponse;
import com.SIH.mark1.dto.response.ReportsResponse;
import com.SIH.mark1.dto.request.VerificationOverrideRequest;
import com.SIH.mark1.dto.response.UserResponse;
import com.SIH.mark1.dto.response.VerificationFlaggedItem;
import com.SIH.mark1.dto.response.VerificationSummaryResponse;

import com.SIH.mark1.model.SystemSetting;
import com.SIH.mark1.model.User;
import com.SIH.mark1.service.AssignmentManagementService;
import com.SIH.mark1.service.AutoAssignmentService;
import com.SIH.mark1.service.SystemSettingService;
import com.SIH.mark1.service.admin.AdminDuplicateService;
import com.SIH.mark1.service.admin.AdminService;
import com.SIH.mark1.service.admin.AdminVerificationService;

import com.SIH.mark1.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AssignmentManagementService assignmentManagementService;
    private final AdminDuplicateService adminDuplicateService;
    private final AdminVerificationService adminVerificationService;

    private final ObjectProvider<KnowledgeSyncService> knowledgeSyncServiceProvider;
    private final SystemSettingService systemSettingService;
    private final AutoAssignmentService autoAssignmentService;
    private final UserRepository userRepository;

    public AdminController(AdminService adminService,
                           AssignmentManagementService assignmentManagementService,
                           AdminDuplicateService adminDuplicateService,
                           AdminVerificationService adminVerificationService,
                           ObjectProvider<KnowledgeSyncService> knowledgeSyncServiceProvider,
                           SystemSettingService systemSettingService,
                           AutoAssignmentService autoAssignmentService,
                           UserRepository userRepository) {
        this.adminService = adminService;
        this.assignmentManagementService = assignmentManagementService;
        this.adminDuplicateService = adminDuplicateService;
        this.adminVerificationService = adminVerificationService;

        this.knowledgeSyncServiceProvider = knowledgeSyncServiceProvider;
        this.systemSettingService = systemSettingService;
        this.autoAssignmentService = autoAssignmentService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboard());
    }

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentResponse>> getDepartments() {
        return ResponseEntity.ok(adminService.getDepartments());
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest req) {
        return ResponseEntity.ok(adminService.createDepartment(req));
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<DepartmentResponse> updateDepartment(@PathVariable("id") Long departmentId,
                                                               @Valid @RequestBody DepartmentRequest req) {
        return ResponseEntity.ok(adminService.updateDepartment(departmentId, req));
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable("id") Long departmentId) {
        adminService.deleteDepartment(departmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(adminService.getCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.ok(adminService.createCategory(req));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable("id") Long categoryId,
                                                           @Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.ok(adminService.updateCategory(categoryId, req));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable("id") Long categoryId) {
        adminService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/officers")
    public ResponseEntity<List<OfficerResponse>> getOfficers() {
        return ResponseEntity.ok(adminService.getOfficers());
    }

    /**
     * Returns every active user (citizens, officers, admins) for admin management.
     * Excludes soft-deleted users and never exposes password hashes.
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PostMapping("/officers")
    public ResponseEntity<OfficerResponse> createOfficer(@Valid @RequestBody OfficerRequest req) {
        return ResponseEntity.ok(adminService.createOfficer(req));
    }

    @PutMapping("/officers/{id}")
    public ResponseEntity<OfficerResponse> updateOfficer(@PathVariable("id") Long officerId,
                                                         @Valid @RequestBody OfficerRequest req) {
        return ResponseEntity.ok(adminService.updateOfficer(officerId, req));
    }

    @DeleteMapping("/officers/{id}")
    public ResponseEntity<Void> deleteOfficer(@PathVariable("id") Long officerId) {
        adminService.deleteOfficer(officerId);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/assignments/pending")
    public ResponseEntity<List<AssignmentQueueResponse>> getPendingAssignments() {
        return ResponseEntity.ok(assignmentManagementService.pendingQueue());
    }

    @GetMapping("/assignments/complaints/{id}")
    public ResponseEntity<AssignmentComplaintDetailResponse> getAssignmentComplaintDetail(@PathVariable("id") Long complaintId) {
        return ResponseEntity.ok(assignmentManagementService.detail(complaintId));
    }

    @GetMapping("/assignments/departments/{departmentId}/officers")
    public ResponseEntity<List<AssignmentOfficerResponse>> getDepartmentOfficers(
            @PathVariable Long departmentId,
            @RequestParam(required = false) Long complaintId) {
        return ResponseEntity.ok(assignmentManagementService.officersByDepartment(departmentId, complaintId));
    }

    @GetMapping("/assignments/complaints/{id}/recommendation")
    public ResponseEntity<AssignmentRecommendationResponse> getOfficerRecommendation(
            @PathVariable("id") Long complaintId,
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(assignmentManagementService.recommend(complaintId, departmentId));
    }

    @GetMapping("/assignments/complaints/{id}/history")
    public ResponseEntity<List<AssignmentHistoryResponse>> getAssignmentHistory(@PathVariable("id") Long complaintId) {
        return ResponseEntity.ok(assignmentManagementService.history(complaintId));
    }

    @GetMapping("/complaints")
    public ResponseEntity<List<ComplaintAdminResponse>> getComplaints() {
        return ResponseEntity.ok(adminService.getComplaints());
    }

    /** Paginated complaints: ?page=0&size=10 (newest first). */
    @GetMapping("/complaints/paginated")
    public ResponseEntity<Page<ComplaintAdminResponse>> getComplaintsPaginated(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(adminService.getComplaintsPaginated(page, size));
    }

    /** Paginated users: ?page=0&size=10&role=CITIZEN (role optional). */
    @GetMapping("/users/paginated")
    public ResponseEntity<Page<UserResponse>> getAllUsersPaginated(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "role", required = false) String role) {
        return ResponseEntity.ok(adminService.getAllUsersPaginated(page, size, role));
    }

    /** Paginated pending (AI_ANALYZED) queue: ?page=0&size=10. */
    @GetMapping("/assignments/pending/paginated")
    public ResponseEntity<Page<AssignmentQueueResponse>> getPendingAssignmentsPaginated(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(assignmentManagementService.pendingQueuePaginated(page, size));
    }

    /** Active assignments past their due date — escalation view. */
    @GetMapping("/assignments/overdue")
    public ResponseEntity<List<AssignmentQueueResponse>> getOverdueAssignments() {
        return ResponseEntity.ok(assignmentManagementService.overdueAssignments());
    }

    /** Active assignments due today. */
    @GetMapping("/assignments/due-today")
    public ResponseEntity<List<AssignmentQueueResponse>> getDueTodayAssignments() {
        return ResponseEntity.ok(assignmentManagementService.dueTodayAssignments());
    }

    @GetMapping("/complaints/{id}")
    public ResponseEntity<ComplaintAdminResponse> getComplaintById(@PathVariable("id") Long complaintId) {
        return ResponseEntity.ok(adminService.getComplaintById(complaintId));
    }

    @PutMapping("/complaints/assign")
    public ResponseEntity<AssignmentComplaintDetailResponse> assignComplaint(@Valid @RequestBody ComplaintAssignRequest req) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(assignmentManagementService.assign(req, authentication.getName(), false));
    }

    @PutMapping("/complaints/reassign")
    public ResponseEntity<AssignmentComplaintDetailResponse> reassignComplaint(@Valid @RequestBody ComplaintAssignRequest req) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(assignmentManagementService.assign(req, authentication.getName(), true));
    }

    // ─────────────────────────────────────────────
    // AI AUTO-ASSIGNMENT — mode switch + visibility
    // ─────────────────────────────────────────────

    /**
     * Returns the current assignment mode for the admin panel switch.
     * enabled=true → AI auto-assigns new complaints; false → manual queue.
     */
    @GetMapping("/settings/auto-assignment")
    public ResponseEntity<AutoAssignmentSettingResponse> getAutoAssignmentSetting() {
        boolean enabled = systemSettingService.isAutoAssignmentEnabled();
        SystemSetting setting = systemSettingService.getAutoAssignmentSetting();
        return ResponseEntity.ok(AutoAssignmentSettingResponse.builder()
                .enabled(enabled)
                .mode(enabled ? "AI" : "MANUAL")
                .description(setting != null ? setting.getDescription()
                        : "When true, AI automatically assigns new complaints to the best-matched officer of the detected department.")
                .updatedAt(setting != null ? setting.getUpdatedAt() : null)
                .build());
    }

    /**
     * Switches the assignment mode between AI and MANUAL.
     * Body: { "enabled": true } → AI mode, { "enabled": false } → manual mode.
     */
    @PutMapping("/settings/auto-assignment")
    public ResponseEntity<AutoAssignmentSettingResponse> updateAutoAssignmentSetting(
            @Valid @RequestBody AutoAssignmentSettingRequest req) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long adminId = resolveAdminUserId(authentication != null ? authentication.getName() : null);
        SystemSetting saved = systemSettingService.setAutoAssignmentEnabled(
                Boolean.TRUE.equals(req.getEnabled()), adminId);
        return ResponseEntity.ok(AutoAssignmentSettingResponse.builder()
                .enabled(Boolean.TRUE.equals(req.getEnabled()))
                .mode(Boolean.TRUE.equals(req.getEnabled()) ? "AI" : "MANUAL")
                .description(saved.getDescription())
                .updatedAt(saved.getUpdatedAt())
                .build());
    }

    /**
     * Lists every complaint the AI engine has assigned to officers
     * (assignedByType = AI), newest first.
     */
    @GetMapping("/assignments/auto")
    public ResponseEntity<List<AssignmentQueueResponse>> getAutoAssignedComplaints() {
        return ResponseEntity.ok(assignmentManagementService.autoAssignedComplaints());
    }

    /**
     * AI vs MANUAL assignment summary (counts, last 7 days, per-department).
     */
    @GetMapping("/assignments/auto/stats")
    public ResponseEntity<AutoAssignmentStatsResponse> getAutoAssignmentStats() {
        return ResponseEntity.ok(assignmentManagementService.autoAssignmentStats());
    }

    /**
     * Runs the AI auto-assignment engine over every complaint still waiting in
     * the AI_ANALYZED manual queue. Requires AI mode to be ON.
     */
    @PostMapping("/assignments/auto/run-pending")
    public ResponseEntity<Map<String, Object>> runAutoAssignmentForPending() {
        int assigned = autoAssignmentService.runForPendingQueue();
        return ResponseEntity.ok(Map.of(
                "autoAssigned", assigned,
                "message", assigned + " complaint(s) auto-assigned by AI from the pending queue."));
    }

    private Long resolveAdminUserId(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByMobile(username)
                .or(() -> userRepository.findByEmail(username))
                .map(User::getUserId)
                .orElse(null);
    }

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics() {
        return ResponseEntity.ok(adminService.getAnalytics());
    }

    @GetMapping("/reports")
    public ResponseEntity<ReportsResponse> getReports() {
        return ResponseEntity.ok(adminService.getReports());
    }

    /**
     * Returns the queue of complaints flagged as POSSIBLE_DUPLICATE / POSSIBLE_RECURRING_ISSUE
     * awaiting admin review.
     */
    @GetMapping("/duplicates/review")
    public ResponseEntity<List<DuplicateReviewItem>> getDuplicateReviewQueue() {
        return ResponseEntity.ok(adminDuplicateService.reviewQueue());
    }

    /**
     * Admin confirms a flagged complaint is a duplicate of its matched complaint.
     * Links the pair and transitions the complaint to DUPLICATE status.
     */
    @PostMapping("/duplicates/{complaintId}/confirm")
    public ResponseEntity<DuplicateReviewItem> confirmDuplicate(@PathVariable("complaintId") Long complaintId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(adminDuplicateService.confirmDuplicate(complaintId, authentication.getName()));
    }

    /**
     * Admin reviews a flagged complaint and determines it is NOT a duplicate.
     * The complaint continues its normal lifecycle.
     */
    @PostMapping("/duplicates/{complaintId}/reject")
    public ResponseEntity<DuplicateReviewItem> rejectDuplicate(@PathVariable("complaintId") Long complaintId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(adminDuplicateService.rejectDuplicate(complaintId, authentication.getName()));
    }

    // ─────────────────────────────────────────────
    // CITIZEN CALL-BACK VERIFICATION — review + override
    // ─────────────────────────────────────────────

    /**
     * Complaints the citizen denied lodging on the verification call, newest first.
     *
     * <p>These are blocked from all departmental action until an admin rules on them, so this
     * queue needs working through — a genuine complaint denied by a mis-pressed key sits here
     * with its SLA clock still running.</p>
     */
    @GetMapping("/verification/flagged")
    public ResponseEntity<List<VerificationFlaggedItem>> getFlaggedVerifications() {
        return ResponseEntity.ok(adminVerificationService.flaggedQueue());
    }

    /**
     * Verification counts by status for the dashboard.
     *
     * <p>A rising pending count is the signal that our call-back pipeline is lagging, which
     * matters because the SLA clock runs from complaint creation regardless.</p>
     */
    @GetMapping("/verification/summary")
    public ResponseEntity<VerificationSummaryResponse> getVerificationSummary() {
        return ResponseEntity.ok(adminVerificationService.summary());
    }

    /**
     * Admin override: releases a flagged complaint so the department can act on it.
     *
     * <p>{@code reason} is mandatory — overriding a citizen's explicit denial is the single
     * action here that most needs to be attributable afterwards.</p>
     */
    @PostMapping("/verification/{complaintId}/approve")
    public ResponseEntity<VerificationFlaggedItem> approveVerification(
            @PathVariable("complaintId") Long complaintId,
            @Valid @RequestBody VerificationOverrideRequest req) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(adminVerificationService.approve(
                complaintId, authentication.getName(), req.getReason()));
    }

    /**
     * Admin override: confirms the complaint is fake, keeps it blocked and closes it.
     *
     * <p>{@code reason} is mandatory, for the same audit reason as approval.</p>
     */
    @PostMapping("/verification/{complaintId}/reject")
    public ResponseEntity<VerificationFlaggedItem> rejectVerification(
            @PathVariable("complaintId") Long complaintId,
            @Valid @RequestBody VerificationOverrideRequest req) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(adminVerificationService.reject(
                complaintId, authentication.getName(), req.getReason()));
    }

    /**
     * Reindexes all MySQL complaints into the Qdrant {@code complaint_history} collection.

     * Required before duplicate detection can work on existing data.
     */
    @PostMapping("/rag/reindex/complaints")
    public ResponseEntity<Map<String, Object>> reindexComplaints() {
        KnowledgeSyncService syncService = knowledgeSyncServiceProvider.getIfAvailable();
        if (syncService == null) {
            return ResponseEntity.ok(Map.of(
                    "indexed", 0,
                    "message", "Qdrant not configured — complaint reindex skipped."));
        }
        int indexed = syncService.reindexAllComplaints();
        return ResponseEntity.ok(Map.of(
                "indexed", indexed,
                "collection", KnowledgeSyncService.COLLECTION_COMPLAINT_HISTORY,
                "message", "Complaint history reindex completed."));
    }
}


