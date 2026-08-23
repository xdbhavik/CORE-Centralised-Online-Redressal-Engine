    package com.SIH.mark1.ivr.model;

import com.SIH.mark1.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One IVR phone call and everything gathered during it.
 *
 * <p>Provider webhooks are stateless HTTP requests, so this row is the conversation
 * memory: each webhook loads the session by {@code callSid}, acts on the current
 * {@link IvrCallState}, advances it, and saves. It also gives support staff a full
 * audit trail of what a caller said and what the system did with it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "ivr_sessions",
        indexes = {
                @Index(name = "idx_ivr_sessions_call_sid", columnList = "call_sid", unique = true),
                @Index(name = "idx_ivr_sessions_mobile", columnList = "mobile"),
                @Index(name = "idx_ivr_sessions_state", columnList = "state"),
                @Index(name = "idx_ivr_sessions_complaint", columnList = "complaint_id")
        }
)
public class IvrSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ivr_session_id")
    private Long ivrSessionId;

    /**
     * The provider's unique call identifier, and the correlation key for all webhooks.
     *
     * <p>Holds Sarvam's {@code attempt_id} for outbound verification calls. The column name
     * predates the move off Twilio and is kept so existing rows and their audit-log
     * references stay readable.</p>
     */
    @Column(name = "call_sid", nullable = false, unique = true, length = 64)
    private String callSid;

    /** Caller's phone number in E.164 form, normalised to the local 10 digits for lookups. */
    @Column(name = "mobile", length = 20)
    private String mobile;

    /** Raw caller number as the provider sent it, kept for troubleshooting. */
    @Column(name = "raw_from_number", length = 30)
    private String rawFromNumber;

    /** INBOUND for citizen-initiated calls, OUTBOUND for verification call-backs. */
    @Builder.Default
    @Column(name = "direction", length = 12)
    private String direction = "INBOUND";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 40)
    private IvrCallState state = IvrCallState.GREETING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 20)
    private IvrLanguage language = IvrLanguage.HINDI;

    /** Provider URL for the caller's recording, when one was captured. */
    @Column(name = "recording_url", length = 500)
    private String recordingUrl;

    @Column(name = "recording_duration_seconds")
    private Integer recordingDurationSeconds;

    /** English transcript produced by Sarvam speech-to-text-translate. */
    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    /** Sarvam's detected source language for the recording, when reported. */
    @Column(name = "detected_language", length = 20)
    private String detectedLanguage;

    /** Complaint created from (or being verified by) this call. */
    @Column(name = "complaint_id")
    private Long complaintId;

    /** Human-readable complaint number read back to the caller. */
    @Column(name = "complaint_no", length = 40)
    private String complaintNo;

    /** Invalid keypresses / re-record requests so far, used to bail out of loops. */
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /** Why the session ended in {@link IvrCallState#FAILED}. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Caller's spoken reason for rejecting a resolution (verification #2). */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Increments the retry counter and returns the new value. */
    public int incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        return this.retryCount;
    }

    /** Moves the session to a terminal state and stamps the completion time. */
    public void complete(IvrCallState terminalState) {
        this.state = terminalState;
        this.completedAt = LocalDateTime.now();
    }
}
