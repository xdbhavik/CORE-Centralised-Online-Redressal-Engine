package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lightweight response returned after complaint creation
 * and used in the citizen's complaint list view.
 */
@Data
@Builder
public class ComplaintResponse {

    private Long   complaintId;
    private String complaintNumber;
    private String status;
    private String message;

    /** List-view enrichment — populated by GET /complaints/my. */
    private String        title;
    private String        category;
    private LocalDateTime createdAt;

    /** Duplicate detection outcome — present when duplicate check ran. */
    private String duplicateDecision;
    private Double similarity;
    private List<String> reasons;
    private String existingComplaintNumber;
    private String existingStatus;
    private String scope;
    private Boolean resourceMatch;
    private Boolean locationMatch;
}
