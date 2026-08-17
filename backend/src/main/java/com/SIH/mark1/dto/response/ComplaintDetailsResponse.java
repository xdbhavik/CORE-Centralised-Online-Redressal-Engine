package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full detail response for GET /api/v1/complaints/{id}.
 * Includes location + media so the citizen app can render
 * the map and attachment gallery.
 */
@Data
@Builder
public class ComplaintDetailsResponse {

    private String        complaintNumber;
    private String        title;
    private String        description;
    private String        department;
    private String        category;
    private String        priority;
    private String        status;
    private String        officer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Human-readable address captured at filing time. */
    private String        address;
    private BigDecimal    latitude;
    private BigDecimal    longitude;

    /** Web URLs of uploaded media, served under /media/{complaintId}/{filename}. */
    private List<String>  mediaUrls;

    // ── SLA tracking ──
    /** Official SLA deadline (creation time + priority-based window). */
    private LocalDateTime slaDueAt;
    /** ON_TRACK | NEAR_BREACH | BREACHED | RESOLVED_WITHIN_SLA | RESOLVED_AFTER_SLA. */
    private String        slaStatus;
    /** Timestamp of the first SLA breach; null when never breached. */
    private LocalDateTime slaBreachedAt;

    // ── Citizen call-back verification ──
    /** PENDING | VERIFIED | REJECTED | FAILED. */
    private String        verificationStatus;
    /** True once the department may act on this complaint. */
    private boolean       actionAllowed;
    /**
     * Why action is on hold, for display; null when action is allowed.
     *
     * <p>Without this the gate can block work with no way for the app to explain the delay,
     * leaving the citizen staring at a complaint that appears to be going nowhere.</p>
     */
    private String        verificationBlockedReason;
}


