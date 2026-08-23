package com.SIH.mark1.ivr.service;

import com.SIH.mark1.ai.config.AIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Transcribes IVR call recordings using Sarvam's speech-to-text-translate endpoint.
 *
 * <p>Endpoint: {@code POST {baseUrl}/speech-to-text-translate} (multipart/form-data).
 * Unlike the plain {@code /speech-to-text} endpoint used by the citizen app (which needs
 * an explicit language code and returns text in the spoken language), this endpoint
 * auto-detects the spoken language and returns <strong>English</strong>. That collapses
 * the transcribe-then-translate two-step into one call and means complaints spoken in
 * Hindi, Gujarati or English all normalise to English before AI analysis.</p>
 *
 * @see <a href="https://docs.sarvam.ai/api/api-guides-tutorials/speech-to-text/overview">Sarvam STT</a>
 */
@Service
public class SarvamSpeechTranslateService {

    private static final Logger log = LoggerFactory.getLogger(SarvamSpeechTranslateService.class);

    private final AIProperties properties;
    private final RestClient restClient;

    public SarvamSpeechTranslateService(AIProperties properties, RestClient aiRestClient) {
        this.properties = properties;
        this.restClient = aiRestClient;
    }

    /**
     * Result of a transcription attempt.
     *
     * @param transcript       normalised English text (never blank when {@code success})
     * @param detectedLanguage Sarvam's detected source language code, when reported
     * @param success          whether a usable transcript was produced
     * @param errorMessage     failure detail for logging / call-flow decisions
     */
    public record TranscriptionResult(
            String transcript,
            String detectedLanguage,
            boolean success,
            String errorMessage
    ) {
        public static TranscriptionResult ok(String transcript, String detectedLanguage) {
            return new TranscriptionResult(transcript, detectedLanguage, true, null);
        }

        public static TranscriptionResult failure(String errorMessage) {
            return new TranscriptionResult(null, null, false, errorMessage);
        }
    }

    /**
     * Transcribes raw audio bytes into English text.
     *
     * @param audioBytes the call recording downloaded from the telephony provider (WAV)
     * @param fileName   original file name, used for the multipart part
     * @return the transcription result; never throws, so a Sarvam outage degrades the
     *         call gracefully instead of dropping it
     */
    public TranscriptionResult transcribe(byte[] audioBytes, String fileName) {
        if (audioBytes == null || audioBytes.length == 0) {
            return TranscriptionResult.failure("Recording was empty");
        }

        AIProperties.SarvamConfig cfg = properties.sarvam();
        if (cfg == null || cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            return TranscriptionResult.failure("Sarvam API key is not configured");
        }

        int maxBytes = Math.max(1, cfg.maxAudioSizeMb()) * 1024 * 1024;
        if (audioBytes.length > maxBytes) {
            return TranscriptionResult.failure(
                    "Recording exceeds the " + cfg.maxAudioSizeMb() + "MB Sarvam limit");
        }

        String resolvedName = (fileName == null || fileName.isBlank()) ? "ivr-recording.wav" : fileName;

        MultiValueMap<String, Object> formBody = new LinkedMultiValueMap<>();
        formBody.add("file", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return resolvedName;
            }
        });
        formBody.add("model", cfg.resolvedSttTranslateModel());

        try {
            Map<?, ?> response = restClient.post()
                    .uri(cfg.baseUrl() + "/speech-to-text-translate")
                    .header("api-subscription-key", cfg.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(formBody)
                    .retrieve()
                    .body(Map.class);

            String transcript = extractTranscript(response);
            if (transcript == null || transcript.isBlank()) {
                log.warn("Sarvam speech-to-text-translate returned an empty transcript: {}", response);
                return TranscriptionResult.failure("Empty transcript returned");
            }
            return TranscriptionResult.ok(transcript.trim(), extractDetectedLanguage(response));
        } catch (RestClientResponseException e) {
            log.error("Sarvam speech-to-text-translate HTTP {}: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return TranscriptionResult.failure("Sarvam API error: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Sarvam speech-to-text-translate failed: {}", e.getMessage());
            return TranscriptionResult.failure("Transcription failed: " + e.getMessage());
        }
    }

    private String extractTranscript(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object transcript = response.get("transcript");
        if (transcript != null) {
            return transcript.toString();
        }
        Object result = response.get("result");
        if (result instanceof Map<?, ?> resultMap && resultMap.get("transcript") != null) {
            return resultMap.get("transcript").toString();
        }
        return null;
    }

    private String extractDetectedLanguage(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object detected = response.get("language_code");
        if (detected != null) {
            return detected.toString();
        }
        Object alt = response.get("detected_language_code");
        return alt == null ? null : alt.toString();
    }
}
