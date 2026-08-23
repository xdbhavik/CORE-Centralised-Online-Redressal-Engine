package com.SIH.mark1.ivr.service;

import com.SIH.mark1.ivr.config.SarvamVoiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * Authenticates Sarvam's post-call webhooks with a per-call HMAC token.
 *
 * <p>This exists because Sarvam, unlike Twilio's {@code X-Twilio-Signature}, does not sign its
 * callbacks. The verification webhook must stay publicly reachable (Sarvam's servers cannot
 * present a citizen JWT), so without a check of our own, anyone who guessed the URL could POST
 * a forged {@code confirmed} result and mark an arbitrary complaint as verified by its owner.
 * That is exactly the fraud the verification gate exists to prevent.</p>
 *
 * <p>The token is minted when the call is placed and travels in
 * {@code webhook_config.metadata}, which Sarvam echoes back verbatim. Binding it to the
 * complaint id makes it useless for any other complaint, so a leaked token cannot be replayed
 * across the system.</p>
 *
 * <p>Verification is defence in depth, not the only gate: the controller additionally requires
 * the {@code attempt_id} to match one this system actually created, and ignores callbacks for
 * complaints already in a terminal state.</p>
 */
@Component
public class SarvamWebhookAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(SarvamWebhookAuthenticator.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SarvamVoiceProperties voiceProperties;

    public SarvamWebhookAuthenticator(SarvamVoiceProperties voiceProperties) {
        this.voiceProperties = voiceProperties;
    }

    /**
     * Mints the token to send with an outbound call.
     *
     * @return the token, or empty when no secret is configured (local/dev runs)
     */
    public Optional<String> issueToken(Long complaintId) {
        if (complaintId == null || !voiceProperties.canSignWebhooks()) {
            return Optional.empty();
        }
        return sign(complaintId);
    }

    /**
     * Checks a token echoed back by Sarvam against the complaint it claims to be for.
     *
     * <p>Returns true when no secret is configured, so a local ngrok run works out of the box.
     * The startup warning in {@code SarvamVoiceCallService} plus the {@code attempt_id} check
     * cover that gap; refusing to run without a secret would block development, while silently
     * accepting one in production is what the warning is there to prevent.</p>
     */
    public boolean isTokenValid(Long complaintId, String presentedToken) {
        if (!voiceProperties.canSignWebhooks()) {
            log.debug("sarvam.voice.webhook-secret not set - skipping webhook token check");
            return true;
        }
        if (complaintId == null || presentedToken == null || presentedToken.isBlank()) {
            return false;
        }
        return sign(complaintId)
                .map(expected -> constantTimeEquals(expected, presentedToken))
                .orElse(false);
    }

    private Optional<String> sign(Long complaintId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    voiceProperties.webhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(String.valueOf(complaintId).getBytes(StandardCharsets.UTF_8));
            return Optional.of(Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        } catch (Exception e) {
            // Never leak the secret or a stack trace into logs on a crypto misconfiguration;
            // callers treat empty as "cannot authenticate" and fall back to the other checks.
            log.error("Unable to compute webhook token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Compares digests without leaking their contents through timing differences. */
    private boolean constantTimeEquals(String expected, String presented) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
