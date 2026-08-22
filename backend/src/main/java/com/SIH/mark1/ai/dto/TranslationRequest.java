package com.SIH.mark1.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record TranslationRequest(
        @NotBlank String text,
        String sourceLanguage,
        String targetLanguage
) {
}
