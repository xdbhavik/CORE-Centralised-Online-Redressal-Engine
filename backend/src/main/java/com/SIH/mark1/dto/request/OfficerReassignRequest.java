package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OfficerReassignRequest {

    @NotBlank(message = "Reason for reassignment is required")
    private String reason;
}
