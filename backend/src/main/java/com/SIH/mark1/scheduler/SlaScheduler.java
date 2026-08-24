package com.SIH.mark1.scheduler;

import com.SIH.mark1.service.SlaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically re-evaluates SLA state for all open complaints.
 *
 * <p>Intentionally a thin wrapper: all logic lives in {@link SlaService} so it can be
 * unit-tested without a scheduler or Spring context.</p>
 *
 * <p>Disabled by setting {@code sla.scheduler.enabled=false} — used by integration
 * tests so background passes cannot interfere with assertions.</p>
 */
@Component
@ConditionalOnProperty(name = "sla.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);

    private final SlaService slaService;

    public SlaScheduler(SlaService slaService) {
        this.slaService = slaService;
    }

    /**
     * Runs every {@code sla.scheduler.fixed-rate-ms} milliseconds (default 15 min),
     * after an initial delay that lets the application finish booting.
     */
    @Scheduled(
            fixedRateString = "${sla.scheduler.fixed-rate-ms:900000}",
            initialDelayString = "${sla.scheduler.initial-delay-ms:60000}"
    )
    public void scanSlaBreaches() {
        try {
            int updated = slaService.scanAndUpdateOpenComplaints();
            if (updated > 0) {
                log.info("SLA scheduler updated {} complaint(s)", updated);
            }
        } catch (Exception ex) {
            // Never let a scheduled task die — it would stop all future runs.
            log.error("SLA scheduler run failed: {}", ex.getMessage(), ex);
        }
    }
}
