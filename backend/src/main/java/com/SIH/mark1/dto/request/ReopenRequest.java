package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReopenRequest {

    @NotBlank(message = "Reason for reopening is required")
    private String reason;
}
