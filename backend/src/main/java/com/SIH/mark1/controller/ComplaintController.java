package com.SIH.mark1.controller;

import com.SIH.mark1.dto.request.CreateComplaintRequest;
import com.SIH.mark1.dto.request.UpdateComplaintRequest;
import com.SIH.mark1.dto.response.ComplaintDetailsResponse;
import com.SIH.mark1.dto.response.ComplaintResponse;
import com.SIH.mark1.dto.response.TimelineDTO;
import com.SIH.mark1.dto.response.VerificationStatusResponse;

import com.SIH.mark1.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Citizen-facing REST endpoints for the complaint module.
 *
 * Base path : /api/v1/complaints
 * Security  : JWT required; all endpoints restricted to CITIZEN role.
 *
 * Endpoints:
 *   POST   /                   → createComplaint
 *   GET    /my                 → getMyComplaints
 *   GET    /{id}               → getComplaintById
 *   PUT    /{id}               → updateComplaint
 *   DELETE /{id}               → deleteComplaint
 *   POST   /{id}/media         → uploadMedia
 *   GET    /{id}/timelineDTOs  → getTimeline
 */
@RestController
@RequestMapping("/api/v1/complaints")
@PreAuthorize("hasRole('CITIZEN')")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/v1/complaints
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a new complaint for the authenticated citizen.
     *
     * @param request complaint creation payload (validated)
     * @return 201 Created with complaint number and status
     */
    @PostMapping
    public ResponseEntity<ComplaintResponse> createComplaint(
            @Valid @RequestBody CreateComplaintRequest request) {

        String mobile = currentUserMobile();
        ComplaintResponse response = complaintService.createComplaint(mobile, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/v1/complaints/my
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns all complaints submitted by the authenticated citizen.
     *
     * @return 200 OK with list of complaint summaries
     */
    @GetMapping("/my")
    public ResponseEntity<List<ComplaintResponse>> getMyComplaints() {
        String mobile = currentUserMobile();
        return ResponseEntity.ok(complaintService.getMyComplaints(mobile));
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/v1/complaints/{id}
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns full details for a specific complaint.
     * Ownership is enforced — citizens can only view their own complaints.
     *
     * @param complaintId database ID of the complaint
     * @return 200 OK with complaint details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ComplaintDetailsResponse> getComplaintById(
            @PathVariable("id") Long complaintId) {

        String mobile = currentUserMobile();
        return ResponseEntity.ok(complaintService.getComplaintById(mobile, complaintId));
    }

    // ─────────────────────────────────────────────────────────────
    // PUT /api/v1/complaints/{id}
    // ─────────────────────────────────────────────────────────────

    /**
     * Partially updates a complaint (title / description / address).
     * Blocked if complaint status is RESOLVED or CLOSED.
     *
     * @param complaintId database ID of the complaint
     * @param request     fields to update (all optional)
     * @return 200 OK with updated complaint details
     */
    @PutMapping("/{id}")
    public ResponseEntity<ComplaintDetailsResponse> updateComplaint(
            @PathVariable("id") Long complaintId,
            @Valid @RequestBody UpdateComplaintRequest request) {

        String mobile = currentUserMobile();
        return ResponseEntity.ok(complaintService.updateComplaint(mobile, complaintId, request));
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE /api/v1/complaints/{id}
    // ─────────────────────────────────────────────────────────────

    /**
     * Soft-deletes the complaint (sets is_deleted = true).
     * Blocked if complaint status is RESOLVED or CLOSED.
     *
     * @param complaintId database ID of the complaint
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComplaint(@PathVariable("id") Long complaintId) {
        String mobile = currentUserMobile();
        complaintService.deleteComplaint(mobile, complaintId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/v1/complaints/{id}/media
    // ─────────────────────────────────────────────────────────────

    /**
     * Uploads a media file (JPG / PNG / PDF / MP4) for a complaint.
     *
     * @param complaintId database ID of the complaint
     * @param file        multipart file from the Flutter app
     * @return 200 OK with upload confirmation
     */
    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplaintResponse> uploadMedia(
            @PathVariable("id") Long complaintId,
            @RequestParam("file") MultipartFile file) {

        String mobile = currentUserMobile();
        return ResponseEntity.ok(complaintService.uploadMedia(mobile, complaintId, file));
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/v1/complaints/{id}/timelineDTOs
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the complete status timeline for a complaint, ordered oldest → newest.
     *
     * Example response:
     * [
     *   { "status": "REGISTERED", "time": "2026-08-06T10:00", "remarks": "..." },
     *   { "status": "ASSIGNED",   "time": "2026-08-06T10:15", "remarks": "..." }
     * ]
     *
     * @param complaintId database ID of the complaint
     * @return 200 OK with ordered list of timeline events
     */
    @GetMapping("/{id}/timelineDTOs")
    public ResponseEntity<List<TimelineDTO>> getTimeline(
            @PathVariable("id") Long complaintId) {

        String mobile = currentUserMobile();
        return ResponseEntity.ok(complaintService.getTimeline(mobile, complaintId));
    }

    /** Citizen marks a RESOLVED complaint as CLOSED. */
    @PostMapping("/{id}/close")
    public ResponseEntity<ComplaintDetailsResponse> closeComplaint(@PathVariable("id") Long complaintId) {
        String mobile = currentUserMobile();
        return ResponseEntity.ok(complaintService.closeComplaint(mobile, complaintId));
    }

    /** Citizen reopens a RESOLVED complaint with a reason. */
    @PostMapping("/{id}/reopen")
    public ResponseEntity<ComplaintDetailsResponse> reopenComplaint(
            @PathVariable("id") Long complaintId,
            @Valid @RequestBody com.SIH.mark1.dto.request.ReopenRequest request) {
        String mobile = currentUserMobile();
        return ResponseEntity.ok(complaintService.reopenComplaint(mobile, complaintId, request));
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPER
    // ─────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────
    // Call-back verification
    // ─────────────────────────────────────────────────────────────

    /**
     * Verification state of the citizen's own complaint: status, attempts made, next retry.
     *
     * <p>Lets the app explain why a complaint is on hold instead of showing an unexplained
     * stall while the call-back pipeline works through its retries.</p>
     */
    @GetMapping("/{id}/verification")
    public ResponseEntity<VerificationStatusResponse> getVerificationStatus(
            @PathVariable("id") Long complaintId) {

        String mobile = currentUserMobile();
        return ResponseEntity.ok(complaintService.getVerificationStatus(mobile, complaintId));
    }

    /**
     * Citizen confirms in-app that they lodged this complaint, without waiting for our call.
     *
     * <p>Ownership is enforced in the service layer, so one citizen can never confirm
     * another's complaint — that would defeat the anti-fake-complaint control this whole
     * flow exists for. Safe to call twice: a repeat confirmation just returns current state.</p>
     */
    @PostMapping("/{id}/verification/confirm")
    public ResponseEntity<VerificationStatusResponse> confirmVerification(
            @PathVariable("id") Long complaintId) {

        String mobile = currentUserMobile();
        return ResponseEntity.ok(complaintService.confirmVerification(mobile, complaintId));
    }

    /** Extracts the mobile number (JWT subject) of the currently authenticated user. */
    private String currentUserMobile() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
