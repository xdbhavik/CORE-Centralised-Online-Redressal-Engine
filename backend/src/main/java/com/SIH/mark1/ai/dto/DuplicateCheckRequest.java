package com.SIH.mark1.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record DuplicateCheckRequest(
        String title,
        @NotBlank String description,
        String address,
        Double latitude,
        Double longitude
) {
}
