package com.SIH.mark1.ai.client;

import com.SIH.mark1.ai.config.AIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client for Sarvam AI Text Translation.
 *
 * <p>Translates citizen complaint text from Indian languages
 * (Hindi, Gujarati, Hinglish, etc.) into English so that downstream
 * analysis (Nemotron via NVIDIA NIM) and stored summaries are always English.</p>
 *
 * <p>Endpoint: POST {baseUrl}/translate (application/json)</p>
 *
 * <p>Returns {@link Optional#empty()} on any failure so callers can
 * gracefully fall back to another translation strategy.</p>
 */
@Component
public class SarvamTranslationClient {

    private static final Logger log = LoggerFactory.getLogger(SarvamTranslationClient.class);

    /** Sarvam /translate accepts at most ~2000 characters per request for mayura:v1. */
    private static final int MAX_INPUT_CHARS = 1900;

    private final AIProperties properties;
    private final RestClient restClient;

    public SarvamTranslationClient(AIProperties properties, RestClient aiRestClient) {
        this.properties = properties;
        this.restClient = aiRestClient;
    }

    /**
     * Translates the given text to English using Sarvam AI.
     *
     * @param text               the source text (any supported Indian language)
     * @param sourceLanguageCode BCP-47 code of the source language (e.g. hi-IN, gu-IN).
     *                           Pass null/blank to let Sarvam auto-detect ("auto").
     * @return the English translation, or empty if translation failed
     */
    public Optional<String> translateToEnglish(String text, String sourceLanguageCode) {
        AIProperties.SarvamConfig cfg = properties.sarvam();
        if (cfg == null || cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            return Optional.empty();
        }
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String input = text.trim();
        if (input.length() > MAX_INPUT_CHARS) {
            input = input.substring(0, MAX_INPUT_CHARS);
        }

        String source = (sourceLanguageCode == null || sourceLanguageCode.isBlank())
                ? "auto"
                : sourceLanguageCode;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);
        body.put("source_language_code", source);
        body.put("target_language_code", "en-IN");
        body.put("model", cfg.resolvedTranslateModel());
        body.put("mode", "formal");
        body.put("enable_preprocessing", true);

        try {
            Map<?, ?> response = restClient.post()
                    .uri(cfg.baseUrl() + "/translate")
                    .header("api-subscription-key", cfg.apiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String translated = extractTranslatedText(response);
            if (translated == null || translated.isBlank()) {
                log.warn("Sarvam AI translate returned empty text. Raw response: {}", response);
                return Optional.empty();
            }
            return Optional.of(translated.trim());
        } catch (Exception e) {
            log.warn("Sarvam AI translate failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractTranslatedText(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object translated = response.get("translated_text");
        if (translated != null) {
            return translated.toString();
        }
        // Defensive: some responses may nest under "result" / "data".
        Object result = response.get("result");
        if (result instanceof Map<?, ?> resultMap && resultMap.get("translated_text") != null) {
            return resultMap.get("translated_text").toString();
        }
        return null;
    }
}
