package com.SIH.mark1.ivr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The payload Sarvam POSTs once an outbound verification call attempt finishes.
 *
 * <p>This single callback replaces Twilio's four separate webhooks: because a Sarvam agent
 * conducts the whole conversation itself, the citizen's answer arrives here — already extracted
 * into {@link #finalAgentVariables()} — instead of as a DTMF digit mid-call.</p>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is essential rather than lax: Sarvam
 * adds fields to this payload over time, and a strict binding would turn a harmless additive
 * change into a 400 that silently strands every complaint in PENDING.</p>
 *
 * @param attemptId             matches the id returned when the call was placed
 * @param status                {@code connected}, {@code no_answer}, {@code busy} or {@code failed}
 * @param duration              call length in seconds; null when it never connected
 * @param interactionId         handle for fetching recordings/transcripts later
 * @param failureReason         provider-prefixed error text, e.g. TRAI NDNC rejection
 * @param finalAgentVariables   agent variables at call end — where the answer lives
 * @param webhookConfig         our own webhook config echoed back, carrying the metadata
 * @param interactionTranscript turn-by-turn transcript; null when it never connected
 * @see <a href="https://docs.sarvam.ai/conversations/api/instant-outbound/webhook-payload">Webhook payload</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SarvamCallWebhookPayload(

        @JsonProperty("attempt_id") String attemptId,
        @JsonProperty("status") String status,
        @JsonProperty("duration") Double duration,
        @JsonProperty("interaction_id") String interactionId,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("channel_info") ChannelInfo channelInfo,
        @JsonProperty("final_agent_variables") Map<String, Object> finalAgentVariables,
        @JsonProperty("webhook_config") WebhookConfig webhookConfig,
        @JsonProperty("interaction_transcript") List<TranscriptTurn> interactionTranscript
) {

    /**
     * Complaint id we attached when placing the call.
     *
     * <p>Read from the echoed metadata rather than a URL parameter because the metadata was
     * authored by us and travels with the HMAC token that authenticates it.</p>
     */
    public Optional<Long> complaintId(String metadataKey) {
        return metadataValue(metadataKey).map(SarvamCallWebhookPayload::parseLong).filter(id -> id != null);
    }

    /** Reads a single value out of the echoed webhook metadata. */
    public Optional<String> metadataValue(String key) {
        if (webhookConfig == null || webhookConfig.metadata() == null) {
            return Optional.empty();
        }
        Object value = webhookConfig.metadata().get(key);
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }

    /**
     * Reads the named agent output variable, e.g. the verification answer.
     *
     * <p>Trimmed and lower-cased so a prompt tweak that yields {@code "Confirmed"} instead of
     * {@code "confirmed"} does not quietly break the mapping and send every caller to retry.</p>
     */
    public Optional<String> agentVariable(String name) {
        if (finalAgentVariables == null || name == null) {
            return Optional.empty();
        }
        Object value = finalAgentVariables.get(name);
        if (value == null) {
            return Optional.empty();
        }
        String text = value.toString().trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text.toLowerCase());
    }

    /**
     * Flattens the transcript into a single line for the audit log.
     *
     * <p>Capped because it is stored in the complaint's remarks column, and a long call would
     * otherwise overflow it and fail the whole webhook.</p>
     */
    public String transcriptSummary(int maxLength) {
        if (interactionTranscript == null || interactionTranscript.isEmpty()) {
            return null;
        }
        StringBuilder summary = new StringBuilder();
        for (TranscriptTurn turn : interactionTranscript) {
            if (turn == null || turn.text() == null || turn.text().isBlank()) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append(" | ");
            }
            summary.append(turn.role() == null ? "?" : turn.role()).append(": ").append(turn.text().trim());
            if (summary.length() >= maxLength) {
                break;
            }
        }
        if (summary.isEmpty()) {
            return null;
        }
        return summary.length() <= maxLength ? summary.toString() : summary.substring(0, maxLength - 3) + "...";
    }

    private static Long parseLong(String raw) {
        try {
            // Jackson may have deserialised the JSON number as a Double, leaving "12.0".
            return (long) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Telephony channel details for the attempt. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChannelInfo(
            @JsonProperty("channel_type") String channelType,
            @JsonProperty("channel_provider") String channelProvider,
            @JsonProperty("agent_phone_number") String agentPhoneNumber
    ) {
    }

    /** Our webhook configuration, echoed back so metadata survives the round trip. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookConfig(
            @JsonProperty("url") String url,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
    }

    /** One conversational turn; {@code en_text} is Sarvam's English translation. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TranscriptTurn(
            @JsonProperty("role") String role,
            @JsonProperty("en_text") String text
    ) {
    }
}
