package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {
    private long totalUsers;
    private long totalOfficers;
    private long totalDepartments;
    private long totalCategories;
    private long totalComplaints;
    private long openComplaints;
    private long resolvedComplaints;
    private long pendingAiReviewedComplaints;
}

