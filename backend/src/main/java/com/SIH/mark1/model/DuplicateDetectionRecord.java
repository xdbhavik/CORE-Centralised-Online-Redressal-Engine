package com.SIH.mark1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persists the outcome of an AI duplicate-detection check so admins can review
 * POSSIBLE_DUPLICATE / POSSIBLE_RECURRING_ISSUE complaints and confirm or reject them.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "duplicate_detection_result",
        indexes = {
                @Index(name = "idx_duplicate_result_complaint", columnList = "complaint_id"),
                @Index(name = "idx_duplicate_result_review", columnList = "review_status")
        }
)
public class DuplicateDetectionRecord extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long resultId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    /** The existing complaint the new complaint was matched against. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_complaint_id")
    private Complaint matchedComplaint;

    @Column(name = "similarity", nullable = false)
    private Double similarity;

    /** The AI decision at detection time: POSSIBLE_DUPLICATE or POSSIBLE_RECURRING_ISSUE. */
    @Column(name = "decision", nullable = false, length = 40)
    private String decision;

    @Column(name = "scope", length = 40)
    private String scope;

    @Column(name = "resource_match", nullable = false)
    private boolean resourceMatch;

    @Column(name = "location_match", nullable = false)
    private boolean locationMatch;

    /** Human-readable reasons from the decision engine (newline separated). */
    @Column(name = "reasons", columnDefinition = "TEXT")
    private String reasons;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private DuplicateReviewStatus reviewStatus;
}