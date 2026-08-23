package com.SIH.mark1.ivr.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Append-only audit trail of every IVR webhook event and call-lifecycle change.
 *
 * <p>Deliberately not an {@code AuditableEntity}: these rows are immutable facts, never
 * updated or soft-deleted. When a citizen disputes "I never made that complaint", this
 * table is the evidence of exactly which digits were pressed and when.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "ivr_call_logs",
        indexes = {
                @Index(name = "idx_ivr_logs_call_sid", columnList = "call_sid"),
                @Index(name = "idx_ivr_logs_event", columnList = "event_type")
        }
)
public class IvrCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ivr_call_log_id")
    private Long ivrCallLogId;

    @Column(name = "call_sid", length = 64)
    private String callSid;

    /** Which webhook or internal step produced this entry (INBOUND, MENU, RECORDING, ...). */
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    /** The provider's own call status at the time (completed, busy, no-answer, failed, ...). */
    @Column(name = "call_status", length = 40)
    private String callStatus;

    /** DTMF digits received with this event, if any. */
    @Column(name = "digits", length = 20)
    private String digits;

    /** Session state before handling the event. */
    @Column(name = "state_before", length = 40)
    private String stateBefore;

    /** Session state after handling the event. */
    @Column(name = "state_after", length = 40)
    private String stateAfter;

    /** Serialised webhook parameters, with any signature header stripped. */
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    /** Error detail when the event could not be processed. */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
