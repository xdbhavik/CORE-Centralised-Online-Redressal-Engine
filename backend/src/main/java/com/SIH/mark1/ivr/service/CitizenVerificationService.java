package com.SIH.mark1.ivr.service;

import com.SIH.mark1.dto.response.VerificationStatusResponse;
import com.SIH.mark1.ivr.config.IvrProperties;
import com.SIH.mark1.ivr.config.SarvamVoiceProperties;
import com.SIH.mark1.ivr.dto.SarvamCallWebhookPayload;
import com.SIH.mark1.ivr.model.IvrCallLog;
import com.SIH.mark1.ivr.model.IvrCallState;
import com.SIH.mark1.ivr.model.IvrLanguage;
import com.SIH.mark1.ivr.model.IvrSession;
import com.SIH.mark1.ivr.repository.IvrCallLogRepository;
import com.SIH.mark1.ivr.repository.IvrSessionRepository;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.NotificationType;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.VerificationStatus;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.service.AutoAssignmentService;
import com.SIH.mark1.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Citizen call-back verification (verification #1): "did you really lodge this complaint?"
 *
 * <p>An anti-spam gate. A newly created complaint is {@link VerificationStatus#PENDING} and
 * held back from departmental action until the citizen confirms it on an outbound call placed
 * through Sarvam AI Voice Agents. The outcomes are handled as:</p>
 *
 * <ul>
 *   <li><b>confirmed</b> → {@code VERIFIED}, action allowed, complaint flows to the department.</li>
 *   <li><b>denied</b> → {@code REJECTED} and flagged; action stays blocked pending admin review.</li>
 *   <li><b>no answer / unclear</b> → retry after a delay; once attempts are exhausted →
 *       {@code FAILED}, and by default action is <em>allowed</em> anyway (fail-open).</li>
 * </ul>
 *
 * <p>Because the Sarvam agent runs the conversation itself, the citizen's answer arrives once
 * the call ends, extracted into an agent variable — not as a keypress mid-call. That is why
 * there is a single {@link #handleCallCompleted} entry point rather than a handler per prompt:
 * every outcome, including "they never picked up", flows through the same webhook.</p>
 *
 * <p>The fail-open choice on unanswered calls is deliberate: a citizen whose phone is
 * switched off would otherwise silently lose service, which is worse than an occasional
 * unverified complaint reaching an officer who can dismiss it. Set
 * {@code ivr.verification.proceed-on-failed=false} to fail closed instead.</p>
 *
 * <p>Note that verification never touches the SLA clock — that starts at creation — so a
 * citizen's deadline never depends on us reaching them by phone.</p>
 */
@Service
public class CitizenVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CitizenVerificationService.class);

    /** Webhook path Sarvam posts the call outcome to once the attempt finishes. */
    public static final String CALLBACK_PATH = "/api/v1/ivr/sarvam/verify/callback";

    /** Cap on the transcript excerpt stored in the audit log, in characters. */
    private static final int TRANSCRIPT_LOG_LIMIT = 500;

    private final ComplaintRepository complaintRepository;
    private final IvrSessionRepository sessionRepository;
    private final IvrCallLogRepository callLogRepository;
    private final SarvamVoiceCallService voiceCallService;
    private final PhoneNumberFormatter phoneNumberFormatter;
    private final SarvamVoiceProperties voiceProperties;
    private final VerificationPrompts prompts;
    private final NotificationService notificationService;
    private final IvrProperties ivrProperties;

    /**
     * Resolved lazily: assignment happens only after a citizen confirms, and going through a
     * provider keeps this service constructible even though the assignment side of the graph
     * also reaches back into complaint handling.
     */
    private final ObjectProvider<AutoAssignmentService> autoAssignmentServiceProvider;

    public CitizenVerificationService(ComplaintRepository complaintRepository,
                                      IvrSessionRepository sessionRepository,
                                      IvrCallLogRepository callLogRepository,
                                      SarvamVoiceCallService voiceCallService,
                                      PhoneNumberFormatter phoneNumberFormatter,
                                      SarvamVoiceProperties voiceProperties,
                                      VerificationPrompts prompts,
                                      NotificationService notificationService,
                                      IvrProperties ivrProperties,
                                      ObjectProvider<AutoAssignmentService> autoAssignmentServiceProvider) {
        this.complaintRepository = complaintRepository;
        this.sessionRepository = sessionRepository;
        this.callLogRepository = callLogRepository;
        this.voiceCallService = voiceCallService;
        this.phoneNumberFormatter = phoneNumberFormatter;
        this.voiceProperties = voiceProperties;
        this.prompts = prompts;
        this.notificationService = notificationService;
        this.ivrProperties = ivrProperties;
        this.autoAssignmentServiceProvider = autoAssignmentServiceProvider;
    }

    // ─────────────────────────────────────────────────────────────
    // Entry point: called right after a complaint is created
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts verification for a freshly created complaint and places the first call.
     *
     * <p>Never throws: complaint creation must succeed even when telephony is down, so all
     * failures here degrade to "retry later" (or fail-open) rather than propagating.</p>
     */
    @Transactional
    public void startVerification(Complaint complaint) {
        if (complaint == null) {
            return;
        }

        // Gate disabled by config: nothing to verify, let the complaint proceed immediately.
        if (!ivrProperties.resolvedVerification().resolvedGateEnabled()) {
            complaint.setActionAllowed(true);
            complaint.setVerificationNextAttemptAt(null);
            complaint.setVerificationRemarks("Verification gate disabled by configuration");
            complaintRepository.save(complaint);
            return;
        }

        complaint.setVerificationStatus(VerificationStatus.PENDING);
        complaint.setActionAllowed(false);
        complaint.setVerificationFlagged(false);
        complaint.setVerificationAttempts(0);
        complaintRepository.save(complaint);

        attemptCall(complaint);
    }

    /**
     * Places one verification call attempt.
     *
     * @return true when Sarvam accepted the call
     */
    @Transactional
    public boolean attemptCall(Complaint complaint) {
        String mobile = complaint.getCitizen() == null ? null : complaint.getCitizen().getMobile();
        int attemptNo = safeAttempts(complaint) + 1;
        int maxAttempts = ivrProperties.resolvedVerification().resolvedMaxAttempts();

        complaint.setVerificationAttempts(attemptNo);
        complaint.setVerificationLastAttemptAt(LocalDateTime.now());

        SarvamVoiceCallService.Result result = voiceCallService.placeVerificationCall(
                mobile,
                CALLBACK_PATH,
                complaint.getComplaintId(),
                complaint.getComplaintNo(),
                citizenNameOf(complaint),
                languageOf(complaint));

        if (result.isSkipped()) {
            // Telephony is off (feature dark or credentials absent). Rolling the attempt back
            // keeps the counter meaningful: attempts should count real dial attempts only, so
            // enabling the IVR later does not start from an already-exhausted budget.
            complaint.setVerificationAttempts(attemptNo - 1);
            complaint.setVerificationLastAttemptAt(null);
            complaint.setVerificationNextAttemptAt(null);
            complaint.setActionAllowed(true);
            complaint.setVerificationRemarks("Verification not attempted: " + result.detail());
            complaintRepository.save(complaint);
            logEvent(null, "VERIFY_SKIPPED", null, complaint, result.detail());
            return false;
        }

        if (result.isPermanentFailure()) {
            // Redialling a malformed number can never work, so skip straight to exhausted.
            exhaustAttempts(complaint, result.detail());
            logEvent(null, "VERIFY_INVALID_NUMBER", null, complaint, result.detail());
            return false;
        }

        if (!result.isPlaced()) {
            scheduleRetryOrExhaust(complaint, "Call attempt failed: " + result.detail());
            logEvent(null, "VERIFY_CALL_FAILED", null, complaint, result.detail());
            return false;
        }

        // Call is ringing. A retry is pre-scheduled as a safety net in case Sarvam's post-call
        // webhook never arrives (lost callback, tunnel down) — otherwise the complaint would
        // sit in PENDING forever with nothing to move it forward. The webhook clears it.
        complaint.setVerificationNextAttemptAt(
                LocalDateTime.now().plusMinutes(ivrProperties.resolvedVerification().resolvedRetryDelayMinutes()));
        complaint.setVerificationRemarks(null);
        complaintRepository.save(complaint);

        // The session is what lets the webhook trust itself: an attempt id that has no session
        // was never issued by us, so a forged callback has nothing to match against.
        sessionRepository.save(IvrSession.builder()
                .callSid(result.attemptId())
                .mobile(phoneNumberFormatter.toLocalTenDigits(mobile))
                .rawFromNumber(mobile)
                .direction("OUTBOUND")
                .state(IvrCallState.VERIFYING_REGISTRATION)
                .language(languageOf(complaint))
                .complaintId(complaint.getComplaintId())
                .complaintNo(complaint.getComplaintNo())
                .retryCount(0)
                .build());

        logEvent(result.attemptId(), "VERIFY_CALL_PLACED", null, complaint,
                "attempt " + attemptNo + " of " + maxAttempts);
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // Post-call webhook
    // ─────────────────────────────────────────────────────────────

    /**
     * Applies the outcome of a finished Sarvam call.
     *
     * <p>The single decision point of the whole flow. Sarvam reports once per attempt, so this
     * one method covers every branch that Twilio spread across four webhooks: the citizen's
     * answer, an unanswered ring, and a provider-side failure.</p>
     *
     * <p>Never throws. A 5xx would make Sarvam re-deliver the callback and could double-apply
     * a transition — counting one unanswered call as two exhausted attempts — so problems are
     * logged and swallowed.</p>
     *
     * @return true when the payload was accepted (whether or not it changed anything)
     */
    @Transactional
    public boolean handleCallCompleted(SarvamCallWebhookPayload payload) {
        if (payload == null) {
            return false;
        }
        String attemptId = payload.attemptId();
        Long metadataComplaintId = payload
                .complaintId(SarvamVoiceCallService.META_COMPLAINT_ID)
                .orElse(null);

        Optional<Complaint> found = resolveComplaint(attemptId, metadataComplaintId);
        if (found.isEmpty()) {
            log.warn("Verification callback for unknown complaint: attemptId={} complaintId={}",
                    attemptId, metadataComplaintId);
            return false;
        }

        Complaint complaint = found.get();
        SarvamVoiceCallService.CallStatus status = SarvamVoiceCallService.CallStatus.from(payload.status());
        logEvent(attemptId, "VERIFY_CALL_COMPLETED", null, complaint, describeOutcome(payload, status));

        // Already settled by an app confirmation, an admin decision, or a re-delivered copy of
        // this same callback. Re-applying would overwrite a recorded decision, so stop here.
        if (VerificationStatus.isTerminal(complaint.getVerificationStatus())) {
            logEvent(attemptId, "VERIFY_ALREADY_SETTLED", null, complaint,
                    String.valueOf(complaint.getVerificationStatus()));
            return true;
        }

        if (status != SarvamVoiceCallService.CallStatus.CONNECTED) {
            markSessionState(attemptId, IvrCallState.ABANDONED);
            scheduleRetryOrExhaust(complaint, unreachableReason(payload, status));
            return true;
        }

        // Connected: the agent's extracted answer decides the outcome.
        String answer = payload.agentVariable(voiceProperties.resolvedResultVariable()).orElse(null);
        String transcript = payload.transcriptSummary(TRANSCRIPT_LOG_LIMIT);

        if (voiceProperties.resolvedConfirmedValue().equalsIgnoreCase(answer)) {
            markVerified(complaint, "Confirmed by citizen on Sarvam verification call");
            logEvent(attemptId, "VERIFY_CONFIRMED", null, complaint, transcript);
            markSessionState(attemptId, IvrCallState.COMPLETED);
            return true;
        }

        if (voiceProperties.resolvedDeniedValue().equalsIgnoreCase(answer)) {
            markRejected(complaint, "Citizen denied lodging this complaint on Sarvam verification call");
            logEvent(attemptId, "VERIFY_REJECTED", null, complaint, transcript);
            markSessionState(attemptId, IvrCallState.COMPLETED);
            return true;
        }

        // Talked to us but gave no usable answer — wrong person, confusion, a dropped line, or
        // a prompt that failed to extract. Treated as unresolved and retried rather than
        // guessed at, since guessing either way corrupts the anti-spam signal.
        logEvent(attemptId, "VERIFY_ANSWER_UNCLEAR", null, complaint,
                "result=" + answer + (transcript == null ? "" : "; " + transcript));
        markSessionState(attemptId, IvrCallState.ABANDONED);
        scheduleRetryOrExhaust(complaint, "Citizen gave no clear confirmation on the call");
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // Retry sweep + app-based confirmation
    // ─────────────────────────────────────────────────────────────

    /**
     * Places any verification calls that have come due.
     *
     * @return number of calls placed
     */
    @Transactional
    public int runDueVerificationCalls() {
        if (!voiceCallService.isAvailable()) {
            return 0;
        }
        List<Complaint> due = complaintRepository.findVerificationCallsDue(
                VerificationStatus.PENDING, LocalDateTime.now());

        int placed = 0;
        for (Complaint complaint : due) {
            try {
                if (safeAttempts(complaint) >= ivrProperties.resolvedVerification().resolvedMaxAttempts()) {
                    exhaustAttempts(complaint, "All verification call attempts exhausted");
                    continue;
                }
                if (attemptCall(complaint)) {
                    placed++;
                }
            } catch (Exception ex) {
                // One bad row must not abort the sweep for every other complaint.
                log.error("Verification retry failed for complaintId={}: {}",
                        complaint.getComplaintId(), ex.getMessage());
            }
        }
        return placed;
    }

    /**
     * Confirms verification from the citizen app instead of the phone.
     *
     * <p>Needed because the "we could not reach you" notification tells the citizen to
     * confirm in the app; without this the fail-open path would be the only escape and an
     * unreachable citizen could never actively verify.</p>
     *
     * <p>Idempotent: confirming an already-verified complaint is a no-op rather than an
     * error, so a double tap in the app cannot re-notify the citizen or re-trigger
     * assignment.</p>
     *
     * @throws IllegalStateException when the citizen already denied this complaint on the phone
     */
    @Transactional
    public void confirmFromApp(Complaint complaint) {
        if (complaint == null || VerificationStatus.VERIFIED.equals(complaint.getVerificationStatus())) {
            return;
        }

        // A denial keyed on the call outranks an app tap. Answering the phone on the
        // registered number proves possession of the SIM; an app session only proves someone
        // is logged in. Letting the weaker signal silently overturn the stronger one would
        // also empty the admin review queue of exactly the cases a human should look at.
        if (isDenied(complaint)) {
            logEvent(null, "VERIFY_APP_CONFIRM_BLOCKED", null, complaint, "denied on call");
            throw new IllegalStateException(
                    "This complaint was reported as not lodged by you on our verification call. "
                            + "It is under review by our team and cannot be confirmed from the app.");
        }

        markVerified(complaint, "Confirmed by citizen in app");
        logEvent(null, "VERIFY_CONFIRMED_APP", null, complaint, null);
    }

    // ─────────────────────────────────────────────────────────────
    // Admin override
    // ─────────────────────────────────────────────────────────────

    /**
     * Admin overrides a denial (or an unreachable outcome) and releases the complaint.
     *
     * <p>Used when the denial looks like a mistake — wrong keypress, a relative answering the
     * phone, or a citizen who misunderstood the question. Without this a single stray "2"
     * would bury a genuine grievance permanently.</p>
     *
     * <p>{@code verificationStatus} is deliberately <em>not</em> rewritten to VERIFIED: the
     * citizen did say no, and that remains the historical fact. What the override changes is
     * {@code verificationFlagged}, which is what "needs admin attention" means — so the
     * complaint leaves the review queue without the record claiming a confirmation that
     * never happened.</p>
     *
     * @param reason mandatory justification, recorded on the complaint
     */
    @Transactional
    public void approveByAdmin(Complaint complaint, String reason) {
        if (complaint == null) {
            return;
        }
        complaint.setVerificationFlagged(false);
        complaint.setActionAllowed(true);
        complaint.setVerificationNextAttemptAt(null);
        complaint.setVerificationRemarks("Admin override (approved): " + reason);
        complaintRepository.save(complaint);

        logEvent(null, "VERIFY_ADMIN_APPROVED", null, complaint, reason);

        notify(complaint, NotificationType.VERIFICATION_CONFIRMED,
                "Complaint under process",
                "Your complaint " + complaint.getComplaintNo()
                        + " has been reviewed and sent to the concerned department.");

        // Same release path as a phone confirmation: an approved complaint that nobody
        // assigns is no better off than a blocked one.
        releaseForAssignment(complaint);
    }

    /**
     * Admin confirms the complaint really is fake and keeps it blocked.
     *
     * <p>Only records the verification side of the decision. Closing the complaint's
     * lifecycle status is the caller's job, so status transitions stay in one place instead
     * of being split across the IVR module.</p>
     *
     * @param reason mandatory justification, recorded on the complaint
     */
    @Transactional
    public void rejectByAdmin(Complaint complaint, String reason) {
        if (complaint == null) {
            return;
        }
        complaint.setVerificationStatus(VerificationStatus.REJECTED);
        // Flag cleared: the review has happened, so it should leave the pending queue while
        // action stays permanently blocked.
        complaint.setVerificationFlagged(false);
        complaint.setActionAllowed(false);
        complaint.setVerificationNextAttemptAt(null);
        complaint.setVerificationRemarks("Admin override (rejected): " + reason);
        complaintRepository.save(complaint);

        logEvent(null, "VERIFY_ADMIN_REJECTED", null, complaint, reason);

        notify(complaint, NotificationType.VERIFICATION_REJECTED,
                "Complaint closed",
                "Complaint " + complaint.getComplaintNo()
                        + " has been closed after review as it could not be verified.");
    }


    // ─────────────────────────────────────────────────────────────
    // Action gate — consulted before any departmental work
    // ─────────────────────────────────────────────────────────────

    /**
     * True when departmental action (assignment, officer work, resolution) may proceed.
     *
     * <p>Reads the stored flag rather than re-deriving policy so the answer cannot drift
     * between callers if configuration changes mid-flight.</p>
     */
    public boolean isActionAllowed(Complaint complaint) {
        if (complaint == null) {
            return false;
        }
        if (!ivrProperties.resolvedVerification().resolvedGateEnabled()) {
            return true;
        }
        return Boolean.TRUE.equals(complaint.getActionAllowed());
    }

    /** Human-readable reason action is blocked, for API error messages. */
    public String blockedReason(Complaint complaint) {
        if (complaint == null) {
            return "Complaint not found";
        }
        if (Boolean.TRUE.equals(complaint.getVerificationFlagged())
                || VerificationStatus.REJECTED.equals(complaint.getVerificationStatus())) {
            return "Citizen denied lodging this complaint. It is flagged for admin review.";
        }
        return "Awaiting citizen verification call-back. Action is on hold until the citizen confirms.";
    }

    /**
     * Reason action is blocked, or {@code null} when it is allowed.
     *
     * <p>{@link #blockedReason} answers unconditionally, which is right for an exception
     * message but wrong for a DTO — mapping it directly would attach a phantom "on hold"
     * string to every healthy complaint. Callers building responses should use this.</p>
     */
    public String blockedReasonOrNull(Complaint complaint) {
        return isActionAllowed(complaint) ? null : blockedReason(complaint);
    }

    /** True when the citizen explicitly denied lodging the complaint. */
    public boolean isDenied(Complaint complaint) {
        return complaint != null
                && (VerificationStatus.REJECTED.equals(complaint.getVerificationStatus())
                || Boolean.TRUE.equals(complaint.getVerificationFlagged()));
    }

    /**
     * Builds the citizen-facing description of a complaint's verification state.
     *
     * <p>Lives here rather than in each caller because it needs {@link IvrProperties} (for the
     * attempt budget) alongside the gate and the blocked-reason wording. One definition keeps
     * the citizen app, the confirm response and the admin views from disagreeing about what
     * the same complaint's state means.</p>
     */
    public VerificationStatusResponse describe(Complaint complaint) {
        if (complaint == null) {
            return null;
        }
        boolean actionAllowed = isActionAllowed(complaint);
        boolean settled = VerificationStatus.VERIFIED.equals(complaint.getVerificationStatus());

        return VerificationStatusResponse.builder()
                .complaintId(complaint.getComplaintId())
                .complaintNumber(complaint.getComplaintNo())
                .verificationStatus(complaint.getVerificationStatus())
                .actionAllowed(actionAllowed)
                .flagged(Boolean.TRUE.equals(complaint.getVerificationFlagged()))
                .attempts(safeAttempts(complaint))
                .maxAttempts(ivrProperties.resolvedVerification().resolvedMaxAttempts())
                .lastAttemptAt(complaint.getVerificationLastAttemptAt())
                .nextAttemptAt(complaint.getVerificationNextAttemptAt())
                .verifiedAt(complaint.getVerifiedAt())
                .blockedReason(actionAllowed ? null : blockedReason(complaint))
                // Offered unless the answer is already settled: verified needs nothing, and a
                // denial can only be overturned by an admin. Note FAILED still allows it — a
                // citizen we never reached is exactly who this escape hatch is for, even
                // though fail-open has already unblocked the complaint.
                .canConfirmInApp(!settled && !isDenied(complaint))
                .build();
    }


    // ─────────────────────────────────────────────────────────────
    // State transitions
    // ─────────────────────────────────────────────────────────────

    private void markVerified(Complaint complaint, String remarks) {
        complaint.setVerificationStatus(VerificationStatus.VERIFIED);
        complaint.setVerifiedAt(LocalDateTime.now());
        complaint.setVerificationNextAttemptAt(null);
        complaint.setVerificationFlagged(false);
        complaint.setActionAllowed(true);
        complaint.setVerificationRemarks(remarks);
        complaintRepository.save(complaint);

        notify(complaint, NotificationType.VERIFICATION_CONFIRMED,
                "Complaint verified",
                "Your complaint " + complaint.getComplaintNo()
                        + " is verified and has been sent to the concerned department.");

        // Assignment was skipped at creation while the gate was closed, so it has to be
        // triggered here — otherwise a verified complaint would wait for an admin to notice it.
        releaseForAssignment(complaint);
    }

    /**
     * Hands a now-unblocked complaint to auto-assignment.
     *
     * <p>Runs after every transition that opens the gate (confirmed, or attempts exhausted with
     * fail-open) so no path leaves a serviceable complaint unassigned.</p>
     */
    private void releaseForAssignment(Complaint complaint) {
        AutoAssignmentService autoAssignmentService = autoAssignmentServiceProvider.getIfAvailable();
        if (autoAssignmentService == null) {
            return;
        }
        try {
            autoAssignmentService.autoAssignIfEnabled(complaint);
        } catch (Exception ex) {
            // Verification is already recorded; assignment can still be done manually by an admin.
            log.warn("Post-verification auto-assignment failed for complaintId={}: {}",
                    complaint.getComplaintId(), ex.getMessage());
        }
    }

    private void markRejected(Complaint complaint, String remarks) {
        complaint.setVerificationStatus(VerificationStatus.REJECTED);
        complaint.setVerificationNextAttemptAt(null);
        complaint.setVerificationFlagged(true);
        // Stays blocked: a denied complaint must not reach an officer until a human decides
        // whether it was spam or the citizen simply misunderstood the question.
        complaint.setActionAllowed(false);
        complaint.setVerificationRemarks(remarks);
        complaintRepository.save(complaint);

        notify(complaint, NotificationType.VERIFICATION_REJECTED,
                "Complaint not verified",
                "Complaint " + complaint.getComplaintNo()
                        + " was marked as not registered by you. Our team will review it.");
    }

    /** Schedules the next attempt, or gives up if the budget is spent. */
    private void scheduleRetryOrExhaust(Complaint complaint, String reason) {
        IvrProperties.Verification config = ivrProperties.resolvedVerification();
        int attempts = safeAttempts(complaint);

        if (attempts >= config.resolvedMaxAttempts()) {
            exhaustAttempts(complaint, reason);
            return;
        }

        complaint.setVerificationNextAttemptAt(
                LocalDateTime.now().plusMinutes(config.resolvedRetryDelayMinutes()));
        complaint.setVerificationRemarks(reason);
        complaintRepository.save(complaint);

        notify(complaint, NotificationType.VERIFICATION_PENDING,
                prompts.pendingNotificationTitle(languageOf(complaint)),
                prompts.pendingNotificationBody(languageOf(complaint), complaint.getComplaintNo()));
    }

    /** Terminal no-answer state; fails open or closed per configuration. */
    private void exhaustAttempts(Complaint complaint, String reason) {
        boolean proceed = ivrProperties.resolvedVerification().resolvedProceedOnFailed();

        complaint.setVerificationStatus(VerificationStatus.FAILED);
        complaint.setVerificationNextAttemptAt(null);
        complaint.setActionAllowed(proceed);
        complaint.setVerificationRemarks(reason);
        complaintRepository.save(complaint);

        log.info("Verification exhausted for complaintId={} after {} attempt(s); actionAllowed={}",
                complaint.getComplaintId(), safeAttempts(complaint), proceed);

        notify(complaint, NotificationType.VERIFICATION_UNREACHABLE,
                prompts.pendingNotificationTitle(languageOf(complaint)),
                prompts.unreachableNotificationBody(languageOf(complaint), complaint.getComplaintNo()));

        // Fail-open only: when configured to fail closed the complaint must stay out of the
        // officer queue until an admin reviews it.
        if (proceed) {
            releaseForAssignment(complaint);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Citizen-facing reason a call did not produce an answer.
     *
     * <p>Sarvam's {@code failure_reason} is passed through because it often carries the one
     * detail support staff need — a TRAI/NDNC block, for instance, means no amount of
     * redialling will ever reach this number.</p>
     */
    private String unreachableReason(SarvamCallWebhookPayload payload,
                                     SarvamVoiceCallService.CallStatus status) {
        String reason = switch (status) {
            case BUSY -> "Citizen's line was busy";
            case FAILED -> "Call could not be completed";
            case NO_ANSWER -> "Citizen did not answer the verification call";
            default -> "Call ended without a conversation (status: " + payload.status() + ")";
        };
        return payload.failureReason() == null || payload.failureReason().isBlank()
                ? reason
                : reason + " (" + payload.failureReason() + ")";
    }

    /** One-line audit description of a finished call. */
    private String describeOutcome(SarvamCallWebhookPayload payload,
                                   SarvamVoiceCallService.CallStatus status) {
        StringBuilder detail = new StringBuilder("status=").append(status);
        if (payload.duration() != null) {
            detail.append("; duration=").append(payload.duration()).append("s");
        }
        if (payload.interactionId() != null) {
            // Recorded so support can pull the recording/transcript from Sarvam later.
            detail.append("; interaction=").append(payload.interactionId());
        }
        if (payload.failureReason() != null && !payload.failureReason().isBlank()) {
            detail.append("; reason=").append(payload.failureReason());
        }
        return detail.toString();
    }

    /**
     * Finds the complaint a webhook refers to.
     *
     * <p>Prefers the session's stored complaint id over the value in the payload, since the
     * session was written by us when the call was placed and so cannot be influenced by a
     * forged callback.</p>
     */
    private Optional<Complaint> resolveComplaint(String attemptId, Long complaintIdFromPayload) {
        Optional<Long> fromSession = sessionRepository.findByCallSid(attemptId)
                .map(IvrSession::getComplaintId);

        Long complaintId = fromSession.orElse(complaintIdFromPayload);
        if (complaintId == null) {
            return Optional.empty();
        }
        return complaintRepository.findById(complaintId)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()));
    }

    private void markSessionState(String callSid, IvrCallState state) {
        if (callSid == null) {
            return;
        }
        sessionRepository.findByCallSid(callSid).ifPresent(session -> {
            if (state.isTerminal()) {
                session.complete(state);
            } else {
                session.setState(state);
            }
            sessionRepository.save(session);
        });
    }

    private void logEvent(String callSid, String eventType, String digits,
                          Complaint complaint, String detail) {
        try {
            callLogRepository.save(IvrCallLog.builder()
                    .callSid(callSid)
                    .eventType(eventType)
                    .digits(digits)
                    .stateBefore(complaint == null ? null : complaint.getVerificationStatus())
                    .stateAfter(complaint == null ? null : complaint.getVerificationStatus())
                    .rawPayload(complaint == null ? null : "complaintNo=" + complaint.getComplaintNo())
                    .errorMessage(detail)
                    .build());
        } catch (Exception ex) {
            // The audit trail is valuable but must never break a live call.
            log.warn("Could not write IVR call log ({}): {}", eventType, ex.getMessage());
        }
    }

    private void notify(Complaint complaint, NotificationType type, String title, String message) {
        try {
            User citizen = complaint.getCitizen();
            if (citizen != null) {
                notificationService.sendNotification(citizen, complaint, type, title, message);
            }
        } catch (Exception ex) {
            log.warn("Verification notification failed for complaintId={}: {}",
                    complaint.getComplaintId(), ex.getMessage());
        }
    }

    private IvrLanguage languageOf(Complaint complaint) {
        if (complaint == null || complaint.getCitizen() == null) {
            return IvrLanguage.HINDI;
        }
        return IvrLanguage.fromPreferredLanguage(complaint.getCitizen().getLanguage());
    }

    /** Citizen's name for the agent's greeting, or null to let it fall back to a generic one. */
    private String citizenNameOf(Complaint complaint) {
        if (complaint == null || complaint.getCitizen() == null) {
            return null;
        }
        String name = complaint.getCitizen().getName();
        return name == null || name.isBlank() ? null : name.trim();
    }

    private int safeAttempts(Complaint complaint) {
        return complaint.getVerificationAttempts() == null ? 0 : complaint.getVerificationAttempts();
    }
}
