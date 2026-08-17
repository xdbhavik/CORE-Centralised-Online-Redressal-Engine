package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Verification counters for the admin dashboard.
 *
 * <p>Read as a health check on the call-back pipeline rather than a per-complaint view:
 * a large {@link #pending} means we are not reaching citizens fast enough, and since the
 * SLA clock never pauses for verification that backlog is silently eating into
 * departmental deadlines. {@link #flagged} is the only bucket that needs human action.</p>
 */
@Data
@Builder
public class VerificationSummaryResponse {

    /** Awaiting a call-back or its retries. */
    private long pending;

    /** Citizen confirmed the complaint is genuine. */
    private long verified;

    /** Citizen denied lodging the complaint. */
    private long rejected;

    /** All call attempts exhausted without reaching the citizen. */
    private long failed;

    /**
     * Denied complaints still awaiting admin review.
     *
     * <p>Tracked separately from {@link #rejected} because an admin override clears the flag
     * but leaves the recorded denial intact — so this is the actionable queue size, while
     * {@code rejected} is the historical total.</p>
     */
    private long flagged;

    /** Complaints the department may currently act on. */
    private long actionAllowed;

    /** Complaints held back by the verification gate ({@code total - actionAllowed}). */
    private long actionBlocked;

    private long total;
}
