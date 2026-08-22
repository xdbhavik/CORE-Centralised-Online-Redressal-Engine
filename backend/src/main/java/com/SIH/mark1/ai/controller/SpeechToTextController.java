package com.SIH.mark1.ai.controller;

import com.SIH.mark1.ai.dto.SpeechToTextResponse;
import com.SIH.mark1.ai.service.SpeechToTextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for Sarvam AI Speech-to-Text.
 *
 * <p>Exposes POST /api/v1/speech-to-text to convert spoken audio
 * into transcribed text for auto-filling complaint descriptions.</p>
 */
@RestController
@RequestMapping("/api/v1/speech-to-text")
public class SpeechToTextController {

    private final SpeechToTextService speechToTextService;

    public SpeechToTextController(SpeechToTextService speechToTextService) {
        this.speechToTextService = speechToTextService;
    }

    /**
     * Transcribes an uploaded audio file using Sarvam AI.
     *
     * @param file         the audio file (WAV / MP3 / M4A / OGG)
     * @param languageCode optional BCP-47 language code (e.g. hi-IN)
     * @return transcribed text
     */
    @PostMapping
    public ResponseEntity<SpeechToTextResponse> transcribe(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "languageCode", required = false) String languageCode) {
        SpeechToTextResponse response = speechToTextService.transcribe(file, languageCode);
        return ResponseEntity.ok(response);
    }
}