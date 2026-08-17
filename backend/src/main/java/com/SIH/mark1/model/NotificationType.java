package com.SIH.mark1.model;

public enum NotificationType {
    COMPLAINT_REGISTERED,
    OFFICER_ASSIGNED,
    STATUS_UPDATED,
    MESSAGE_RECEIVED,
    COMPLAINT_RESOLVED,
    FEEDBACK_REQUESTED,
    /** AI auto-assignment engine assigned a complaint to an officer (admin alert). */
    AUTO_ASSIGNMENT,
    /** Citizen deleted/cancelled their complaint (officer + admin alert). */
    COMPLAINT_CANCELLED,
    /** Complaint has consumed most of its SLA window and is close to breaching. */
    SLA_NEAR_BREACH,
    /** Complaint crossed its SLA deadline while still unresolved. */
    SLA_BREACHED,

    /** Verification call-back could not be connected yet; citizen sees "verification pending". */
    VERIFICATION_PENDING,

    /** Citizen confirmed on the call that the complaint is genuine. */
    VERIFICATION_CONFIRMED,

    /** Citizen denied lodging the complaint — flagged for admin review. */
    VERIFICATION_REJECTED,

    /** All verification call attempts were exhausted without reaching the citizen. */
    VERIFICATION_UNREACHABLE
}


