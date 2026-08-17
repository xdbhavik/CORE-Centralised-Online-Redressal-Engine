package com.SIH.mark1.model;

import java.util.Set;

/**
 * SLA lifecycle status values stored in {@code complaints.sla_status}.
 *
 * <p>Kept as String constants (not an enum) so that older rows / future values
 * never break deserialization, and so the column stays human-readable in SQL.</p>
 *
 * <pre>
 * ON_TRACK             → deadline abhi door hai
 * NEAR_BREACH          → 80% (configurable) time consume ho gaya
 * BREACHED             → deadline nikal gayi, complaint still unresolved
 * RESOLVED_WITHIN_SLA  → deadline se pehle resolve hui (terminal)
 * RESOLVED_AFTER_SLA   → deadline ke baad resolve hui (terminal)
 * </pre>
 */
public final class SlaStatus {

    public static final String ON_TRACK            = "ON_TRACK";
    public static final String NEAR_BREACH         = "NEAR_BREACH";
    public static final String BREACHED            = "BREACHED";
    public static final String RESOLVED_WITHIN_SLA = "RESOLVED_WITHIN_SLA";
    public static final String RESOLVED_AFTER_SLA  = "RESOLVED_AFTER_SLA";

    /** SLA statuses that are final — scheduler inko dobara modify nahi karta. */
    public static final Set<String> TERMINAL_SLA_STATUSES =
            Set.of(RESOLVED_WITHIN_SLA, RESOLVED_AFTER_SLA);

    /** Complaint statuses jinke liye SLA clock band ho jati hai. */
    public static final Set<String> TERMINAL_COMPLAINT_STATUSES =
            Set.of("RESOLVED", "CLOSED", "CANCELLED");

    private SlaStatus() {
        // constants holder
    }

    /** True when the given SLA status is final (complaint already resolved). */
    public static boolean isTerminal(String slaStatus) {
        return slaStatus != null && TERMINAL_SLA_STATUSES.contains(slaStatus);
    }

    /** True when the given complaint status code stops the SLA clock. */
    public static boolean isTerminalComplaintStatus(String statusCode) {
        return statusCode != null && TERMINAL_COMPLAINT_STATUSES.contains(statusCode.toUpperCase());
    }
}
