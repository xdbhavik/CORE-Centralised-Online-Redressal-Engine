package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Citizen-facing view of the call-back verification state of one complaint.
 *
 * <p>Backs {@code GET /api/v1/complaints/{id}/verification} and is also the response of the
 * in-app confirm endpoint, so the app can re-render from a single shape either way.</p>
 *
 * <p>Deliberately carries no raw {@code verificationRemarks}: those hold internal detail
 * ("Call attempt failed: &lt;provider error&gt;") that is noise at best and a leak of our
 * telephony internals at worst. {@link #blockedReason} is the sanitised, human-readable
 * equivalent meant for display.</p>
 *
 * <p>Note that {@link #actionAllowed} says nothing about the SLA clock — that always runs
 * from complaint creation, so a complaint can be past its deadline while still awaiting
 * verification. That combination is intentional and signals a slow call-back pipeline.</p>
 */
@Data
@Builder
public class VerificationStatusResponse {

    private Long          complaintId;
    private String        complaintNumber;

    /** PENDING | VERIFIED | REJECTED | FAILED. */
    private String        verificationStatus;

    /** True once the department may act on the complaint. */
    private boolean       actionAllowed;

    /** True when the citizen denied lodging this complaint; it is awaiting admin review. */
    private boolean       flagged;

    /** Verification calls placed so far, and the budget before we give up. */
    private int           attempts;
    private int           maxAttempts;

    private LocalDateTime lastAttemptAt;

    /** When the next call is due; null when verification is settled or unscheduled. */
    private LocalDateTime nextAttemptAt;

    private LocalDateTime verifiedAt;

    /** Why action is on hold, for display. Null when action is allowed. */
    private String        blockedReason;

    /**
     * Whether the in-app confirm button should be offered.
     *
     * <p>False once the answer is settled — already verified, or explicitly denied on the
     * phone (which only an admin can overturn).</p>
     */
    private boolean       canConfirmInApp;
}
