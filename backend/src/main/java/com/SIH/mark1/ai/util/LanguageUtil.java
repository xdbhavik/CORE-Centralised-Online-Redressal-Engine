package com.SIH.mark1.ai.util;

import org.springframework.stereotype.Component;

/**
 * Lightweight script-based language detector.
 *
 * <p>Detects the language of complaint text by inspecting Unicode script blocks.
 * Covers the 10 Indic scripts supported by Sarvam AI (translation + STT) plus English.
 * Returned codes are internal ISO-style codes that
 * {@code TranslationService#toSarvamLanguageCode} maps to Sarvam BCP-47 codes.</p>
 */
@Component
public class LanguageUtil {

    public String detect(String text) {
        if (text == null || text.isBlank()) {
            return "UNKNOWN";
        }
        long devanagari = countInRange(text, 0x0900, 0x097F);
        long bengali    = countInRange(text, 0x0980, 0x09FF);
        long gurmukhi   = countInRange(text, 0x0A00, 0x0A7F);
        long gujarati   = countInRange(text, 0x0A80, 0x0AFF);
        long odia       = countInRange(text, 0x0B00, 0x0B7F);
        long tamil      = countInRange(text, 0x0B80, 0x0BFF);
        long telugu     = countInRange(text, 0x0C00, 0x0C7F);
        long kannada    = countInRange(text, 0x0C80, 0x0CFF);
        long malayalam  = countInRange(text, 0x0D00, 0x0D7F);

        long max = Math.max(devanagari, Math.max(Math.max(bengali, gurmukhi),
                Math.max(Math.max(gujarati, odia),
                        Math.max(Math.max(tamil, telugu), Math.max(kannada, malayalam)))));

        if (max == 0) {
            return "en";
        }
        if (max == devanagari) return "hi";
        if (max == bengali) return "bn";
        if (max == gurmukhi) return "pa";
        if (max == gujarati) return "gu";
        if (max == odia) return "od";
        if (max == tamil) return "ta";
        if (max == telugu) return "te";
        if (max == kannada) return "kn";
        return "ml";
    }

    private long countInRange(String text, int start, int end) {
        return text.chars().filter(ch -> ch >= start && ch <= end).count();
    }
}
