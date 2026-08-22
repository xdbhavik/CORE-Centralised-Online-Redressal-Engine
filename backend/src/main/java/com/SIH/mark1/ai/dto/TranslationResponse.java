package com.SIH.mark1.ai.dto;

public record TranslationResponse(
        String sourceLanguage,
        String targetLanguage,
        String translatedText
) {
}
