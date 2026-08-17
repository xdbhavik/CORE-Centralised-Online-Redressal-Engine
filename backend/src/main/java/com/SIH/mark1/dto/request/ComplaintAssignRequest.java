package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ComplaintAssignRequest {
    @NotNull
    private Long complaintId;

    @NotNull
    private Long officerId;

    private Long departmentId;
    private Long priorityId;
    private LocalDate dueDate;
    private String internalNote;
    private String reason;

    /** Backward compatible field for older clients. */
    private String remarks;
}
