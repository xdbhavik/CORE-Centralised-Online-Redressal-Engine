package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AssignmentQueueResponse {
    /** SLA deadline for this complaint (creation time + priority window). */
    private java.time.LocalDateTime slaDueAt;
    /** ON_TRACK | NEAR_BREACH | BREACHED | RESOLVED_WITHIN_SLA | RESOLVED_AFTER_SLA. */
    private String slaStatus;

    private Long complaintId;

    private String complaintNo;
    private String title;
    private String citizenName;
    private String location;
    private String category;
    private String department;
    private String priority;
    private String status;
    private String assignmentStatus;
    private String suggestedDepartment;
    private Integer aiConfidence;
    private LocalDateTime submittedTime;
    private LocalDateTime assignedDate;
    private LocalDate dueDate;
    private boolean overdue;
    /** Officer to whom the complaint is currently assigned (null when unassigned). */
    private String officerName;
    /** Who assigned it: "AI" (auto-assignment engine) or "MANUAL" (admin). */
    private String assignedByType;

    // ── Citizen call-back verification ──
    /** PENDING | VERIFIED | REJECTED | FAILED. */
    private String verificationStatus;
    /**
     * True once the department may act on this complaint.
     *
     * <p>Surfaced in the queue so an admin can see at a glance which items are on hold
     * before trying to assign one and being refused.</p>
     */
    private boolean actionAllowed;
    /** True when the citizen denied lodging it; needs admin review. */
    private boolean verificationFlagged;
}

