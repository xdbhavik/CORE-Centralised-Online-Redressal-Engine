package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OfficerResolutionRequest {

    @NotBlank(message = "Resolution summary is required")
    private String resolutionSummary;

    private String remarks;
}
