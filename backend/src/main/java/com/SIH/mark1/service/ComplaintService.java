package com.SIH.mark1.service;

import com.SIH.mark1.ai.dto.AIResponse;
import com.SIH.mark1.ai.duplicate.DuplicateDecision;
import com.SIH.mark1.ai.duplicate.DuplicateDetectionResult;
import com.SIH.mark1.ai.qdrant.KnowledgeSyncService;
import com.SIH.mark1.ai.service.AIService;
import com.SIH.mark1.ai.service.DuplicateDetectionService;
import com.SIH.mark1.ai.service.TranslationService;
import com.SIH.mark1.dto.request.CreateComplaintRequest;
import com.SIH.mark1.dto.request.ReopenRequest;
import com.SIH.mark1.dto.request.UpdateComplaintRequest;
import com.SIH.mark1.dto.response.ComplaintDetailsResponse;
import com.SIH.mark1.dto.response.ComplaintResponse;
import com.SIH.mark1.dto.response.TimelineDTO;
import com.SIH.mark1.dto.response.VerificationStatusResponse;

import com.SIH.mark1.ivr.service.CitizenVerificationService;
import com.SIH.mark1.model.AiAnalysis;
import com.SIH.mark1.model.AssignmentStatus;
import com.SIH.mark1.model.Category;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintAssignment;
import com.SIH.mark1.model.ComplaintMedia;
import com.SIH.mark1.model.ComplaintStatusMaster;
import com.SIH.mark1.model.Department;
import com.SIH.mark1.model.DuplicateDetectionRecord;
import com.SIH.mark1.model.DuplicateReviewStatus;
import com.SIH.mark1.model.MediaType;
import com.SIH.mark1.model.NotificationType;
import com.SIH.mark1.model.PriorityMaster;
import com.SIH.mark1.model.StatusHistory;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.repository.AiAnalysisRepository;
import com.SIH.mark1.repository.CategoryRepository;
import com.SIH.mark1.repository.ComplaintAssignmentRepository;
import com.SIH.mark1.repository.ComplaintMediaRepository;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.ComplaintStatusMasterRepository;
import com.SIH.mark1.repository.DepartmentRepository;
import com.SIH.mark1.repository.DuplicateDetectionRecordRepository;
import com.SIH.mark1.repository.PriorityMasterRepository;
import com.SIH.mark1.repository.StatusHistoryRepository;
import com.SIH.mark1.repository.UserRepository;
import com.SIH.mark1.util.ComplaintNumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Citizen-facing complaint service.
 *
 * Business rules:
 *  - A citizen can only view/edit/delete their OWN complaints.
 *  - Complaints with status RESOLVED or CLOSED cannot be edited or deleted.
 *  - Complaint creation auto-assigns department and priority from the selected category.
 *  - Status history is created automatically on complaint creation.
 *  - Deletion is a soft-delete (is_deleted = true).
 */
@Service
public class ComplaintService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintService.class);

    /** Status codes that prevent further citizen edits. */
    private static final Set<String> LOCKED_STATUSES = Set.of("RESOLVED", "CLOSED");

    /** Allowed MIME types for media upload. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf", "video/mp4"
    );

    private final ComplaintRepository            complaintRepository;
    private final AiAnalysisRepository           aiAnalysisRepository;
    private final AIService                      aiService;
    private final DuplicateDetectionService      duplicateDetectionService;
    private final TranslationService             translationService;
    private final UserRepository                 userRepository;
    private final CategoryRepository             categoryRepository;
    private final DepartmentRepository           departmentRepository;
    private final ComplaintStatusMasterRepository statusMasterRepository;
    private final PriorityMasterRepository        priorityMasterRepository;
    private final StatusHistoryRepository        statusHistoryRepository;
    private final ComplaintMediaRepository       mediaRepository;
    private final ComplaintNumberGenerator       numberGenerator;
    private final NotificationService            notificationService;
    private final DuplicateDetectionRecordRepository duplicateDetectionRecordRepository;
    private final ObjectProvider<KnowledgeSyncService> knowledgeSyncServiceProvider;
    private final AutoAssignmentService           autoAssignmentService;
    private final ComplaintAssignmentRepository   assignmentRepository;
    private final SlaService                      slaService;
    private final CitizenVerificationService      citizenVerificationService;


    /** Local upload directory — override in application.properties via `app.upload.dir`. */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public ComplaintService(ComplaintRepository complaintRepository,
                            AiAnalysisRepository aiAnalysisRepository,
                            AIService aiService,
                            DuplicateDetectionService duplicateDetectionService,
                            TranslationService translationService,
                            UserRepository userRepository,
                            CategoryRepository categoryRepository,
                            DepartmentRepository departmentRepository,
                            ComplaintStatusMasterRepository statusMasterRepository,
                            PriorityMasterRepository priorityMasterRepository,
                            StatusHistoryRepository statusHistoryRepository,
                            ComplaintMediaRepository mediaRepository,
                            ComplaintNumberGenerator numberGenerator,
                            NotificationService notificationService,
                             DuplicateDetectionRecordRepository duplicateDetectionRecordRepository,
                              ObjectProvider<KnowledgeSyncService> knowledgeSyncServiceProvider,
                              AutoAssignmentService autoAssignmentService,
                              ComplaintAssignmentRepository assignmentRepository,
                              SlaService slaService,
                              CitizenVerificationService citizenVerificationService) {
        this.complaintRepository     = complaintRepository;
        this.aiAnalysisRepository    = aiAnalysisRepository;
        this.aiService               = aiService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.translationService = translationService;
        this.userRepository          = userRepository;
        this.categoryRepository      = categoryRepository;
        this.departmentRepository    = departmentRepository;
        this.statusMasterRepository  = statusMasterRepository;
        this.priorityMasterRepository = priorityMasterRepository;
        this.statusHistoryRepository  = statusHistoryRepository;
        this.mediaRepository         = mediaRepository;
        this.numberGenerator         = numberGenerator;
        this.notificationService     = notificationService;
        this.duplicateDetectionRecordRepository = duplicateDetectionRecordRepository;
        this.knowledgeSyncServiceProvider = knowledgeSyncServiceProvider;
        this.autoAssignmentService        = autoAssignmentService;
        this.assignmentRepository         = assignmentRepository;
        this.slaService                   = slaService;
        this.citizenVerificationService   = citizenVerificationService;
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a new complaint for the logged-in citizen.
     * Flow: validate → resolve category/dept/priority → generate number
     *       → save complaint → create REGISTERED status history → return response.
     *
     * @param mobile   the authenticated citizen's mobile (from JWT subject)
     * @param request  complaint creation payload
     * @return lightweight ComplaintResponse with complaint number
     */
    @Transactional
    public ComplaintResponse createComplaint(String mobile, CreateComplaintRequest request) {
        User citizen = findUserByMobile(mobile);

        // Rate limit: max 10 complaints per hour per citizen (spam/cancel-recreate loop protection)
        long recentCount = complaintRepository.countByCitizenAndCreatedAtAfter(
                citizen, LocalDateTime.now().minusHours(1));
        if (recentCount >= 10) {
            throw new IllegalStateException(
                    "Too many complaints submitted in a short time. Please try again after some time.");
        }

        // Translate complaint to English first (AI), so analysis & storage are in English
        String originalDescription = request.getDescription();
        String englishDescription = translationService.translateToEnglish(originalDescription);
        String originalTitle = request.getTitle();
        String englishTitle = translationService.translateToEnglish(originalTitle);

        // Use English text for analysis
        CreateComplaintRequest englishRequest = new CreateComplaintRequest();
        englishRequest.setTitle(englishTitle);
        englishRequest.setDescription(englishDescription);
        englishRequest.setAddress(request.getAddress());
        englishRequest.setLatitude(request.getLatitude());
        englishRequest.setLongitude(request.getLongitude());
        englishRequest.setLanguage("en");

        AIResponse aiResponse = analyzeSafely(englishRequest);
        request = englishRequest;
        DuplicateDetectionResult duplicateResult = checkDuplicateSafely(request, aiResponse);
        if (duplicateResult.decision() == DuplicateDecision.DUPLICATE) {
            return ComplaintResponse.builder()
                    .complaintId(duplicateResult.existingComplaintId())
                    .complaintNumber(duplicateResult.existingComplaintNumber())
                    .status("DUPLICATE")
                    .message("A similar complaint is already being processed.")
                    .duplicateDecision(duplicateResult.decision().name())
                    .similarity(duplicateResult.similarity())
                    .reasons(duplicateResult.reasons())
                    .existingComplaintNumber(duplicateResult.existingComplaintNumber())
                    .existingStatus(duplicateResult.existingStatus())
                    .scope(duplicateResult.scope() != null ? duplicateResult.scope().name() : null)
                    .resourceMatch(duplicateResult.resourceMatch())
                    .locationMatch(duplicateResult.locationMatch())
                    .build();
        }

        Category category = resolveCategory(request);

        PriorityMaster priority = category.getDefaultPriority();
        if (priority == null) {
            // Fallback: pick the lowest display-order active priority from master table
            priority = priorityMasterRepository.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getActive()))
                    .min(java.util.Comparator.comparingInt(PriorityMaster::getDisplayOrder))
                    .orElse(null);
        }

        ComplaintStatusMaster registeredStatus = statusMasterRepository
                .findByStatusCode("REGISTERED")
                .orElseThrow(() -> new IllegalStateException(
                        "REGISTERED status not found in complaint_status table. Please seed master data."));

        String complaintNo = numberGenerator.next();

        String resolvedTitle = request.getTitle();
        if (resolvedTitle == null || resolvedTitle.trim().isEmpty()) {
            String desc = request.getDescription();
            if (desc != null && desc.length() > 50) {
                resolvedTitle = desc.substring(0, 47) + "...";
            } else if (desc != null) {
                resolvedTitle = desc;
            } else {
                resolvedTitle = "Untitled Grievance";
            }
        }

        Complaint complaint = Complaint.builder()
                .complaintNo(complaintNo)
                .citizen(citizen)
                .department(category.getDepartment())
                .category(category)
                .priority(priority)
                .currentStatus(registeredStatus)
                .title(resolvedTitle)
                .description(request.getDescription())
                .locationAddress(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        complaint.setCreatedBy(citizen.getUserId());
        complaint.setUpdatedBy(citizen.getUserId());
        complaint.setDeleted(false);

        // Start the SLA clock at creation time (priority-based deadline)
        slaService.applyInitialSla(complaint);

        complaint = complaintRepository.save(complaint);

        // Create initial status history entry
        StatusHistory history = StatusHistory.builder()
                .complaint(complaint)
                .status(registeredStatus)
                .remarks("Complaint registered successfully")
                .changedBy(citizen)
                .build();
        history.setCreatedBy(citizen.getUserId());
        statusHistoryRepository.save(history);

        if (saveAiAnalysis(complaint, request, citizen.getUserId(), aiResponse)) {
            markAiAnalyzed(complaint, citizen);
        }

        // Citizen call-back verification (anti-spam gate): rings the citizen to confirm they
        // really lodged this complaint. Must run BEFORE auto-assignment, which consults the
        // resulting actionAllowed flag — reversing the order would let an unverified
        // complaint reach an officer.
        try {
            citizenVerificationService.startVerification(complaint);
        } catch (Exception ex) {
            // Telephony problems must never fail registration; the retry sweep picks it up.
            log.warn("Verification start failed for complaint id={}: {}",
                    complaint.getComplaintId(), ex.getMessage());
        }

        // AI auto-assignment: when the admin has switched AI mode ON, the engine
        // immediately assigns the best officer of the detected department and
        // notifies admins. No-op in manual mode.
        //
        // Skipped while verification is pending so an unverified complaint never lands in an
        // officer's queue; the verification flow re-triggers assignment once confirmed.
        if (citizenVerificationService.isActionAllowed(complaint)) {
            try {
                autoAssignmentService.autoAssignIfEnabled(complaint);
            } catch (Exception ex) {
                log.warn("Auto-assignment trigger failed for complaint id={}: {}",
                        complaint.getComplaintId(), ex.getMessage());
            }
        } else {
            log.info("Auto-assignment deferred for complaint id={}: {}",
                    complaint.getComplaintId(), citizenVerificationService.blockedReason(complaint));
        }


        // Notify citizen of successful registration
        notificationService.sendNotification(
                citizen, complaint,
                NotificationType.COMPLAINT_REGISTERED,
                "Complaint Registered",
                "Your complaint " + complaintNo + " has been successfully registered. Title: " + resolvedTitle);

        // Persist possible-duplicate results for the admin review queue.
        saveDuplicateDetectionRecord(complaint, duplicateResult, citizen.getUserId());

        // Sync complaint vector to Qdrant (for duplicate detection). MySQL remains source of truth.
        KnowledgeSyncService syncService = knowledgeSyncServiceProvider.getIfAvailable();
        if (syncService != null) {
            try {
                syncService.syncComplaint(complaint, aiResponse);
            } catch (Exception ex) {
                log.warn("Qdrant complaint sync failed for id={}: {}", complaint.getComplaintId(), ex.getMessage());
            }
        }

        return ComplaintResponse.builder()
                .complaintId(complaint.getComplaintId())
                .complaintNumber(complaintNo)
                .status(complaint.getCurrentStatus().getStatusCode())
                .message(responseMessage(complaintNo, duplicateResult))
                .duplicateDecision(duplicateResult.decision().name())
                .similarity(duplicateResult.similarity() > 0 ? duplicateResult.similarity() : null)
                .reasons(duplicateResult.reasons())
                .existingComplaintNumber(duplicateResult.existingComplaintNumber())
                .existingStatus(duplicateResult.existingStatus())
                .scope(duplicateResult.scope() != null ? duplicateResult.scope().name() : null)
                .resourceMatch(duplicateResult.resourceMatch())
                .locationMatch(duplicateResult.locationMatch())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // READ — single complaint
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns full details for one complaint.
     * Ownership enforced: throws AccessDeniedException if not citizen's own complaint.
     */
    @Transactional(readOnly = true)
    public ComplaintDetailsResponse getComplaintById(String mobile, Long complaintId) {
        User citizen   = findUserByMobile(mobile);
        Complaint complaint = findOwnedComplaint(citizen, complaintId);
        return toDetailsResponse(complaint);
    }

    // ─────────────────────────────────────────────────────────────
    // READ — my complaints
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns all non-deleted complaints belonging to the logged-in citizen.
     */
    @Transactional(readOnly = true)
    public List<ComplaintResponse> getMyComplaints(String mobile) {
        User citizen = findUserByMobile(mobile);
        return complaintRepository.findByCitizenAndDeletedFalse(citizen)
                .stream()
                .map(c -> ComplaintResponse.builder()
                        .complaintId(c.getComplaintId())
                        .complaintNumber(c.getComplaintNo())
                        .status(c.getCurrentStatus().getStatusCode())
                        .message(c.getTitle())
                        .title(c.getTitle())
                        .category(c.getCategory() != null ? c.getCategory().getCategoryName() : null)
                        .createdAt(c.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────

    /**
     * Updates title, description and/or address of a complaint.
     * Blocked if status is RESOLVED or CLOSED.
     */
    @Transactional
    public ComplaintDetailsResponse updateComplaint(String mobile, Long complaintId,
                                                   UpdateComplaintRequest request) {
        User citizen   = findUserByMobile(mobile);
        Complaint complaint = findOwnedComplaint(citizen, complaintId);

        assertNotLocked(complaint);

        if (request.getTitle() != null)       complaint.setTitle(request.getTitle());
        if (request.getDescription() != null) complaint.setDescription(request.getDescription());
        if (request.getAddress() != null)     complaint.setLocationAddress(request.getAddress());
        if (request.getLatitude() != null)    complaint.setLatitude(request.getLatitude());
        if (request.getLongitude() != null)   complaint.setLongitude(request.getLongitude());

        complaint.setUpdatedBy(citizen.getUserId());
        complaint = complaintRepository.save(complaint);

        return toDetailsResponse(complaint);
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE (soft)
    // ─────────────────────────────────────────────────────────────

    /**
     * Soft-deletes the complaint (sets is_deleted = true, deleted_at = now).
     * Blocked if status is RESOLVED or CLOSED.
     *
     * Cancellation rules & side-effects:
     *  - NOT allowed once the officer has ACCEPTED / started work (IN_PROGRESS) — citizen
     *    must contact the admin instead (protects in-flight work).
     *  - Complaint status moves to CANCELLED + a status-history entry is recorded (audit trail).
     *  - Any active assignment is marked CANCELLED (no dangling rows; keeps AI stats correct).
     *  - The assigned officer and all admins are notified.
     */
    @Transactional
    public void deleteComplaint(String mobile, Long complaintId) {
        User citizen   = findUserByMobile(mobile);
        Complaint complaint = findOwnedComplaint(citizen, complaintId);

        assertNotLocked(complaint);

        // Block cancellation once the officer has started working on it
        ComplaintAssignment assignment = assignmentRepository.findByComplaint(complaint).orElse(null);
        if (assignment != null && (assignment.getAssignmentStatus() == AssignmentStatus.ACCEPTED
                || assignment.getAssignmentStatus() == AssignmentStatus.IN_PROGRESS)) {
            throw new IllegalStateException(
                    "Officer has already started working on this complaint. Please contact the admin to cancel it.");
        }

        complaint.setDeleted(true);
        complaint.setDeletedAt(LocalDateTime.now());
        complaint.setUpdatedBy(citizen.getUserId());

        // Move status to CANCELLED + record status history (audit trail)
        statusMasterRepository.findByStatusCode("CANCELLED").ifPresent(cancelledStatus -> {
            complaint.setCurrentStatus(cancelledStatus);
            StatusHistory history = StatusHistory.builder()
                    .complaint(complaint)
                    .status(cancelledStatus)
                    .remarks("Cancelled by citizen")
                    .changedBy(citizen)
                    .build();
            history.setCreatedBy(citizen.getUserId());
            statusHistoryRepository.save(history);
        });
        complaintRepository.save(complaint);

        // Cancel the active assignment (if any) and alert the assigned officer
        if (assignment != null
                && assignment.getAssignmentStatus() != AssignmentStatus.CANCELLED
                && assignment.getAssignmentStatus() != AssignmentStatus.COMPLETED) {
            assignment.setAssignmentStatus(AssignmentStatus.CANCELLED);
            assignmentRepository.save(assignment);

            User officer = assignment.getOfficer();
            if (officer != null) {
                notificationService.sendNotification(
                        officer, complaint,
                        NotificationType.COMPLAINT_CANCELLED,
                        "Complaint Cancelled by Citizen",
                        "Complaint " + complaint.getComplaintNo() + " (\"" + complaint.getTitle()
                                + "\") assigned to you has been cancelled by the citizen. No action is needed.");
            }
        }

        // Notify all admins (also covers complaints that were never assigned)
        userRepository.findByRoleAndDeletedFalse(UserRole.ADMIN).forEach(admin ->
                notificationService.sendNotification(
                        admin, complaint,
                        NotificationType.COMPLAINT_CANCELLED,
                        "Complaint Cancelled by Citizen",
                        "Citizen " + citizen.getName() + " (" + mobile + ") cancelled complaint "
                                + complaint.getComplaintNo() + " (\"" + complaint.getTitle() + "\")."));
    }

    // ─────────────────────────────────────────────────────────────
    // MEDIA UPLOAD
    // ─────────────────────────────────────────────────────────────

    /**
     * Uploads a media file and persists a ComplaintMedia record.
     * Supported: JPG, PNG, PDF, MP4.
     * Files are stored under: {uploadDir}/{complaintId}/{original-filename}
     */
    @Transactional
    public ComplaintResponse uploadMedia(String mobile, Long complaintId, MultipartFile file) {
        User citizen   = findUserByMobile(mobile);
        Complaint complaint = findOwnedComplaint(citizen, complaintId);

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Allowed: JPG, PNG, PDF, MP4");
        }

        // Derive enum from MIME type
        MediaType mediaType = contentType.startsWith("video/") ? MediaType.VIDEO : MediaType.IMAGE;

        // Persist file locally
        String relativePath = saveFileToDisk(complaintId, file);

        ComplaintMedia media = ComplaintMedia.builder()
                .complaint(complaint)
                .mediaType(mediaType)
                .fileUrl(relativePath)
                .fileName(file.getOriginalFilename())
                .uploadedAt(LocalDateTime.now())
                .build();
        media.setCreatedBy(citizen.getUserId());
        media.setDeleted(false);
        mediaRepository.save(media);

        return ComplaintResponse.builder()
                .complaintId(complaint.getComplaintId())
                .complaintNumber(complaint.getComplaintNo())
                .status(complaint.getCurrentStatus().getStatusCode())
                .message("Media uploaded successfully")
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // TIMELINE
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the full status timeline for a complaint, ordered oldest → newest.
     */
    @Transactional(readOnly = true)
    public List<TimelineDTO> getTimeline(String mobile, Long complaintId) {
        User citizen   = findUserByMobile(mobile);
        Complaint complaint = findOwnedComplaint(citizen, complaintId);

        // Repository returns descending; reverse to get ascending (oldest first)
        List<StatusHistory> history =
                statusHistoryRepository.findByComplaintOrderByCreatedAtDesc(complaint);

        return history.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(h -> TimelineDTO.builder()
                        .status(h.getStatus().getStatusCode())
                        .time(h.getCreatedAt())
                        .remarks(h.getRemarks())
                        .build())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // P4 — CLOSE & REOPEN WORKFLOW
    // ─────────────────────────────────────────────────────────────

    /**
     * Citizen marks a RESOLVED complaint as CLOSED (satisfied with resolution).
     */
    @Transactional
    public ComplaintDetailsResponse closeComplaint(String mobile, Long complaintId) {
        User citizen = findUserByMobile(mobile);
        Complaint complaint = findOwnedComplaint(citizen, complaintId);

        String currentCode = complaint.getCurrentStatus() != null ? complaint.getCurrentStatus().getStatusCode() : "";
        if (!"RESOLVED".equalsIgnoreCase(currentCode)) {
            throw new IllegalStateException("Only RESOLVED complaints can be closed. Current status: " + currentCode);
        }

        ComplaintStatusMaster closedStatus = statusMasterRepository.findByStatusCode("CLOSED")
                .orElseThrow(() -> new IllegalStateException("CLOSED status master missing"));

        complaint.setCurrentStatus(closedStatus);
        complaint.setUpdatedBy(citizen.getUserId());
        final Complaint savedComplaint = complaintRepository.save(complaint);

        StatusHistory history = StatusHistory.builder()
                .complaint(savedComplaint)
                .status(closedStatus)
                .remarks("Closed by citizen")
                .changedBy(citizen)
                .build();
        history.setCreatedBy(citizen.getUserId());
        statusHistoryRepository.save(history);

        notificationService.sendNotification(
                citizen, savedComplaint,
                NotificationType.STATUS_UPDATED,
                "Complaint Closed",
                "Your complaint " + savedComplaint.getComplaintNo() + " is now CLOSED. Thank you for your feedback!");

        return toDetailsResponse(savedComplaint);
    }

    /**
     * Citizen reopens a RESOLVED complaint (unsatisfied with resolution).
     * Transitions status to REOPENED and notifies officer and admins.
     */
    @Transactional
    public ComplaintDetailsResponse reopenComplaint(String mobile, Long complaintId, ReopenRequest request) {
        User citizen = findUserByMobile(mobile);
        Complaint complaint = findOwnedComplaint(citizen, complaintId);

        String currentCode = complaint.getCurrentStatus() != null ? complaint.getCurrentStatus().getStatusCode() : "";
        if (!"RESOLVED".equalsIgnoreCase(currentCode)) {
            throw new IllegalStateException("Only RESOLVED complaints can be reopened. Current status: " + currentCode);
        }

        ComplaintStatusMaster reopenedStatus = statusMasterRepository.findByStatusCode("REOPENED")
                .orElseGet(() -> statusMasterRepository.findByStatusCode("IN_PROGRESS")
                        .orElseThrow(() -> new IllegalStateException("REOPENED status master missing")));

        complaint.setCurrentStatus(reopenedStatus);
        complaint.setResolvedAt(null); // Reset resolution date
        // Restart the SLA clock from the reopen moment — the officer gets a fresh
        // window, and the historic slaBreachedAt (if any) is intentionally preserved.
        complaint.setSlaDueAt(slaService.calculateDueAt(complaint.getPriority(), LocalDateTime.now()));
        complaint.setSlaStatus(com.SIH.mark1.model.SlaStatus.ON_TRACK);
        complaint.setSlaReminderSentAt(null);
        complaint.setUpdatedBy(citizen.getUserId());
        final Complaint savedComplaint = complaintRepository.save(complaint);

        StatusHistory history = StatusHistory.builder()
                .complaint(savedComplaint)
                .status(reopenedStatus)
                .remarks("Reopened by citizen. Reason: " + request.getReason())
                .changedBy(citizen)
                .build();
        history.setCreatedBy(citizen.getUserId());
        statusHistoryRepository.save(history);

        // Reactivate the assignment so the complaint reappears as actionable in the
        // officer's queue (resolve had marked it COMPLETED).
        assignmentRepository.findByComplaint(savedComplaint).ifPresent(assignment -> {
            assignment.setAssignmentStatus(AssignmentStatus.ACCEPTED);
            assignment.setUpdatedBy(citizen.getUserId());
            assignmentRepository.save(assignment);
        });

        // Notify assigned officer if present
        if (savedComplaint.getOfficer() != null) {
            notificationService.sendNotification(
                    savedComplaint.getOfficer(), savedComplaint,
                    NotificationType.STATUS_UPDATED,
                    "Complaint Reopened",
                    "Complaint " + savedComplaint.getComplaintNo() + " was reopened by citizen. Reason: " + request.getReason());
        }

        // Notify admins
        userRepository.findByRole(com.SIH.mark1.model.UserRole.ADMIN).forEach(admin ->
                notificationService.sendNotification(
                        admin, savedComplaint,
                        NotificationType.STATUS_UPDATED,
                        "Complaint Reopened",
                        "Complaint " + savedComplaint.getComplaintNo() + " was reopened by citizen. Reason: " + request.getReason()));

        return toDetailsResponse(savedComplaint);
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    private Category resolveCategory(CreateComplaintRequest request) {
        List<Category> activeCategories = categoryRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .toList();

        if (activeCategories.isEmpty()) {
            throw new IllegalArgumentException("No active categories found in database to associate with the complaint.");
        }

        String complaintText = ((request.getTitle() == null ? "" : request.getTitle()) + " "
                + (request.getDescription() == null ? "" : request.getDescription()))
                .toLowerCase(Locale.ROOT);

        return activeCategories.stream()
                .max(java.util.Comparator.comparingInt(category -> categoryMatchScore(category, complaintText)))
                .orElse(activeCategories.getFirst());
    }

    private int categoryMatchScore(Category category, String complaintText) {
        int score = 0;
        score += phraseScore(category.getCategoryName(), complaintText, 10);
        score += phraseScore(category.getDescription(), complaintText, 4);
        if (category.getDepartment() != null) {
            score += phraseScore(category.getDepartment().getDepartmentName(), complaintText, 2);
            score += phraseScore(category.getDepartment().getDescription(), complaintText, 1);
        }
        return score;
    }

    private int phraseScore(String phrase, String complaintText, int weight) {
        if (phrase == null || phrase.isBlank()) {
            return 0;
        }
        int score = 0;
        for (String token : phrase.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (token.length() > 2 && complaintText.contains(token)) {
                score += weight;
            }
        }
        return score;
    }

    private AIResponse analyzeSafely(CreateComplaintRequest request) {
        try {
            return aiService.analyze(request);
        } catch (Exception ex) {
            return new AIResponse("Complaint Analysis", request.getDescription(), "UNKNOWN", "UNKNOWN", "MEDIUM", 0);
        }
    }

    private DuplicateDetectionResult checkDuplicateSafely(CreateComplaintRequest request, AIResponse aiResponse) {
        try {
            return duplicateDetectionService.check(request, aiResponse);
        } catch (Exception ex) {
            return DuplicateDetectionResult.unavailable("Duplicate detection unavailable: " + ex.getMessage());
        }
    }

    /**
     * Persists a POSSIBLE_DUPLICATE / POSSIBLE_RECURRING_ISSUE detection result so
     * admins can review it in the duplicate review queue.
     */
    private void saveDuplicateDetectionRecord(Complaint complaint,
                                              DuplicateDetectionResult duplicateResult,
                                              Long userId) {
        if (duplicateResult.decision() != DuplicateDecision.POSSIBLE_DUPLICATE
                && duplicateResult.decision() != DuplicateDecision.POSSIBLE_RECURRING_ISSUE) {
            return;
        }
        if (duplicateResult.existingComplaintId() == null) {
            return;
        }
        try {
            Complaint matched = complaintRepository.findById(duplicateResult.existingComplaintId())
                    .orElse(null);
            DuplicateDetectionRecord record = DuplicateDetectionRecord.builder()
                    .complaint(complaint)
                    .matchedComplaint(matched)
                    .similarity(duplicateResult.similarity())
                    .decision(duplicateResult.decision().name())
                    .scope(duplicateResult.scope() != null ? duplicateResult.scope().name() : null)
                    .resourceMatch(duplicateResult.resourceMatch())
                    .locationMatch(duplicateResult.locationMatch())
                    .reasons(duplicateResult.reasons() != null
                            ? String.join("\n", duplicateResult.reasons()) : null)
                    .reviewStatus(DuplicateReviewStatus.PENDING)
                    .build();
            record.setCreatedBy(userId);
            duplicateDetectionRecordRepository.save(record);
        } catch (Exception ex) {
            // Persisting the review record must never break complaint registration.
            log.warn("Failed to persist duplicate detection record for complaint id={}: {}",
                    complaint.getComplaintId(), ex.getMessage());
        }
    }

    private String responseMessage(String complaintNo, DuplicateDetectionResult duplicateResult) {
        if (duplicateResult.decision() == DuplicateDecision.POSSIBLE_DUPLICATE
                || duplicateResult.decision() == DuplicateDecision.POSSIBLE_RECURRING_ISSUE) {
            return "Complaint registered successfully. Your complaint number is " + complaintNo
                    + ". Note: similar complaint found for review ("
                    + duplicateResult.existingComplaintNumber() + ").";
        }
        return "Complaint registered successfully. Your complaint number is " + complaintNo;
    }

    private boolean saveAiAnalysis(Complaint complaint, CreateComplaintRequest request, Long userId, AIResponse response) {
        try {
            Category detectedCategory = resolveDetectedCategory(response.category(), complaint.getCategory());
            Department detectedDepartment = resolveDetectedDepartment(response.department(), detectedCategory, complaint.getDepartment());
            PriorityMaster detectedPriority = resolveDetectedPriority(response.priority(), complaint.getPriority());

            AiAnalysis analysis = aiAnalysisRepository.findByComplaint(complaint)
                    .orElseGet(() -> AiAnalysis.builder().complaint(complaint).build());
            analysis.setDetectedDepartment(detectedDepartment);
            analysis.setDetectedCategory(detectedCategory);
            analysis.setDetectedPriority(detectedPriority);
            analysis.setConfidenceScore(BigDecimal.valueOf(response.confidence()));
            analysis.setPriorityScore(priorityScore(response.priority()));
            analysis.setModelName(aiService.modelName());
            analysis.setAiSummary(response.summary());
            analysis.setUpdatedBy(userId);
            if (analysis.getAnalysisId() == null) {
                analysis.setCreatedBy(userId);
            }
            aiAnalysisRepository.save(analysis);

            complaint.setCategory(detectedCategory);
            complaint.setDepartment(detectedDepartment);
            complaint.setPriority(detectedPriority);
            complaint.setUpdatedBy(userId);
            complaintRepository.save(complaint);
            return true;
        } catch (Exception ignored) {
            // Complaint registration should not fail because AI analysis is unavailable.
            return false;
        }
    }

    private void markAiAnalyzed(Complaint complaint, User citizen) {
        ComplaintStatusMaster aiAnalyzedStatus = statusMasterRepository.findByStatusCode("AI_ANALYZED")
                .orElseThrow(() -> new IllegalStateException(
                        "AI_ANALYZED status not found in complaint_status table. Please seed master data."));
        complaint.setCurrentStatus(aiAnalyzedStatus);
        complaint.setUpdatedBy(citizen.getUserId());
        complaintRepository.save(complaint);

        StatusHistory history = StatusHistory.builder()
                .complaint(complaint)
                .status(aiAnalyzedStatus)
                .remarks("AI analysis completed and ready for admin assignment")
                .changedBy(citizen)
                .build();
        history.setCreatedBy(citizen.getUserId());
        statusHistoryRepository.save(history);
    }

    private Category resolveDetectedCategory(String categoryName, Category fallback) {
        if (categoryName != null && !categoryName.isBlank() && !"UNKNOWN".equalsIgnoreCase(categoryName)) {
            String normalized = categoryName.trim();
            return categoryRepository.findAll().stream()
                    .filter(category -> category.getCategoryName() != null)
                    .filter(category -> category.getCategoryName().equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElse(fallback);
        }
        return fallback;
    }

    private Department resolveDetectedDepartment(String departmentName, Category detectedCategory, Department fallback) {
        if (departmentName != null && !departmentName.isBlank() && !"UNKNOWN".equalsIgnoreCase(departmentName)) {
            String normalized = departmentName.trim();
            return departmentRepository.findAll().stream()
                    .filter(department -> department.getDepartmentName() != null)
                    .filter(department -> department.getDepartmentName().equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElseGet(() -> detectedCategory != null ? detectedCategory.getDepartment() : fallback);
        }
        if (detectedCategory != null && detectedCategory.getDepartment() != null) {
            return detectedCategory.getDepartment();
        }
        return fallback;
    }

    private PriorityMaster resolveDetectedPriority(String priorityCode, PriorityMaster fallback) {
        if (priorityCode != null && !priorityCode.isBlank()) {
            String normalized = priorityCode.trim();
            return priorityMasterRepository.findAll().stream()
                    .filter(priority -> priority.getPriorityCode() != null)
                    .filter(priority -> priority.getPriorityCode().equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElse(fallback);
        }
        return fallback;
    }

    private BigDecimal priorityScore(String priority) {
        if (priority == null) {
            return BigDecimal.valueOf(50);
        }
        return switch (priority.toUpperCase(Locale.ROOT)) {
            case "HIGH" -> BigDecimal.valueOf(90);
            case "LOW" -> BigDecimal.valueOf(30);
            default -> BigDecimal.valueOf(50);
        };
    }

    private User findUserByMobile(String mobile) {
        return userRepository.findByMobile(mobile)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + mobile));
    }

    /**
     * Finds a non-deleted complaint by ID and verifies it belongs to the citizen.
     */
    private Complaint findOwnedComplaint(User citizen, Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        if (Boolean.TRUE.equals(complaint.getDeleted())) {
            throw new IllegalArgumentException("Complaint not found: " + complaintId);
        }

        if (!complaint.getCitizen().getUserId().equals(citizen.getUserId())) {
            throw new AccessDeniedException("You do not have permission to access this complaint");
        }

        return complaint;
    }

    /** Throws if the complaint is in a terminal status (RESOLVED / CLOSED). */
    private void assertNotLocked(Complaint complaint) {
        String statusCode = complaint.getCurrentStatus().getStatusCode();
        if (LOCKED_STATUSES.contains(statusCode)) {
            throw new IllegalStateException(
                    "Complaint cannot be modified in status: " + statusCode);
        }
    }

    /** Saves the uploaded file to local disk and returns the relative path. */
    private String saveFileToDisk(Long complaintId, MultipartFile file) {
        try {
            Path dir = Paths.get(uploadDir, complaintId.toString());
            Files.createDirectories(dir);

            String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path destination = dir.resolve(filename);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            // Store path relative to uploadDir — served as /media/{complaintId}/{filename}
            return complaintId + "/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store media file: " + e.getMessage(), e);
        }
    }

    /** Maps a Complaint entity to the full details response DTO. */
    private ComplaintDetailsResponse toDetailsResponse(Complaint c) {
        return ComplaintDetailsResponse.builder()
                .complaintNumber(c.getComplaintNo())
                .title(c.getTitle())
                .description(c.getDescription())
                .department(c.getDepartment() != null ? c.getDepartment().getDepartmentName() : null)
                .category(c.getCategory() != null ? c.getCategory().getCategoryName() : null)
                .priority(c.getPriority() != null ? c.getPriority().getPriorityCode() : null)
                .status(c.getCurrentStatus() != null ? c.getCurrentStatus().getStatusCode() : null)
                .officer(c.getOfficer() != null ? c.getOfficer().getName() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .address(c.getLocationAddress())
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .mediaUrls(mediaRepository.findByComplaint(c).stream()
                        .map(ComplaintMedia::getFileUrl)
                        .map(this::toMediaUrl)
                        .toList())
                .slaDueAt(c.getSlaDueAt())
                .slaStatus(c.getSlaStatus())
                .slaBreachedAt(c.getSlaBreachedAt())
                .verificationStatus(c.getVerificationStatus())
                .actionAllowed(citizenVerificationService.isActionAllowed(c))
                // Null when action is allowed — otherwise every healthy complaint would carry
                // an "on hold" string the app might render.
                .verificationBlockedReason(citizenVerificationService.blockedReasonOrNull(c))
                .build();

    }

    // ─────────────────────────────────────────────────────────────
    // Citizen call-back verification
    // ─────────────────────────────────────────────────────────────

    /**
     * Current verification state of the citizen's own complaint.
     *
     * <p>Routed through {@link #findOwnedComplaint} rather than reading the complaint directly
     * so the ownership check applies: verification detail says how many times we called a
     * citizen and when we will call again, which is not another user's business.</p>
     */
    @Transactional(readOnly = true)
    public VerificationStatusResponse getVerificationStatus(String mobile, Long complaintId) {
        User citizen = findUserByMobile(mobile);
        Complaint complaint = findOwnedComplaint(citizen, complaintId);
        return citizenVerificationService.describe(complaint);
    }

    /**
     * Citizen confirms in the app that they did lodge this complaint.
     *
     * <p>The escape hatch for someone who opens the app before our call-back reaches them —
     * without it a citizen on a poor phone line would be stuck waiting on the retry
     * schedule while their SLA clock, which starts at creation, keeps running.</p>
     *
     * <p>Ownership is enforced by {@link #findOwnedComplaint}: allowing anyone to confirm
     * anyone's complaint would defeat the entire anti-fake-complaint control, since a
     * spammer could simply confirm their own fake lodgements from another account.</p>
     *
     * @throws IllegalStateException when the citizen already denied it on the verification call
     */
    @Transactional
    public VerificationStatusResponse confirmVerification(String mobile, Long complaintId) {
        User citizen = findUserByMobile(mobile);
        Complaint complaint = findOwnedComplaint(citizen, complaintId);

        // Idempotent inside confirmFromApp: a double tap re-reads the state instead of
        // re-confirming, so attempt counts and notifications cannot be duplicated.
        citizenVerificationService.confirmFromApp(complaint);
        return citizenVerificationService.describe(complaint);
    }


    /**
     * Converts a stored relative path (e.g. "8/photo.jpg") into the public
     * web URL served by WebConfig: /media/8/photo.jpg
     */
    private String toMediaUrl(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return storedPath;
        }
        String s = storedPath.replace('\\', '/');
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("/media/")) {
            return s;
        }
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        return "/media/" + s;
    }
}
