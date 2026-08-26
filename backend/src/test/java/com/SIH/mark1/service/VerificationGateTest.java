package com.SIH.mark1.service;

import com.SIH.mark1.dto.request.ComplaintAssignRequest;
import com.SIH.mark1.ivr.config.IvrProperties;
import com.SIH.mark1.ivr.config.SarvamVoiceProperties;
import com.SIH.mark1.ivr.repository.IvrCallLogRepository;
import com.SIH.mark1.ivr.repository.IvrSessionRepository;
import com.SIH.mark1.ivr.service.CitizenVerificationService;
import com.SIH.mark1.ivr.service.PhoneNumberFormatter;
import com.SIH.mark1.ivr.service.SarvamVoiceCallService;
import com.SIH.mark1.ivr.service.VerificationPrompts;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.VerificationStatus;
import com.SIH.mark1.repository.AiAnalysisRepository;
import com.SIH.mark1.repository.ComplaintAssignmentHistoryRepository;
import com.SIH.mark1.repository.ComplaintAssignmentRepository;
import com.SIH.mark1.repository.ComplaintMediaRepository;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.ComplaintStatusMasterRepository;
import com.SIH.mark1.repository.DepartmentRepository;
import com.SIH.mark1.repository.OfficerDepartmentRepository;
import com.SIH.mark1.repository.OfficerNoteRepository;
import com.SIH.mark1.repository.PriorityMasterRepository;
import com.SIH.mark1.repository.StatusHistoryRepository;
import com.SIH.mark1.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the citizen call-back verification gate.
 *
 * <p>The gate is the entire point of the IVR feature: an unverified complaint must not consume
 * officer time, and a complaint the citizen explicitly denied must not reach an officer at all.
 * Equally important is the other direction — a citizen whose phone we simply could not reach
 * must still get service. Both halves are asserted here because a regression in either one is
 * silent in normal use: nothing throws, complaints just quietly do (or do not) get worked on.</p>
 *
 * <p>Assignment cases drive {@link AssignmentManagementService#assign} rather than calling the
 * gate helper directly, since the bug this suite exists to prevent was a caller that skipped
 * the gate — testing the helper alone would have passed while the leak was wide open.</p>
 */
@DisplayName("Citizen verification gate")
class VerificationGateTest {

    private ComplaintRepository complaintRepository;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        complaintRepository = mock(ComplaintRepository.class);
        userRepository = mock(UserRepository.class);
    }

    // ─────────────────────────────────────────────────────────────
    // Fixtures
    // ─────────────────────────────────────────────────────────────

    /** Verification config with the gate on and the given fail-open policy. */
    private IvrProperties properties(boolean gateEnabled, boolean proceedOnFailed) {
        return new IvrProperties(
                false, "sarvam", null, null, null, null, false,
                new IvrProperties.Verification(gateEnabled, proceedOnFailed, 3, 30));
    }

    private CitizenVerificationService verificationService(IvrProperties properties) {
        @SuppressWarnings("unchecked")
        ObjectProvider<AutoAssignmentService> autoAssignmentProvider = mock(ObjectProvider.class);
        lenient().when(autoAssignmentProvider.getIfAvailable()).thenReturn(null);

        // Telephony collaborators are bare mocks: these tests never place a call, they assert
        // what the gate does with a verification outcome that is already recorded.
        return new CitizenVerificationService(
                complaintRepository,
                mock(IvrSessionRepository.class),
                mock(IvrCallLogRepository.class),
                mock(SarvamVoiceCallService.class),
                mock(PhoneNumberFormatter.class),
                mock(SarvamVoiceProperties.class),
                mock(VerificationPrompts.class),
                mock(NotificationService.class),
                properties,
                autoAssignmentProvider);
    }

    /**
     * Builds the assignment service with only the collaborators the gate path touches.
     *
     * <p>Everything past the gate is left as a bare mock on purpose: these tests assert where
     * execution stops, not that a full assignment succeeds end to end.</p>
     */
    private AssignmentManagementService assignmentService(CitizenVerificationService gate) {
        @SuppressWarnings("unchecked")
        ObjectProvider<CitizenVerificationService> gateProvider = mock(ObjectProvider.class);
        lenient().when(gateProvider.getIfAvailable()).thenReturn(gate);

        return new AssignmentManagementService(
                complaintRepository,
                userRepository,
                mock(DepartmentRepository.class),
                mock(PriorityMasterRepository.class),
                mock(ComplaintStatusMasterRepository.class),
                mock(StatusHistoryRepository.class),
                mock(OfficerDepartmentRepository.class),
                mock(ComplaintAssignmentRepository.class),
                mock(ComplaintAssignmentHistoryRepository.class),
                mock(AiAnalysisRepository.class),
                mock(ComplaintMediaRepository.class),
                mock(OfficerNoteRepository.class),
                mock(NotificationService.class),
                mock(SlaService.class),
                gateProvider);
    }

    private Complaint complaint(String verificationStatus, boolean flagged, boolean actionAllowed) {
        Complaint complaint = new Complaint();
        complaint.setComplaintId(1L);
        complaint.setComplaintNo("CMP-001");
        complaint.setDeleted(false);
        complaint.setVerificationStatus(verificationStatus);
        complaint.setVerificationFlagged(flagged);
        complaint.setActionAllowed(actionAllowed);
        return complaint;
    }

    private ComplaintAssignRequest assignRequest() {
        ComplaintAssignRequest request = new ComplaintAssignRequest();
        request.setComplaintId(1L);
        request.setOfficerId(99L);
        return request;
    }

    /** Registers the complaint for lookup and points the officer id at nobody. */
    private void stubLookups(Complaint complaint) {
        when(complaintRepository.findById(1L)).thenReturn(Optional.of(complaint));
        // Deliberately absent: an assignment that reaches this lookup has cleared the gate,
        // which is what these tests measure. Stubbing a real officer would drag the whole
        // status/SLA/notification pipeline into a test about one boolean.
        lenient().when(userRepository.findById(99L)).thenReturn(Optional.empty());
    }

    /** Asserts the gate let the call through — it failed later, on the absent officer. */
    private void assertPassedGate(AssignmentManagementService service) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> service.assign(assignRequest(), "admin-mobile", false));
        assertEquals("Officer not found", thrown.getMessage(),
                "Expected to get past the verification gate and fail on the officer lookup");
    }

    // ─────────────────────────────────────────────────────────────
    // Blocked: the citizen said no
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("when the citizen denied lodging the complaint")
    class Denied {

        @Test
        @DisplayName("manual admin assignment is refused")
        void deniedComplaintCannotBeAssigned() {
            Complaint denied = complaint(VerificationStatus.REJECTED, true, false);
            stubLookups(denied);
            AssignmentManagementService service =
                    assignmentService(verificationService(properties(true, true)));

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> service.assign(assignRequest(), "admin-mobile", false));

            assertTrue(thrown.getMessage().contains("denied"),
                    "Blocked reason should say the citizen denied it, so the admin UI can explain "
                            + "the refusal instead of showing a bare error: " + thrown.getMessage());
        }

        @Test
        @DisplayName("the gate reports it as not actionable and flagged")
        void deniedComplaintIsNotActionable() {
            CitizenVerificationService gate = verificationService(properties(true, true));
            Complaint denied = complaint(VerificationStatus.REJECTED, true, false);

            assertFalse(gate.isActionAllowed(denied));
            assertTrue(gate.isDenied(denied));
            assertTrue(gate.blockedReasonOrNull(denied).contains("flagged for admin review"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Blocked: we have not heard back yet
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a complaint still awaiting its call-back cannot be assigned")
    void pendingComplaintCannotBeAssigned() {
        Complaint pending = complaint(VerificationStatus.PENDING, false, false);
        stubLookups(pending);
        AssignmentManagementService service =
                assignmentService(verificationService(properties(true, true)));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.assign(assignRequest(), "admin-mobile", false));

        assertTrue(thrown.getMessage().contains("Awaiting citizen verification"),
                "Pending should read as a wait, not an accusation: " + thrown.getMessage());
    }

    // ─────────────────────────────────────────────────────────────
    // Allowed: fail-open
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an unreachable citizen does not strand the complaint (fail-open)")
    void unreachableComplaintProceedsWhenFailOpen() {
        // What exhaustAttempts() leaves behind under proceed-on-failed=true.
        Complaint unreachable = complaint(VerificationStatus.FAILED, false, true);
        stubLookups(unreachable);
        CitizenVerificationService gate = verificationService(properties(true, true));

        assertTrue(gate.isActionAllowed(unreachable),
                "A switched-off phone must never be the reason a real grievance is dropped");
        assertNull(gate.blockedReasonOrNull(unreachable),
                "An allowed complaint must not carry a phantom 'on hold' reason into the UI");
        assertPassedGate(assignmentService(gate));
    }

    @Test
    @DisplayName("fail-closed keeps an unreachable complaint out of the officer queue")
    void unreachableComplaintStaysBlockedWhenFailClosed() {
        // The mirror image: proceed-on-failed=false leaves actionAllowed false, so the same
        // FAILED status blocks. Asserting both directions keeps the flag meaningful.
        Complaint unreachable = complaint(VerificationStatus.FAILED, false, false);
        CitizenVerificationService gate = verificationService(properties(true, false));

        assertFalse(gate.isActionAllowed(unreachable));
    }

    // ─────────────────────────────────────────────────────────────
    // Allowed: verified, or the gate is not in play
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a verified complaint assigns normally")
    void verifiedComplaintPassesGate() {
        Complaint verified = complaint(VerificationStatus.VERIFIED, false, true);
        stubLookups(verified);

        assertPassedGate(assignmentService(verificationService(properties(true, true))));
    }

    @Test
    @DisplayName("gate-enabled=false lets even a denied complaint through")
    void gateDisabledAllowsDeniedComplaint() {
        // Operators must be able to switch the feature off without stranding the complaints
        // that were mid-verification when they did.
        Complaint denied = complaint(VerificationStatus.REJECTED, true, false);
        stubLookups(denied);
        CitizenVerificationService gate = verificationService(properties(false, true));

        assertTrue(gate.isActionAllowed(denied));
        assertPassedGate(assignmentService(gate));
    }

    @Test
    @DisplayName("assignment works when the IVR module is absent entirely")
    void missingIvrModuleDisablesGate() {
        // getIfAvailable() == null is the no-IVR deployment. It must read as "no gate",
        // never as "block everything".
        Complaint pending = complaint(VerificationStatus.PENDING, false, false);
        stubLookups(pending);

        assertPassedGate(assignmentService(null));
    }
}
