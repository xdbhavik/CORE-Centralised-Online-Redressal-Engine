package com.SIH.mark1.ai.service;

import com.SIH.mark1.ai.config.AIProperties;
import com.SIH.mark1.ai.dto.SpeechToTextResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Client for Sarvam AI Speech-to-Text (STT).
 *
 * <p>Converts spoken audio (Hindi/Hinglish and other Indian languages)
 * into transcribed text that can be used to auto-fill a complaint description.</p>
 *
 * <p>Endpoint: POST {baseUrl}/speech-to-text (multipart/form-data)</p>
 */
@Service
public class SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextService.class);

    private final AIProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SpeechToTextService(AIProperties properties, RestClient aiRestClient) {
        this.properties = properties;
        this.restClient = aiRestClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Transcribes the given audio file using Sarvam AI.
     *
     * @param file         the uploaded audio (MP3 / WAV / M4A / WebM / OGG)
     * @param languageCode optional BCP-47 language code (defaults to configured language, e.g. hi-IN)
     * @return transcribed text wrapped in a response DTO
     */
    public SpeechToTextResponse transcribe(MultipartFile file, String languageCode) {
        AIProperties.SarvamConfig cfg = properties.sarvam();
        if (cfg == null || cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Sarvam AI API key is not configured. Set ai.sarvam.api-key or SARVAM_API_KEY env var.");
        }

        String resolvedLanguage = (languageCode == null || languageCode.isBlank())
                ? cfg.languageCode()
                : languageCode;

        byte[] audioBytes;
        try {
            audioBytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read audio file: " + e.getMessage(), e);
        }

        if (audioBytes.length == 0) {
            throw new IllegalArgumentException("Uploaded audio file is empty.");
        }

        MultiValueMap<String, Object> formBody = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename() != null
                        ? file.getOriginalFilename()
                        : "audio.bin";
            }
        };
        formBody.add("file", resource);
        formBody.add("model", cfg.sttModel());
        formBody.add("language_code", resolvedLanguage);

        try {
            Map<?, ?> response = restClient.post()
                    .uri(cfg.baseUrl() + "/speech-to-text")
                    .header("api-subscription-key", cfg.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(formBody)
                    .retrieve()
                    .body(Map.class);

            String transcript = extractTranscript(response);
            if (transcript == null || transcript.isBlank()) {
                throw new IllegalStateException(
                        "Sarvam AI returned an empty transcript. Raw response: " + response);
            }
            return new SpeechToTextResponse(
                    transcript.trim(),
                    resolvedLanguage,
                    cfg.sttModel(),
                    extractDuration(response)
            );
        } catch (RestClientResponseException e) {
            log.error("Sarvam AI HTTP {} error: {}", e.getStatusCode(), e.getResponseBodyAsString());
            String errorMsg = extractErrorMessage(e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Sarvam AI API error: " + errorMsg, e);
        } catch (Exception e) {
            log.warn("Sarvam AI speech-to-text failed: {}", e.getMessage());
            if (e instanceof IllegalStateException) {
                throw e;
            }
            throw new IllegalStateException("Speech-to-text conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a human-readable error message from the Sarvam AI error response body.
     * Handles both {"error": {"message": "..."}} and {"message": "..."} formats.
     */
    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Empty response body";
        }
        try {
            Map<?, ?> errorResponse = objectMapper.readValue(responseBody, Map.class);
            Object errorObj = errorResponse.get("error");
            if (errorObj instanceof Map<?, ?> errorMap && errorMap.get("message") != null) {
                return errorMap.get("message").toString();
            }
            if (errorResponse.get("message") != null) {
                return errorResponse.get("message").toString();
            }
        } catch (Exception ignored) {
            // Fall back to raw response body
        }
        return responseBody;
    }

    private String extractTranscript(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object transcript = response.get("transcript");
        if (transcript != null) {
            return transcript.toString();
        }
        // Some responses nest the transcript under a "result" / "data" key.
        Object result = response.get("result");
        if (result instanceof Map<?, ?> resultMap && resultMap.get("transcript") != null) {
            return resultMap.get("transcript").toString();
        }
        return null;
    }

    private Double extractDuration(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object duration = response.get("duration");
        if (duration instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }
}