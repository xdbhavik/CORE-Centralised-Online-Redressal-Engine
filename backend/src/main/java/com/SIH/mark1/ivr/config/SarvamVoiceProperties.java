package com.SIH.mark1.ivr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sarvam AI Voice Agents credentials and deployment coordinates, bound from
 * {@code sarvam.voice.*}.
 *
 * <p>Unlike a raw telephony SDK, Sarvam does not take a prompt per call: the conversation
 * itself lives in an <em>agent</em> built in the Sarvam dashboard, and we only reference it
 * by id. That is why so many of these values are opaque identifiers ({@link #appId},
 * {@link #connectionId}, …) rather than behaviour knobs — the behaviour is configured there,
 * not here.</p>
 *
 * <p>Deliberately distinct from {@code ai.sarvam.*}: that key targets {@code api.sarvam.ai}
 * (TTS/STT REST models), while this one targets {@code apps.sarvam.ai} (hosted voice agents).
 * They are separate products with separate keys, and swapping them silently breaks both.</p>
 *
 * @param apiKey            {@code X-API-Key} for apps.sarvam.ai (Settings → API Key)
 * @param baseUrl           API root, normally {@code https://apps.sarvam.ai}
 * @param orgId             organisation id from the dashboard URL
 * @param workspaceId       workspace id from the dashboard URL
 * @param appId             the verification agent's id
 * @param appVersion        agent version to dial; pinned so a dashboard edit cannot silently
 *                          change what citizens hear mid-deployment
 * @param connectionId      telephony connection (rented Sarvam number or BYO provider)
 * @param agentPhoneNumber  caller id the citizen sees, E.164
 * @param webhookSecret     shared secret proving a post-call webhook really came from us
 * @param resultVariable    name of the agent output variable holding the verification answer
 * @param confirmedValue    value of {@link #resultVariable} meaning "yes, I lodged it"
 * @param deniedValue       value of {@link #resultVariable} meaning "no, I did not"
 */
@ConfigurationProperties(prefix = "sarvam.voice")
public record SarvamVoiceProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String orgId,
        String workspaceId,
        String appId,
        Integer appVersion,
        String connectionId,
        String agentPhoneNumber,
        String webhookSecret,
        String resultVariable,
        String confirmedValue,
        String deniedValue
) {

    private static final String DEFAULT_BASE_URL = "https://apps.sarvam.ai";
    private static final String DEFAULT_RESULT_VARIABLE = "verification_result";
    private static final String DEFAULT_CONFIRMED_VALUE = "confirmed";
    private static final String DEFAULT_DENIED_VALUE = "denied";

    /**
     * True only when every value needed to place a call is present.
     *
     * <p>Checked up-front because a partially configured integration fails at the HTTP layer
     * with an opaque 404 on a path containing {@code null}, which is far harder to diagnose
     * than "not configured".</p>
     */
    public boolean isConfigured() {
        return notBlank(apiKey)
                && notBlank(orgId)
                && notBlank(workspaceId)
                && notBlank(appId)
                && notBlank(connectionId)
                && notBlank(agentPhoneNumber);
    }

    /** Lists the missing settings, so startup logs can say exactly what to fill in. */
    public String missingSettings() {
        StringBuilder missing = new StringBuilder();
        appendIfBlank(missing, apiKey, "sarvam.voice.api-key");
        appendIfBlank(missing, orgId, "sarvam.voice.org-id");
        appendIfBlank(missing, workspaceId, "sarvam.voice.workspace-id");
        appendIfBlank(missing, appId, "sarvam.voice.app-id");
        appendIfBlank(missing, connectionId, "sarvam.voice.connection-id");
        appendIfBlank(missing, agentPhoneNumber, "sarvam.voice.agent-phone-number");
        return missing.isEmpty() ? "none" : missing.toString();
    }

    /** Absolute URL of the instant-outbound endpoint for this org/workspace. */
    public String outboundCallUrl() {
        return resolvedBaseUrl()
                + "/api/outbounds/v1/orgs/" + orgId
                + "/workspaces/" + workspaceId
                + "/outbounds";
    }

    public String resolvedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Agent version to dial; 1 is the version every new Sarvam agent starts at. */
    public int resolvedAppVersion() {
        return appVersion == null || appVersion < 1 ? 1 : appVersion;
    }

    public String resolvedResultVariable() {
        return notBlank(resultVariable) ? resultVariable : DEFAULT_RESULT_VARIABLE;
    }

    public String resolvedConfirmedValue() {
        return notBlank(confirmedValue) ? confirmedValue : DEFAULT_CONFIRMED_VALUE;
    }

    public String resolvedDeniedValue() {
        return notBlank(deniedValue) ? deniedValue : DEFAULT_DENIED_VALUE;
    }

    /** Webhook HMAC signing is only possible once a secret exists. */
    public boolean canSignWebhooks() {
        return notBlank(webhookSecret);
    }

    private static void appendIfBlank(StringBuilder target, String value, String name) {
        if (!notBlank(value)) {
            if (!target.isEmpty()) {
                target.append(", ");
            }
            target.append(name);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
