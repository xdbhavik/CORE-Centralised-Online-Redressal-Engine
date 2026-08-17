package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OfficerUpdateRequest {

    @NotBlank(message = "Message is required")
    private String message;      // Public update visible to citizen

    private String internalNote; // Internal note (officer only, not shown to citizen)
}
