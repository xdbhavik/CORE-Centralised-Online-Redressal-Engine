package com.SIH.mark1.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Main AI module configuration properties.
 * Bound from the {@code ai.*} prefix in application.properties.
 * <p>
 * Collection names are managed separately in {@code RagProperties} (prefix: {@code rag.*}).
 */
@ConfigurationProperties(prefix = "ai")
public record AIProperties(
        boolean enabled,
        ChatModelConfig chat,
        EmbeddingConfig embedding,
        QdrantConfig qdrant,
        SarvamConfig sarvam
) {
    public String modelName() {
        return chat != null && chat.model() != null ? chat.model() : "local-keyword-fallback";
    }

    public record ChatModelConfig(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            int timeoutMillis
    ) {
    }

    public record EmbeddingConfig(
            String provider,
            String model,
            int dimensions
    ) {
    }

    /**
     * Qdrant connection config.
     * Collection list is managed in {@link com.SIH.mark1.ai.qdrant.RagProperties}.
     */
    public record QdrantConfig(
            String baseUrl,
            String apiKey
    ) {
    }

    /**
     * Sarvam AI configuration (Speech-to-Text + Text Translation + Text-to-Speech).
     *
     * <p>Note the two distinct STT settings: {@code sttModel} (saarika family) targets the
     * plain {@code /speech-to-text} endpoint used by the citizen app's voice input, while
     * {@code sttTranslateModel} (saaras family) targets {@code /speech-to-text-translate},
     * which auto-detects the spoken language and returns English in a single call. The IVR
     * uses the latter so complaints from any supported language normalise to English.</p>
     */
    public record SarvamConfig(
            String baseUrl,
            String apiKey,
            String sttModel,
            String sttTranslateModel,
            String languageCode,
            int maxAudioSizeMb,
            String translateModel,
            String ttsModel,
            String ttsSpeaker
    ) {
        /**
         * Translation model used for Sarvam /translate calls.
         * Defaults to mayura:v1 when not configured.
         */
        public String resolvedTranslateModel() {
            return translateModel == null || translateModel.isBlank() ? "mayura:v1" : translateModel;
        }

        /**
         * Speech-to-text-translate model (IVR). Defaults to saaras:v2.5 when not configured.
         */
        public String resolvedSttTranslateModel() {
            return sttTranslateModel == null || sttTranslateModel.isBlank() ? "saaras:v2.5" : sttTranslateModel;
        }

        /**
         * Text-to-speech model used to synthesise IVR prompts. Defaults to bulbul:v2.
         */
        public String resolvedTtsModel() {
            return ttsModel == null || ttsModel.isBlank() ? "bulbul:v2" : ttsModel;
        }

        /**
         * Default TTS speaker voice, used when a language does not specify its own.
         */
        public String resolvedTtsSpeaker() {
            return ttsSpeaker == null || ttsSpeaker.isBlank() ? "anushka" : ttsSpeaker;
        }
    }

}
