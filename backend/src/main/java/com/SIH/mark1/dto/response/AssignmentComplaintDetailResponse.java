package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AssignmentComplaintDetailResponse {
    private Long complaintId;
    private String complaintNo;

    private String citizenName;
    private String citizenMobile;
    private String citizenAddress;
    private String ward;
    private LocalDateTime complaintDate;
    private String currentStatus;

    private String title;
    private String description;
    private String locationAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private List<String> mediaUrls;

    private String aiSummary;
    private String aiCategory;
    private String aiPriority;
    private String urgency;
    private Long suggestedDepartmentId;
    private String suggestedDepartment;
    private Integer confidence;
    private List<String> keywords;
    private String suggestedAction;

    // ── Citizen call-back verification ──
    /** PENDING | VERIFIED | REJECTED | FAILED. */
    private String verificationStatus;
    /** True once the department may act on this complaint. */
    private boolean actionAllowed;
    /** True when the citizen denied lodging it; needs admin review. */
    private boolean verificationFlagged;
    /**
     * Why action is on hold, for display; null when action is allowed.
     *
     * <p>Officers can open a blocked complaint but not act on it, so this detail view is
     * where they find out why their accept/resolve attempt will be refused. Without it the
     * hold looks like a bug rather than a deliberate wait on the citizen.</p>
     */
    private String verificationBlockedReason;
}

