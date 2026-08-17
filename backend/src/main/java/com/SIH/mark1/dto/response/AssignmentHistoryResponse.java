package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AssignmentHistoryResponse {
    private Long id;
    private Long complaintId;
    private String oldOfficerName;
    private String newOfficerName;
    private String changedByName;
    private String reason;
    private LocalDateTime changedAt;
}
