package com.SIH.mark1.ivr.service;

import com.SIH.mark1.ivr.config.IvrProperties;
import com.SIH.mark1.ivr.config.SarvamVoiceProperties;
import com.SIH.mark1.ivr.model.IvrLanguage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Places outbound citizen-verification calls through Sarvam AI Voice Agents.
 *
 * <p>Replaces the previous Twilio integration. The difference is not just the vendor: Twilio
 * gave us <em>turn-by-turn</em> control (we returned TwiML for each prompt and read DTMF
 * digits mid-call), whereas Sarvam runs a conversational agent authored in its dashboard and
 * reports back <em>once</em>, after the call, with the answer extracted into an agent
 * variable. So this class only starts the call; the outcome arrives at the webhook handled by
 * {@code IvrVerificationController}.</p>
 *
 * <p>Every Sarvam-specific detail — endpoint shape, org/workspace path scoping, the nested
 * {@code app_config}/{@code user_config} payload — is isolated here so the verification state
 * machine stays testable without a live telephony account. Failures are returned as a
 * {@link Result} rather than thrown, because an unreachable phone is an expected outcome of
 * this flow, not an exceptional one.</p>
 *
 * @see <a href="https://docs.sarvam.ai/conversations/api/instant-outbound/create">Create outbound call</a>
 */
@Service
public class SarvamVoiceCallService {

    private static final Logger log = LoggerFactory.getLogger(SarvamVoiceCallService.class);

    /** Sarvam authenticates with a plain API key header (not a bearer token). */
    private static final String API_KEY_HEADER = "X-API-Key";

    /** Agent variable carrying the complaint number so the agent can read it aloud. */
    private static final String VAR_COMPLAINT_NO = "complaint_no";

    /** Agent variable carrying the citizen's name, for a personalised greeting. */
    private static final String VAR_CITIZEN_NAME = "citizen_name";

    /** Webhook metadata key correlating the post-call callback with our complaint row. */
    public static final String META_COMPLAINT_ID = "complaint_id";

    /** Webhook metadata key holding the per-call HMAC that authenticates the callback. */
    public static final String META_TOKEN = "token";

    private final SarvamVoiceProperties voiceProperties;
    private final IvrProperties ivrProperties;
    private final PhoneNumberFormatter phoneNumberFormatter;
    private final SarvamWebhookAuthenticator webhookAuthenticator;
    private final RestClient restClient;

    public SarvamVoiceCallService(SarvamVoiceProperties voiceProperties,
                                  IvrProperties ivrProperties,
                                  PhoneNumberFormatter phoneNumberFormatter,
                                  SarvamWebhookAuthenticator webhookAuthenticator,
                                  RestClient aiRestClient) {
        this.voiceProperties = voiceProperties;
        this.ivrProperties = ivrProperties;
        this.phoneNumberFormatter = phoneNumberFormatter;
        this.webhookAuthenticator = webhookAuthenticator;
        this.restClient = aiRestClient;
    }

    /**
     * Reports configuration state at startup.
     *
     * <p>Deliberately does not fail startup on missing credentials: the IVR ships dark and the
     * rest of the grievance system must boot and serve app traffic without a telephony
     * account configured.</p>
     */
    @PostConstruct
    void logConfigurationState() {
        if (!voiceProperties.enabled()) {
            log.info("Sarvam voice calls disabled (sarvam.voice.enabled=false)");
            return;
        }
        if (!voiceProperties.isConfigured()) {
            log.warn("sarvam.voice.enabled=true but configuration is incomplete - outbound "
                    + "verification calls disabled. Missing: {}", voiceProperties.missingSettings());
            return;
        }
        log.info("Sarvam Voice Agents ready for outbound verification calls (app={} v{})",
                voiceProperties.appId(), voiceProperties.resolvedAppVersion());
    }

    /** True when a real call can actually be placed right now. */
    public boolean isAvailable() {
        return ivrProperties.enabled() && voiceProperties.enabled() && voiceProperties.isConfigured();
    }

    /**
     * Rings the citizen with the verification agent and registers the post-call webhook.
     *
     * @param mobile       citizen's mobile number (10-digit local or E.164)
     * @param callbackPath webhook path Sarvam posts the call outcome to
     * @param complaintId  correlation id, echoed back to us in the webhook metadata
     * @param complaintNo  human-readable complaint number the agent reads out
     * @param citizenName  citizen's name for the greeting; may be null
     * @param language     language to open the conversation in
     * @return outcome of the dial attempt; never throws
     */
    public Result placeVerificationCall(String mobile,
                                       String callbackPath,
                                       Long complaintId,
                                       String complaintNo,
                                       String citizenName,
                                       IvrLanguage language) {
        if (!ivrProperties.enabled()) {
            return Result.skipped("IVR disabled (ivr.enabled=false)");
        }
        if (!voiceProperties.enabled()) {
            return Result.skipped("Sarvam voice disabled (sarvam.voice.enabled=false)");
        }
        if (!voiceProperties.isConfigured()) {
            return Result.skipped("Sarvam voice not configured (missing: "
                    + voiceProperties.missingSettings() + ")");
        }

        Optional<String> e164 = phoneNumberFormatter.toE164(mobile);
        if (e164.isEmpty()) {
            // Not retryable: redialling a malformed number will never succeed.
            return Result.invalidNumber("Unusable mobile number: " + phoneNumberFormatter.mask(mobile));
        }

        try {
            Map<?, ?> response = restClient.post()
                    .uri(voiceProperties.outboundCallUrl())
                    .header(API_KEY_HEADER, voiceProperties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPayload(e164.get(), callbackPath, complaintId, complaintNo, citizenName, language))
                    .retrieve()
                    .body(Map.class);

            String attemptId = response == null ? null : asString(response.get("attempt_id"));
            if (attemptId == null || attemptId.isBlank()) {
                // A 2xx without an attempt id leaves us no way to correlate the webhook, so
                // treat it as a failure rather than recording a call we cannot track.
                return Result.failed("Sarvam accepted the request but returned no attempt_id");
            }

            log.info("Verification call placed: complaintId={} attemptId={} to={}",
                    complaintId, attemptId, phoneNumberFormatter.mask(e164.get()));
            return Result.placed(attemptId);

        } catch (RestClientResponseException e) {
            // 4xx means Sarvam rejected the request itself (bad number, bad ids, quota); of
            // those only 429 is worth redialling, since the rest will fail identically.
            int status = e.getStatusCode().value();
            boolean permanent = status >= 400 && status < 500 && status != 429;
            String detail = "Sarvam HTTP " + status + ": " + truncate(e.getResponseBodyAsString());
            log.warn("Sarvam rejected verification call for complaintId={}: {}", complaintId, detail);
            return permanent ? Result.invalidNumber(detail) : Result.failed(detail);
        } catch (Exception e) {
            log.error("Verification call failed for complaintId={}: {}", complaintId, e.getMessage());
            return Result.failed("Call failed: " + e.getMessage());
        }
    }

    /** True when a Sarvam call status means the citizen never picked up. */
    public boolean isNoAnswer(String callStatus) {
        return CallStatus.from(callStatus).isNoAnswer();
    }

    // ── payload ───────────────────────────────────────────────────────────────

    /**
     * Builds the nested instant-outbound request body.
     *
     * <p>{@code agent_variables} personalise what the agent says, while
     * {@code webhook_config.metadata} is echoed back verbatim in the callback — that echo is
     * how we tie an asynchronous webhook to a complaint without trusting a URL parameter.</p>
     */
    private Map<String, Object> buildPayload(String toNumber,
                                             String callbackPath,
                                             Long complaintId,
                                             String complaintNo,
                                             String citizenName,
                                             IvrLanguage language) {
        Map<String, Object> connectionConfig = new LinkedHashMap<>();
        connectionConfig.put("connection_id", voiceProperties.connectionId());
        connectionConfig.put("agent_phone_number", voiceProperties.agentPhoneNumber());

        Map<String, Object> agentVariables = new LinkedHashMap<>();
        if (complaintNo != null && !complaintNo.isBlank()) {
            agentVariables.put(VAR_COMPLAINT_NO, complaintNo);
        }
        if (citizenName != null && !citizenName.isBlank()) {
            agentVariables.put(VAR_CITIZEN_NAME, citizenName);
        }

        Map<String, Object> appConfig = new LinkedHashMap<>();
        appConfig.put("app_id", voiceProperties.appId());
        appConfig.put("app_version", voiceProperties.resolvedAppVersion());
        appConfig.put("connection_config", connectionConfig);
        if (!agentVariables.isEmpty()) {
            appConfig.put("agent_variables", agentVariables);
        }
        appConfig.put("app_overrides", Map.of(
                "initial_language_name", sarvamLanguageName(language)));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_COMPLAINT_ID, complaintId);
        // Per-call HMAC: the webhook endpoint must be public (Sarvam cannot present a JWT)
        // and Sarvam does not sign its callbacks, so this token is what stops anyone from
        // POSTing a forged "confirmed" and verifying a spam complaint.
        webhookAuthenticator.issueToken(complaintId)
                .ifPresent(token -> metadata.put(META_TOKEN, token));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app_config", appConfig);
        payload.put("user_config", Map.of("user_phone_number", toNumber));
        payload.put("webhook_config", Map.of(
                "url", ivrProperties.webhookUrl(callbackPath),
                "metadata", metadata));
        return payload;
    }

    /**
     * Maps our IVR language to the name Sarvam's {@code initial_language_name} enum expects.
     *
     * <p>Sarvam takes an English language <em>name</em> ({@code Hindi}), not the BCP-47 code
     * ({@code hi-IN}) used by its TTS/STT models, and rejects the latter.</p>
     */
    private String sarvamLanguageName(IvrLanguage language) {
        IvrLanguage resolved = language == null ? IvrLanguage.HINDI : language;
        return switch (resolved) {
            case ENGLISH -> "English";
            case GUJARATI -> "Gujarati";
            case HINDI -> "Hindi";
        };
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** Keeps a verbose provider error from bloating the complaint's remarks column. */
    private String truncate(String body) {
        if (body == null || body.isBlank()) {
            return "(no response body)";
        }
        String trimmed = body.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 197) + "...";
    }

    /**
     * Call outcomes reported by Sarvam's post-call webhook.
     *
     * <p>Modelled as an enum rather than compared as strings so an unrecognised value from a
     * future API version degrades to {@link #UNKNOWN} — treated as a no-answer, which retries
     * — instead of silently leaving the complaint stuck in PENDING.</p>
     */
    public enum CallStatus {

        /** Call was answered and a conversation took place. */
        CONNECTED,
        /** The recipient did not pick up. */
        NO_ANSWER,
        /** The recipient's line was busy. */
        BUSY,
        /** Call could not be placed (provider error, invalid number, and so on). */
        FAILED,
        /** Status Sarvam sent that this version does not recognise. */
        UNKNOWN;

        public static CallStatus from(String raw) {
            if (raw == null) {
                return UNKNOWN;
            }
            return switch (raw.trim().toLowerCase()) {
                case "connected" -> CONNECTED;
                case "no_answer", "no-answer" -> NO_ANSWER;
                case "busy" -> BUSY;
                case "failed" -> FAILED;
                default -> UNKNOWN;
            };
        }

        /** True when the call ended without the citizen answering. */
        public boolean isNoAnswer() {
            return this != CONNECTED;
        }
    }

    /**
     * Outcome of a dial attempt.
     *
     * @param outcome   what happened
     * @param attemptId Sarvam's attempt id when {@link Outcome#PLACED}
     * @param detail    human-readable reason, stored on the complaint for support staff
     */
    public record Result(Outcome outcome, String attemptId, String detail) {

        public enum Outcome {
            /** Sarvam accepted the call and is dialling. */
            PLACED,
            /** IVR disabled or unconfigured — do not count this as a failed attempt. */
            SKIPPED,
            /** Transient failure; retrying may succeed. */
            FAILED,
            /** The number or request itself is unusable; retrying is pointless. */
            INVALID_NUMBER
        }

        static Result placed(String attemptId) {
            return new Result(Outcome.PLACED, attemptId, null);
        }

        static Result skipped(String detail) {
            return new Result(Outcome.SKIPPED, null, detail);
        }

        static Result failed(String detail) {
            return new Result(Outcome.FAILED, null, detail);
        }

        static Result invalidNumber(String detail) {
            return new Result(Outcome.INVALID_NUMBER, null, detail);
        }

        public boolean isPlaced() {
            return outcome == Outcome.PLACED;
        }

        public boolean isSkipped() {
            return outcome == Outcome.SKIPPED;
        }

        /** True when retrying this number can never work. */
        public boolean isPermanentFailure() {
            return outcome == Outcome.INVALID_NUMBER;
        }
    }
}
