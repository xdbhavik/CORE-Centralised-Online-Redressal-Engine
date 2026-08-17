package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OfficerDashboardResponse {
    private long totalAssigned;
    private long pending;
    private long accepted;
    private long inProgress;
    private long resolved;
    private long highPriority;
    private long dueToday;
    private long overdue;

    // Backward-compatible fields used by the existing UI.
    private long assignedToday;
    private long urgent;
    private long completed;
}
