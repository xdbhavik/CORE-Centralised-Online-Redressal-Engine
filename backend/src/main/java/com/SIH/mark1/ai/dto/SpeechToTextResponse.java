package com.SIH.mark1.ai.dto;

/**
 * Response returned by the Sarvam AI Speech-to-Text endpoint.
 *
 * @param transcript   the transcribed text from the audio
 * @param languageCode the language code used for transcription (e.g. hi-IN)
 * @param model        the Sarvam STT model used
 * @param durationSecs duration of the audio in seconds (if reported by the API)
 */
public record SpeechToTextResponse(
        String transcript,
        String languageCode,
        String model,
        Double durationSecs
) {
}