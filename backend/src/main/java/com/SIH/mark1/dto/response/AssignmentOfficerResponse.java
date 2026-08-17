package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssignmentOfficerResponse {
    private Long officerId;
    private String name;
    private String mobile;
    private String email;
    private Long departmentId;
    private String departmentName;
    private Long wardId;
    private long pendingComplaints;
    private long resolvedComplaints;
    private Double averageResolutionDays;
    private String availability;
    private boolean recommended;
    private String recommendationReason;
}
