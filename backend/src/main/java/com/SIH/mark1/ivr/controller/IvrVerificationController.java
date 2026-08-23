package com.SIH.mark1.ivr.controller;

import com.SIH.mark1.ivr.config.IvrProperties;
import com.SIH.mark1.ivr.dto.SarvamCallWebhookPayload;
import com.SIH.mark1.ivr.service.CitizenVerificationService;
import com.SIH.mark1.ivr.service.SarvamVoiceCallService;
import com.SIH.mark1.ivr.service.SarvamWebhookAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sarvam AI post-call webhook for the outbound citizen-verification call.
 *
 * <p>One endpoint, because a Sarvam agent runs the entire conversation and reports the result
 * once the call ends. The previous Twilio integration needed four (answer, digits, pin, status)
 * since we drove each prompt ourselves.</p>
 *
 * <p>This route is {@code permitAll} in the security config — Sarvam's servers cannot present a
 * citizen JWT — so it authenticates itself in three layers before touching any state:</p>
 *
 * <ol>
 *   <li>the HMAC token echoed back in the call metadata ({@link SarvamWebhookAuthenticator});</li>
 *   <li>the {@code attempt_id}, which must match a call this system actually placed;</li>
 *   <li>the verification state machine, which ignores callbacks for settled complaints.</li>
 * </ol>
 *
 * <p>Responses are intentionally uninformative: Sarvam ignores the body, and a detailed error
 * would tell an attacker probing the endpoint which of the checks they failed.</p>
 */
@RestController
@RequestMapping("/api/v1/ivr/sarvam/verify")
public class IvrVerificationController {

    private static final Logger log = LoggerFactory.getLogger(IvrVerificationController.class);

    private final CitizenVerificationService verificationService;
    private final SarvamWebhookAuthenticator webhookAuthenticator;
    private final IvrProperties ivrProperties;

    public IvrVerificationController(CitizenVerificationService verificationService,
                                     SarvamWebhookAuthenticator webhookAuthenticator,
                                     IvrProperties ivrProperties) {
        this.verificationService = verificationService;
        this.webhookAuthenticator = webhookAuthenticator;
        this.ivrProperties = ivrProperties;
    }

    /**
     * Receives the outcome of a finished verification call attempt.
     *
     * <p>Always answers 2xx once the request is authenticated, even if the payload changes
     * nothing. A 5xx would make Sarvam re-deliver the callback, and a replayed transition could
     * count a single unanswered call as two spent attempts.</p>
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestBody SarvamCallWebhookPayload payload) {
        // Feature dark: reject rather than 404 so a stale agent pointed at this deployment gets
        // an unambiguous signal instead of looking like a broken URL.
        if (!ivrProperties.enabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (payload == null) {
            return ResponseEntity.badRequest().build();
        }

        Long complaintId = payload.complaintId(SarvamVoiceCallService.META_COMPLAINT_ID).orElse(null);
        String token = payload.metadataValue(SarvamVoiceCallService.META_TOKEN).orElse(null);

        if (!webhookAuthenticator.isTokenValid(complaintId, token)) {
            log.warn("Rejected verification callback with invalid token (attemptId={} complaintId={})",
                    payload.attemptId(), complaintId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            boolean accepted = verificationService.handleCallCompleted(payload);
            // 404 for an unknown attempt/complaint: nothing to act on, and retrying will not help.
            return accepted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (Exception e) {
            // Swallowed deliberately — see the 5xx note above. The call log and this line are
            // the trail; the scheduled retry sweep is the safety net that keeps the complaint
            // moving even though this callback was lost.
            log.error("Verification callback failed (attemptId={}): {}",
                    payload.attemptId(), e.getMessage(), e);
            return ResponseEntity.noContent().build();
        }
    }
}
