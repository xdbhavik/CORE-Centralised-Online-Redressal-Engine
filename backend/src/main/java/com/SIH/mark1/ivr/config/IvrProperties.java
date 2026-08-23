package com.SIH.mark1.ivr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IVR module configuration, bound from the {@code ivr.*} prefix.
 *
 * <p>The whole IVR subsystem is gated behind {@link #enabled()} so the feature can
 * ship dark: with {@code ivr.enabled=false} no outbound calls are placed and the
 * webhook rejects traffic, which also keeps the test suite free of live Sarvam calls.</p>
 *
 * <p>Provider credentials live separately in {@link SarvamVoiceProperties} — these are the
 * settings that describe <em>our</em> verification policy (retry budget, fail-open
 * behaviour, public URL) and would survive another change of telephony vendor.</p>
 */
@ConfigurationProperties(prefix = "ivr")
public record IvrProperties(
        boolean enabled,
        String provider,
        String basePublicUrl,
        Integer maxRetries,
        Integer retryDelayMinutes,
        Recording recording,
        boolean validateSignature,
        Verification verification
) {

    /** Sarvam must reach us on a public URL; in dev this is the ngrok tunnel. */
    public String resolvedBasePublicUrl() {
        if (basePublicUrl == null || basePublicUrl.isBlank()) {
            return "http://localhost:8080";
        }
        return basePublicUrl.endsWith("/")
                ? basePublicUrl.substring(0, basePublicUrl.length() - 1)
                : basePublicUrl;
    }

    /**
     * Absolute webhook URL for a given IVR sub-path.
     *
     * <p>Built from configuration rather than from the incoming request because the callback
     * URL has to be handed to Sarvam <em>before</em> any request exists to derive it from.</p>
     */
    public String webhookUrl(String path) {
        return resolvedBasePublicUrl() + (path.startsWith("/") ? path : "/" + path);
    }

    public int resolvedMaxRetries() {
        return maxRetries == null || maxRetries < 1 ? 3 : maxRetries;
    }

    public int resolvedRetryDelayMinutes() {
        return retryDelayMinutes == null || retryDelayMinutes < 1 ? 30 : retryDelayMinutes;
    }

    public int resolvedMaxRecordingSeconds() {
        return recording == null || recording.maxSeconds() == null || recording.maxSeconds() < 5
                ? 60
                : recording.maxSeconds();
    }

    /** Never null, so callers can read verification settings without a null check. */
    public Verification resolvedVerification() {
        return verification == null ? Verification.defaults() : verification;
    }

    public record Recording(Integer maxSeconds) {
    }

    /**
     * Settings for the outbound "did you really lodge this complaint?" call-back.
     *
     * @param gateEnabled     whether an unverified complaint should hold back departmental action
     * @param proceedOnFailed whether to allow action once all call attempts are exhausted
     * @param maxAttempts     how many times to call before giving up
     * @param retryDelayMinutes how long to wait between attempts
     */
    public record Verification(
            Boolean gateEnabled,
            Boolean proceedOnFailed,
            Integer maxAttempts,
            Integer retryDelayMinutes
    ) {

        public static Verification defaults() {
            return new Verification(null, null, null, null);
        }

        public boolean resolvedGateEnabled() {
            return gateEnabled == null || gateEnabled;
        }

        /**
         * Fail-open by default.
         *
         * <p>If every call attempt goes unanswered we still let the complaint proceed. The
         * alternative — blocking indefinitely — means a citizen whose phone was simply switched
         * off silently loses service, which is a far worse outcome than letting an occasional
         * unverified complaint through to an officer who can dismiss it.</p>
         */
        public boolean resolvedProceedOnFailed() {
            return proceedOnFailed == null || proceedOnFailed;
        }

        public int resolvedMaxAttempts() {
            return maxAttempts == null || maxAttempts < 1 ? 3 : maxAttempts;
        }

        public int resolvedRetryDelayMinutes() {
            return retryDelayMinutes == null || retryDelayMinutes < 1 ? 30 : retryDelayMinutes;
        }
    }
}
