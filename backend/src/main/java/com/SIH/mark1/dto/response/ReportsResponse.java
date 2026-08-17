package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReportsResponse {
    private LocalDateTime generatedAt;
    private long totalComplaints;
    private long resolvedComplaints;
    private double resolutionRate;
    private long totalUsers;
    private long totalOfficers;
    private List<ComplaintAdminResponse> recentComplaints;
}
