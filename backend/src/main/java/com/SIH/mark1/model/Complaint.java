package com.SIH.mark1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "complaints",
        indexes = {
                @Index(name = "idx_complaint_no", columnList = "complaint_no", unique = true),
                @Index(name = "idx_complaints_status", columnList = "status_id"),
                @Index(name = "idx_complaints_priority", columnList = "priority_id"),
                @Index(name = "idx_complaints_location", columnList = "ward, city, state, pincode")
        }
)
public class Complaint extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "complaint_id")
    private Long complaintId;

    @Column(name = "complaint_no", nullable = false, unique = true, length = 40)
    private String complaintNo;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User citizen;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id")
    private User officer;

   
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private ComplaintStatusMaster currentStatus;

   
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "priority_id", nullable = false)
    private PriorityMaster priority;

   
    @Column(nullable = false, length = 150)
    private String title;

    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_address", columnDefinition = "TEXT")
    private String locationAddress;

    @Column(length = 80)
    private String ward;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // ─────────────────────────────────────────────────────────────
    // SLA tracking (complaint-level official deadline)
    //
    // NOTE: ComplaintAssignment.dueDate officer ka task deadline hai;
    //       slaDueAt is the official citizen-facing SLA deadline.
    // ─────────────────────────────────────────────────────────────

    /** Official SLA deadline = createdAt + priority SLA hours. */
    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    /** One of {@link SlaStatus} constants. */
    @Builder.Default
    @Column(name = "sla_status", length = 30)
    private String slaStatus = SlaStatus.ON_TRACK;

    // ─────────────────────────────────────────────────────────────
    // Channel + citizen verification (IVR)
    // NOTE: the SLA clock always starts at creation regardless of verification
    //       state, so a citizen's deadline never depends on us reaching them
    //       by phone. Verification is an independent anti-spam gate.
    // ─────────────────────────────────────────────────────────────

    /** Where the complaint came from — one of {@link SourceChannel} constants. */
    @Builder.Default
    @Column(name = "source_channel", length = 20)
    private String sourceChannel = SourceChannel.APP;

    /** Citizen call-back verification state — one of {@link VerificationStatus} constants. */
    @Builder.Default
    @Column(name = "verification_status", length = 20)
    private String verificationStatus = VerificationStatus.PENDING;

    /** When the citizen confirmed (or the IVR auto-confirmed) this complaint. */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** Outbound verification call attempts made so far. */
    @Builder.Default
    @Column(name = "verification_attempts")
    private Integer verificationAttempts = 0;

    /**
     * When the next verification call attempt becomes due.
     *
     * <p>Null means "not scheduled": either verification has reached a terminal state or
     * no attempt has been queued yet. The retry scheduler only picks up rows whose value
     * is in the past, which is what spaces the attempts out instead of redialling a
     * switched-off phone in a tight loop.</p>
     */
    @Column(name = "verification_next_attempt_at")
    private LocalDateTime verificationNextAttemptAt;

    /** Last outbound attempt time, for support staff answering "when did you call me?". */
    @Column(name = "verification_last_attempt_at")
    private LocalDateTime verificationLastAttemptAt;

    /** Why the citizen said the complaint was not theirs, or why calling failed. */
    @Column(name = "verification_remarks", length = 500)
    private String verificationRemarks;

    /** True when the citizen answered NO — normal action stops pending admin review. */
    @Builder.Default
    @Column(name = "verification_flagged")
    private Boolean verificationFlagged = false;

    /**
     * True once departmental action may proceed.
     *
     * <p>Derived from {@link #verificationStatus} but stored so queries and the officer
     * queue can filter on a single indexed boolean instead of re-deriving policy
     * (which depends on config) in every caller.</p>
     */
    @Builder.Default
    @Column(name = "action_allowed")
    private Boolean actionAllowed = false;



    /** Set once, the first time the SLA deadline was crossed while unresolved. */
    @Column(name = "sla_breached_at")
    private LocalDateTime slaBreachedAt;

    /** Set once, when the NEAR_BREACH reminder was dispatched (dedupe guard). */
    @Column(name = "sla_reminder_sent_at")
    private LocalDateTime slaReminderSentAt;
}

