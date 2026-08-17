package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin review view of a possible duplicate complaint.
 */
@Data
@Builder
public class DuplicateReviewItem {

    private Long resultId;
    private Long complaintId;
    private String complaintNumber;
    private String title;
    private String description;
    private String department;
    private String category;
    private String status;
    private LocalDateTime createdAt;

    private Long matchedComplaintId;
    private String matchedComplaintNumber;
    private String matchedStatus;
    private Double similarity;
    private String decision;
    private String scope;
    private Boolean resourceMatch;
    private Boolean locationMatch;
    private List<String> reasons;
    private String reviewStatus;
}