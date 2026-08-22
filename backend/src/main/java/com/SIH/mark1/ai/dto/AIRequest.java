package com.SIH.mark1.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AIRequest(
        @NotBlank String description,
        String language,
        Double latitude,
        Double longitude
) {
}
