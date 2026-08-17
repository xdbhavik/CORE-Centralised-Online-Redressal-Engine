package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * AI vs MANUAL assignment summary for the admin dashboard.
 */
@Data
@Builder
public class AutoAssignmentStatsResponse {
    private long totalAssignments;
    private long aiAssignments;
    private long manualAssignments;
    /** AI assignments in the last 7 days */
    private long aiAssignmentsLast7Days;
    /** department name → AI assignment count */
    private Map<String, Long> aiAssignmentsByDepartment;
}
