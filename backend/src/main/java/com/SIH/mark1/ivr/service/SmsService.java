package com.SIH.mark1.ivr.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Outbound transactional SMS for verification updates.
 *
 * <p>Exists because verification updates must reach citizens who are <em>not</em> app users:
 * the whole point of the call-back flow is that we could not reach them in-app, so an
 * in-app-only "verification pending" notice would be invisible to exactly the people who
 * need it.</p>
 *
 * <p><b>Currently log-only.</b> The Twilio SDK this used to delegate to was removed along with
 * the rest of the Twilio integration, and Sarvam AI provides voice agents, not SMS — so there
 * is no configured gateway to hand a message to. Rather than delete the class and unpick every
 * call site, it keeps its contract and records what <em>would</em> have been sent, so the
 * verification flow, the message wording and the trigger points all stay intact and testable.
 * Wiring in an Indian gateway (MSG91, Fast2SMS, a DLT-registered aggregator) means
 * implementing {@link #deliver} and reporting {@link #isAvailable} — nothing else needs to
 * change.</p>
 *
 * <p>Note that Indian regulation, not just plumbing, is the reason this cannot simply be
 * pointed at any API: transactional SMS to Indian numbers requires a DLT-registered sender id
 * and pre-approved templates, so the eventual implementation must send an approved template
 * rather than the free-form text assembled here.</p>
 *
 * <p>No method here ever throws: an SMS is a courtesy, and a messaging outage must not fail
 * the grievance operation that triggered it.</p>
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    /**
     * Trim limit for outgoing text.
     *
     * <p>Long messages are silently split into multiple billed segments by carriers, so the
     * body is truncated to keep every verification SMS a single segment.</p>
     */
    private static final int MAX_BODY_LENGTH = 300;

    private final PhoneNumberFormatter phoneNumberFormatter;

    @Value("${sms.enabled:false}")
    private boolean smsEnabled;

    public SmsService(PhoneNumberFormatter phoneNumberFormatter) {
        this.phoneNumberFormatter = phoneNumberFormatter;
    }

    /**
     * Warns when SMS is switched on but no gateway backs it.
     *
     * <p>Worth a warning rather than silence: an operator who sets {@code sms.enabled=true}
     * reasonably expects citizens to receive messages, and would otherwise never learn that
     * every one of them was only written to a log file.</p>
     */
    @PostConstruct
    void logConfigurationState() {
        if (!smsEnabled) {
            log.info("SMS disabled (sms.enabled=false) - verification messages will be logged only");
            return;
        }
        log.warn("sms.enabled=true but no SMS gateway is implemented - messages will be logged only. "
                + "Implement SmsService.deliver() with a DLT-registered provider to send for real.");
    }

    /** True when a real SMS can actually be delivered right now. */
    public boolean isAvailable() {
        // No gateway is wired in, so this is always false; callers already treat a false
        // return as "message not delivered" and carry on.
        return false;
    }

    /**
     * Sends one SMS, or logs it when no gateway is available.
     *
     * @param mobile  recipient in any local or E.164 form
     * @param message body text
     * @return true only when a gateway accepted the message for delivery
     */
    public boolean send(String mobile, String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String body = truncate(message);

        String to = phoneNumberFormatter.toE164(mobile).orElse(null);
        if (to == null) {
            log.warn("Skipping SMS: unusable mobile number");
            return false;
        }

        if (!isAvailable()) {
            // Logged rather than dropped so developers can still verify message wording and
            // trigger points without a paid gateway.
            log.info("[SMS-NOT-SENT] to={} body={}", phoneNumberFormatter.mask(to), body);
            return false;
        }

        try {
            return deliver(to, body);
        } catch (Exception ex) {
            // Swallowed by design: the caller has already committed its business change.
            log.warn("SMS delivery failed to={}: {}", phoneNumberFormatter.mask(to), ex.getMessage());
            return false;
        }
    }

    /**
     * Hands the message to a gateway.
     *
     * <p>Extension point for a real provider. Unreachable today because {@link #isAvailable}
     * short-circuits first; kept as the single seam so adding a gateway does not touch the
     * validation, truncation and masking logic above.</p>
     *
     * @param e164 recipient in strict E.164 form
     * @param body already truncated message text
     * @return true when the gateway accepted the message
     */
    private boolean deliver(String e164, String body) {
        throw new UnsupportedOperationException("No SMS gateway configured");
    }

    private String truncate(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= MAX_BODY_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_BODY_LENGTH - 3) + "...";
    }
}
