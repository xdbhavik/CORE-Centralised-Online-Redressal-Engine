package com.SIH.mark1.model;

/**
 * Identifies WHO assigned a complaint to an officer.
 * AI     — assigned automatically by the AI auto-assignment engine.
 * MANUAL — assigned by an admin through the assignment queue.
 */
public enum AssignedByType {
    AI,
    MANUAL
}
