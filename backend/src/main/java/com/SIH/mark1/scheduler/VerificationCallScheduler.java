package com.SIH.mark1.scheduler;

import com.SIH.mark1.ivr.service.CitizenVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redials citizens whose verification call went unanswered.
 *
 * <p>Without this sweep an unanswered call would leave the complaint stuck in
 * {@code PENDING} forever — the whole retry-then-fail-open policy depends on something
 * periodically re-examining due rows.</p>
 *
 * <p>Mirrors {@link SlaScheduler}: a thin wrapper so all logic stays unit-testable in
 * {@link CitizenVerificationService}, and disabled via
 * {@code ivr.verification.scheduler.enabled=false} so tests never place real calls.</p>
 */
@Component
@ConditionalOnProperty(
        name = "ivr.verification.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class VerificationCallScheduler {

    private static final Logger log = LoggerFactory.getLogger(VerificationCallScheduler.class);

    private final CitizenVerificationService verificationService;

    public VerificationCallScheduler(CitizenVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Runs every {@code ivr.verification.scheduler.fixed-rate-ms} ms (default 5 min).
     *
     * <p>Polls more often than the SLA scan because a citizen waiting on verification is
     * blocked from service, while the retry delay itself is enforced per complaint by
     * {@code verificationNextAttemptAt}.</p>
     */
    @Scheduled(
            fixedRateString = "${ivr.verification.scheduler.fixed-rate-ms:300000}",
            initialDelayString = "${ivr.verification.scheduler.initial-delay-ms:90000}"
    )
    public void placeDueVerificationCalls() {
        try {
            int placed = verificationService.runDueVerificationCalls();
            if (placed > 0) {
                log.info("Verification scheduler placed {} call(s)", placed);
            }
        } catch (Exception ex) {
            // Never let a scheduled task die — it would stop all future retries.
            log.error("Verification scheduler run failed: {}", ex.getMessage(), ex);
        }
    }
}
