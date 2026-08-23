package com.SIH.mark1.ivr.service;

import com.SIH.mark1.ai.config.AIProperties;
import com.SIH.mark1.ivr.config.IvrProperties;
import com.SIH.mark1.ivr.model.IvrLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Synthesises IVR prompts with Sarvam AI Text-to-Speech (bulbul).
 *
 * <p>Endpoint: {@code POST {baseUrl}/text-to-speech} (application/json), which returns
 * base64-encoded WAV audio in the {@code audios} array.</p>
 *
 * <p>Audio is written under {@code {app.upload.dir}/ivr/} and served by the existing
 * {@code /media/**} resource handler, so a telephony provider can fetch it by URL without
 * any new endpoint or security rule. The filename is a SHA-256 hash of
 * (text + language + speaker + model), which makes the whole thing a persistent cache:
 * the fixed menu prompts are synthesised once, not once per call.</p>
 *
 * <p><b>Not used by the outbound verification flow.</b> Sarvam AI Voice Agents speak their
 * own script, so nothing here is on that path. It is retained for the inbound
 * complaint-lodging IVR (a caller dials in and records a grievance), which needs prompts
 * rendered to audio files and is not yet wired to a provider.</p>
 *
 * @see <a href="https://docs.sarvam.ai/api/api-guides-tutorials/text-to-speech/overview">Sarvam TTS</a>
 */
@Service
public class SarvamTtsService {

    private static final Logger log = LoggerFactory.getLogger(SarvamTtsService.class);

    /** Sub-directory of the upload dir holding generated IVR audio. */
    private static final String IVR_AUDIO_SUBDIR = "ivr";

    /** Sarvam bulbul rejects very long inputs; IVR prompts should stay well under this. */
    private static final int MAX_TTS_CHARS = 1500;

    private final AIProperties properties;
    private final IvrProperties ivrProperties;
    private final RestClient restClient;
    private final String uploadDir;

    public SarvamTtsService(AIProperties properties,
                           IvrProperties ivrProperties,
                           RestClient aiRestClient,
                           @Value("${app.upload.dir}") String uploadDir) {
        this.properties = properties;
        this.ivrProperties = ivrProperties;
        this.restClient = aiRestClient;
        this.uploadDir = uploadDir;
    }

    /**
     * Converts text to speech and returns a publicly fetchable URL for the audio.
     *
     * @param text     the prompt to speak
     * @param language the IVR language (drives both the Sarvam language code and speaker voice)
     * @return absolute public URL of the generated audio, or {@code null} when synthesis
     *         failed, so the caller can fall back to the provider's own built-in voice
     */
    public String synthesizeToPublicUrl(String text, IvrLanguage language) {
        String fileName = synthesizeToFile(text, language);
        if (fileName == null) {
            return null;
        }
        return ivrProperties.resolvedBasePublicUrl() + "/media/" + IVR_AUDIO_SUBDIR + "/" + fileName;
    }

    /**
     * Synthesises the prompt and returns the cached file name, reusing an existing file
     * when the same text/language/speaker/model has been rendered before.
     *
     * @return the generated file name, or {@code null} when synthesis failed
     */
    public String synthesizeToFile(String text, IvrLanguage language) {
        if (text == null || text.isBlank()) {
            return null;
        }
        AIProperties.SarvamConfig cfg = properties.sarvam();
        if (cfg == null || cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            log.warn("Sarvam API key not configured - IVR cannot pre-render prompt audio");
            return null;
        }

        IvrLanguage resolvedLanguage = language == null ? IvrLanguage.HINDI : language;
        String speaker = resolvedLanguage.speaker() == null || resolvedLanguage.speaker().isBlank()
                ? cfg.resolvedTtsSpeaker()
                : resolvedLanguage.speaker();
        String model = cfg.resolvedTtsModel();
        String prompt = text.length() > MAX_TTS_CHARS ? text.substring(0, MAX_TTS_CHARS) : text;

        String fileName = cacheKey(prompt, resolvedLanguage.sarvamCode(), speaker, model) + ".wav";
        Path audioDir = Paths.get(uploadDir, IVR_AUDIO_SUBDIR);
        Path target = audioDir.resolve(fileName);

        if (Files.exists(target)) {
            log.debug("Reusing cached IVR audio {}", fileName);
            return fileName;
        }

        try {
            Map<?, ?> response = restClient.post()
                    .uri(cfg.baseUrl() + "/text-to-speech")
                    .header("api-subscription-key", cfg.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "text", prompt,
                            "target_language_code", resolvedLanguage.sarvamCode(),
                            "speaker", speaker,
                            "model", model
                    ))
                    .retrieve()
                    .body(Map.class);

            byte[] audio = extractAudio(response);
            if (audio == null || audio.length == 0) {
                log.warn("Sarvam TTS returned no audio for language {}", resolvedLanguage);
                return null;
            }

            Files.createDirectories(audioDir);
            Files.write(target, audio);
            log.info("Generated IVR audio {} ({} bytes, {})", fileName, audio.length, resolvedLanguage);
            return fileName;
        } catch (RestClientResponseException e) {
            log.error("Sarvam TTS HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (IOException e) {
            log.error("Failed to write IVR audio file {}: {}", fileName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Sarvam TTS failed: {}", e.getMessage());
            return null;
        }
    }

    /** Sarvam returns {@code {"audios": ["<base64 wav>"]}}. */
    private byte[] extractAudio(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object audios = response.get("audios");
        if (audios instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return Base64.getDecoder().decode(list.get(0).toString());
        }
        // Defensive: some responses use a singular key.
        Object audio = response.get("audio");
        if (audio != null) {
            return Base64.getDecoder().decode(audio.toString());
        }
        return null;
    }

    /** Stable hash so identical prompts map to one cached file. */
    private String cacheKey(String text, String languageCode, String speaker, String model) {
        String raw = model + "|" + languageCode + "|" + speaker + "|" + text;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            String hex = new BigInteger(1, hash).toString(16);
            return hex.length() > 32 ? hex.substring(0, 32) : hex;
        } catch (Exception e) {
            // Hashing cannot realistically fail; fall back to a non-cached name.
            return "tts-" + Math.abs(raw.hashCode());
        }
    }
}
