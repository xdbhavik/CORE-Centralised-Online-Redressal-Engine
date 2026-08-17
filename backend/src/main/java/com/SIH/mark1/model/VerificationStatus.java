package com.SIH.mark1.model;

/**
 * Citizen call-back verification state for a complaint (verification #1).
 *
 * <p>This is an anti-spam / authenticity gate: the system calls the citizen back and
 * asks whether they really lodged the complaint. It deliberately does <strong>not</strong>
 * gate the SLA clock — see the note on {@link Complaint#getSlaDueAt()} — so a genuine
 * citizen never loses deadline time because a call could not be connected.</p>
 */
public final class VerificationStatus {

    /** Awaiting an outbound verification call (or its retries). */
    public static final String PENDING = "PENDING";

    /** Citizen confirmed the complaint is genuine. */
    public static final String VERIFIED = "VERIFIED";

    /** Citizen denied lodging the complaint — flagged for admin review. */
    public static final String REJECTED = "REJECTED";

    /** All retry attempts exhausted without reaching the citizen. */
    public static final String FAILED = "FAILED";

    private VerificationStatus() {
        // constants holder
    }

    public static boolean isValid(String value) {
        return PENDING.equals(value)
                || VERIFIED.equals(value)
                || REJECTED.equals(value)
                || FAILED.equals(value);
    }

    /** True when no further verification calls should be attempted. */
    public static boolean isTerminal(String value) {
        return VERIFIED.equals(value) || REJECTED.equals(value) || FAILED.equals(value);
    }
}
