package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AnalyticsResponse {
    private long totalComplaints;
    private long openComplaints;
    private long resolvedComplaints;
    private Map<String, Long> complaintsByStatus;
    private Map<String, Long> complaintsByDepartment;
    private Map<String, Long> complaintsByPriority;
}
