package com.SIH.mark1.controller;

import com.SIH.mark1.dto.request.OfficerActionRequest;
import com.SIH.mark1.dto.request.OfficerReassignRequest;
import com.SIH.mark1.dto.request.OfficerResolutionRequest;
import com.SIH.mark1.dto.request.OfficerUpdateRequest;
import com.SIH.mark1.dto.response.AssignmentComplaintDetailResponse;
import com.SIH.mark1.dto.response.AssignmentQueueResponse;
import com.SIH.mark1.dto.response.OfficerDashboardResponse;
import com.SIH.mark1.dto.response.OfficerNoteResponse;
import com.SIH.mark1.service.AssignmentManagementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/officer")
@PreAuthorize("hasRole('OFFICER')")
public class OfficerController {

    private final AssignmentManagementService assignmentManagementService;

    public OfficerController(AssignmentManagementService assignmentManagementService) {
        this.assignmentManagementService = assignmentManagementService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<OfficerDashboardResponse> dashboard() {
        return ResponseEntity.ok(assignmentManagementService.officerDashboard(currentUsername()));
    }

    @GetMapping("/complaints")
    public ResponseEntity<List<AssignmentQueueResponse>> complaints(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean overdue) {
        return ResponseEntity.ok(assignmentManagementService.officerComplaints(
                currentUsername(), status, priority, category, date, overdue));
    }

    @GetMapping("/complaints/{id}")
    public ResponseEntity<AssignmentComplaintDetailResponse> complaintDetail(@PathVariable("id") Long complaintId) {
        return ResponseEntity.ok(assignmentManagementService.officerComplaintDetail(currentUsername(), complaintId));
    }

    @PostMapping("/complaints/{id}/accept")
    public ResponseEntity<AssignmentComplaintDetailResponse> accept(
            @PathVariable("id") Long complaintId,
            @RequestBody(required = false) OfficerActionRequest request) {
        return ResponseEntity.ok(assignmentManagementService.acceptAssignment(
                currentUsername(), complaintId, request != null ? request.getRemarks() : null));
    }

    @PostMapping("/complaints/{id}/start")
    public ResponseEntity<AssignmentComplaintDetailResponse> start(
            @PathVariable("id") Long complaintId,
            @RequestBody(required = false) OfficerActionRequest request) {
        return ResponseEntity.ok(assignmentManagementService.startWork(
                currentUsername(), complaintId, request != null ? request.getRemarks() : null));
    }

    /** Officer submits resolution — transitions complaint to RESOLVED. */
    @PostMapping("/complaints/{id}/resolve")
    public ResponseEntity<AssignmentComplaintDetailResponse> resolve(
            @PathVariable("id") Long complaintId,
            @Valid @RequestBody OfficerResolutionRequest request) {
        return ResponseEntity.ok(assignmentManagementService.resolveComplaint(
                currentUsername(), complaintId, request));
    }

    /** Officer posts a public update (visible to citizen) + optional internal note. */
    @PostMapping("/complaints/{id}/update")
    public ResponseEntity<AssignmentComplaintDetailResponse> update(
            @PathVariable("id") Long complaintId,
            @Valid @RequestBody OfficerUpdateRequest request) {
        return ResponseEntity.ok(assignmentManagementService.addPublicUpdate(
                currentUsername(), complaintId, request));
    }

    /** Officer adds an internal note (not visible to citizen). */
    @PostMapping("/complaints/{id}/note")
    public ResponseEntity<OfficerNoteResponse> addNote(
            @PathVariable("id") Long complaintId,
            @RequestParam String note) {
        return ResponseEntity.ok(assignmentManagementService.addInternalNote(
                currentUsername(), complaintId, note));
    }

    /** Returns all internal notes for a complaint (officer only). */
    @GetMapping("/complaints/{id}/notes")
    public ResponseEntity<List<OfficerNoteResponse>> getNotes(@PathVariable("id") Long complaintId) {
        return ResponseEntity.ok(assignmentManagementService.getInternalNotes(currentUsername(), complaintId));
    }

    /** Officer requests admin for reassignment with reason. */
    @PostMapping("/complaints/{id}/request-reassign")
    public ResponseEntity<AssignmentComplaintDetailResponse> requestReassign(
            @PathVariable("id") Long complaintId,
            @Valid @RequestBody OfficerReassignRequest request) {
        return ResponseEntity.ok(assignmentManagementService.requestReassignment(
                currentUsername(), complaintId, request.getReason()));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
