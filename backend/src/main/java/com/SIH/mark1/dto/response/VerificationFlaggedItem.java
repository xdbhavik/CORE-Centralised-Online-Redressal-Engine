package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One row of the admin verification review queue.
 *
 * <p>These are the complaints the citizen denied lodging when we called them back. Action on
 * them is blocked until an admin either overrides the denial or confirms the complaint is
 * fake, so this queue is the only thing standing between a mistaken "2" keypress and a real
 * grievance being stranded — it carries enough context to make that call without opening
 * each complaint.</p>
 *
 * <p>Unlike {@link VerificationStatusResponse} this does include the raw
 * {@code verificationRemarks}: an admin deciding an override needs the internal detail of
 * what actually happened on the call.</p>
 */
@Data
@Builder
public class VerificationFlaggedItem {

    private Long          complaintId;
    private String        complaintNumber;
    private String        title;
    private String        description;

    private String        citizenName;
    private String        citizenMobile;

    private String        department;
    private String        category;
    private String        priority;

    /** Complaint lifecycle status, distinct from the verification status below. */
    private String        status;

    /** PENDING | VERIFIED | REJECTED | FAILED. */
    private String        verificationStatus;
    private boolean       actionAllowed;
    private boolean       flagged;

    private int           attempts;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime verifiedAt;

    /** Internal detail of the last verification outcome, including admin override notes. */
    private String        remarks;

    private LocalDateTime createdAt;

    // ── SLA context ──
    /**
     * Carried so the queue shows the cost of leaving a complaint here.
     *
     * <p>The SLA clock runs from creation and is never paused by verification, so a flagged
     * complaint keeps ageing towards its deadline while it waits for review.</p>
     */
    private LocalDateTime slaDueAt;
    private String        slaStatus;
}
