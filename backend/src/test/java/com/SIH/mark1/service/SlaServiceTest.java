package com.SIH.mark1.service;

import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintStatusMaster;
import com.SIH.mark1.model.PriorityMaster;
import com.SIH.mark1.model.SlaStatus;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SLA engine. No Spring context — pure logic with mocked
 * collaborators, so the priority windows and state machine are verified in isolation.
 */
class SlaServiceTest {

    private SlaService slaService;

    @BeforeEach
    void setUp() {
        ComplaintRepository complaintRepository = mock(ComplaintRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        SystemSettingService systemSettingService = mock(SystemSettingService.class);

        // Settings absent → SlaService must use its hardcoded defaults.
        when(systemSettingService.getIntSetting(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        slaService = new SlaService(complaintRepository, userRepository,
                systemSettingService, notificationService);
    }

    // ── Window resolution ──

    @Test
    @DisplayName("HIGH priority resolves to a 24-hour SLA window")
    void highPriorityGets24Hours() {
        assertEquals(24, slaService.resolveSlaHours(priority("HIGH")));
    }

    @Test
    @DisplayName("MEDIUM priority resolves to a 72-hour SLA window")
    void mediumPriorityGets72Hours() {
        assertEquals(72, slaService.resolveSlaHours(priority("MEDIUM")));
    }

    @Test
    @DisplayName("LOW priority resolves to a 120-hour SLA window")
    void lowPriorityGets120Hours() {
        assertEquals(120, slaService.resolveSlaHours(priority("LOW")));
    }

    @Test
    @DisplayName("null and unknown priorities fall back to the MEDIUM window")
    void unknownPriorityFallsBackToMedium() {
        assertEquals(72, slaService.resolveSlaHours(null));
        assertEquals(72, slaService.resolveSlaHours(priority("SOMETHING_ELSE")));
        assertEquals(72, slaService.resolveSlaHours(priority("  ")));
    }

    @Test
    @DisplayName("priority codes are matched case-insensitively")
    void priorityCodeIsCaseInsensitive() {
        assertEquals(24, slaService.resolveSlaHours(priority("high")));
        assertEquals(120, slaService.resolveSlaHours(priority(" Low ")));
    }

    // ── Initial stamping ──

    @Test
    @DisplayName("new complaint gets dueAt = createdAt + window and ON_TRACK status")
    void applyInitialSlaStampsDeadline() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        Complaint complaint = complaint("HIGH", createdAt, null);

        slaService.applyInitialSla(complaint);

        assertEquals(createdAt.plusHours(24), complaint.getSlaDueAt());
        assertEquals(SlaStatus.ON_TRACK, complaint.getSlaStatus());
        assertNull(complaint.getSlaBreachedAt());
        assertNull(complaint.getSlaReminderSentAt());
    }

    // ── Open-complaint transitions ──

    @Test
    @DisplayName("complaint stays ON_TRACK below the near-breach threshold")
    void staysOnTrackEarlyInWindow() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("HIGH", createdAt, "REGISTERED");
        slaService.applyInitialSla(complaint);

        // 6h of a 24h window = 25% elapsed
        boolean changed = slaService.refreshOpenComplaintSla(complaint, createdAt.plusHours(6));

        assertFalse(changed);
        assertEquals(SlaStatus.ON_TRACK, complaint.getSlaStatus());
    }

    @Test
    @DisplayName("complaint becomes NEAR_BREACH at 80% of the window")
    void becomesNearBreachAtThreshold() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("HIGH", createdAt, "IN_PROGRESS");
        slaService.applyInitialSla(complaint);

        // 20h of a 24h window = 83% elapsed
        boolean changed = slaService.refreshOpenComplaintSla(complaint, createdAt.plusHours(20));

        assertTrue(changed);
        assertEquals(SlaStatus.NEAR_BREACH, complaint.getSlaStatus());
        assertNull(complaint.getSlaBreachedAt());
    }

    @Test
    @DisplayName("complaint becomes BREACHED past the deadline and records the breach time")
    void becomesBreachedAfterDeadline() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("HIGH", createdAt, "IN_PROGRESS");
        slaService.applyInitialSla(complaint);

        LocalDateTime now = createdAt.plusHours(25);
        boolean changed = slaService.refreshOpenComplaintSla(complaint, now);

        assertTrue(changed);
        assertEquals(SlaStatus.BREACHED, complaint.getSlaStatus());
        assertEquals(now, complaint.getSlaBreachedAt());
    }

    @Test
    @DisplayName("slaBreachedAt is write-once — later scans never overwrite it")
    void breachTimestampIsWriteOnce() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("HIGH", createdAt, "IN_PROGRESS");
        slaService.applyInitialSla(complaint);

        LocalDateTime firstBreach = createdAt.plusHours(25);
        slaService.refreshOpenComplaintSla(complaint, firstBreach);
        slaService.refreshOpenComplaintSla(complaint, createdAt.plusHours(90));

        assertEquals(firstBreach, complaint.getSlaBreachedAt());
        assertEquals(SlaStatus.BREACHED, complaint.getSlaStatus());
    }

    @Test
    @DisplayName("legacy complaints with no slaDueAt are back-filled from createdAt")
    void backfillsMissingDeadline() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("LOW", createdAt, "REGISTERED");
        // slaDueAt intentionally left null (row created before the SLA feature)

        boolean changed = slaService.refreshOpenComplaintSla(complaint, createdAt.plusHours(1));

        assertTrue(changed);
        assertEquals(createdAt.plusHours(120), complaint.getSlaDueAt());
        assertEquals(SlaStatus.ON_TRACK, complaint.getSlaStatus());
    }

    @Test
    @DisplayName("resolved/closed complaints are never re-evaluated")
    void terminalComplaintsAreSkipped() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("HIGH", createdAt, "RESOLVED");
        complaint.setSlaDueAt(createdAt.plusHours(24));
        complaint.setSlaStatus(SlaStatus.RESOLVED_WITHIN_SLA);

        boolean changed = slaService.refreshOpenComplaintSla(complaint, createdAt.plusHours(500));

        assertFalse(changed);
        assertEquals(SlaStatus.RESOLVED_WITHIN_SLA, complaint.getSlaStatus());
        assertNull(complaint.getSlaBreachedAt());
    }

    // ── Resolution verdicts ──

    @Test
    @DisplayName("resolving before the deadline yields RESOLVED_WITHIN_SLA")
    void resolvingEarlyIsWithinSla() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("MEDIUM", createdAt, "IN_PROGRESS");
        slaService.applyInitialSla(complaint);

        slaService.markResolvedSla(complaint, createdAt.plusHours(10));

        assertEquals(SlaStatus.RESOLVED_WITHIN_SLA, complaint.getSlaStatus());
        assertNull(complaint.getSlaBreachedAt());
    }

    @Test
    @DisplayName("resolving after the deadline yields RESOLVED_AFTER_SLA and stamps the breach")
    void resolvingLateIsAfterSla() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("MEDIUM", createdAt, "IN_PROGRESS");
        slaService.applyInitialSla(complaint);

        slaService.markResolvedSla(complaint, createdAt.plusHours(100));

        assertEquals(SlaStatus.RESOLVED_AFTER_SLA, complaint.getSlaStatus());
        assertEquals(createdAt.plusHours(72), complaint.getSlaBreachedAt());
    }

    @Test
    @DisplayName("resolving exactly at the deadline still counts as within SLA")
    void resolvingExactlyOnDeadlineIsWithinSla() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("HIGH", createdAt, "IN_PROGRESS");
        slaService.applyInitialSla(complaint);

        slaService.markResolvedSla(complaint, createdAt.plusHours(24));

        assertEquals(SlaStatus.RESOLVED_WITHIN_SLA, complaint.getSlaStatus());
    }

    @Test
    @DisplayName("resolving a legacy complaint back-fills the deadline before judging")
    void resolvingLegacyComplaintBackfillsDeadline() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("HIGH", createdAt, "IN_PROGRESS");
        // no slaDueAt

        slaService.markResolvedSla(complaint, createdAt.plusHours(5));

        assertNotNull(complaint.getSlaDueAt());
        assertEquals(createdAt.plusHours(24), complaint.getSlaDueAt());
        assertEquals(SlaStatus.RESOLVED_WITHIN_SLA, complaint.getSlaStatus());
    }

    @Test
    @DisplayName("an earlier breach timestamp survives a late resolution")
    void existingBreachTimestampIsPreservedOnResolve() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        Complaint complaint = complaint("HIGH", createdAt, "IN_PROGRESS");
        slaService.applyInitialSla(complaint);

        LocalDateTime detectedBreach = createdAt.plusHours(25);
        slaService.refreshOpenComplaintSla(complaint, detectedBreach);
        slaService.markResolvedSla(complaint, createdAt.plusHours(40));

        assertEquals(SlaStatus.RESOLVED_AFTER_SLA, complaint.getSlaStatus());
        assertEquals(detectedBreach, complaint.getSlaBreachedAt());
    }

    // ── Helpers ──

    private PriorityMaster priority(String code) {
        PriorityMaster priority = new PriorityMaster();
        priority.setPriorityCode(code);
        return priority;
    }

    private Complaint complaint(String priorityCode, LocalDateTime createdAt, String statusCode) {
        Complaint complaint = new Complaint();
        complaint.setComplaintId(1L);
        complaint.setComplaintNo("TEST-001");
        complaint.setTitle("Test complaint");
        complaint.setPriority(priority(priorityCode));
        complaint.setCreatedAt(createdAt);
        if (statusCode != null) {
            ComplaintStatusMaster status = new ComplaintStatusMaster();
            status.setStatusCode(statusCode);
            complaint.setCurrentStatus(status);
        }
        return complaint;
    }
}
