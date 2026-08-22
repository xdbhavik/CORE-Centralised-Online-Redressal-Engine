package com.SIH.mark1.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record SummaryRequest(@NotBlank String text) {
}
