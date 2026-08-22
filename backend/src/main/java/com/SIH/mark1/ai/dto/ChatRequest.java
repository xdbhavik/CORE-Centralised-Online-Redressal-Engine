package com.SIH.mark1.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message) {
}
