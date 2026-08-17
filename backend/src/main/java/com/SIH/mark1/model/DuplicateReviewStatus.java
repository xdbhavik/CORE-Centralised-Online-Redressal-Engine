package com.SIH.mark1.model;

/**
 * Review state of a possible duplicate complaint in the admin review queue.
 */
public enum DuplicateReviewStatus {
    /** Awaiting admin review. */
    PENDING,
    /** Admin confirmed the complaint is a duplicate of an existing complaint. */
    CONFIRMED_DUPLICATE,
    /** Admin reviewed and determined the complaint is NOT a duplicate. */
    NOT_DUPLICATE
}