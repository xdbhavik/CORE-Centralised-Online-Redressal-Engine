package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssignmentRecommendationResponse {
    private Long officerId;
    private String officerName;
    private String reason;
    private long pendingCases;
    private Double averageResolutionDays;
}
