package com.SIH.mark1.repository;

import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintStatusMaster;
import com.SIH.mark1.model.Department;
import com.SIH.mark1.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    /** Used by ComplaintNumberGenerator to seed the in-memory counter on startup/year-rollover. */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(c.complaintNo, 10) AS long)), 0) " +
           "FROM Complaint c " +
           "WHERE c.complaintNo LIKE CONCAT('GRV-', :year, '-%')")
    long findMaxComplaintSuffixForYear(@Param("year") int year);

    Optional<Complaint> findByComplaintNo(String complaintNo);

    Optional<Complaint> findByComplaintNoAndDeletedFalse(String complaintNo);

    /** Citizen's own complaints (excluding soft-deleted). */
    List<Complaint> findByCitizenAndDeletedFalse(User citizen);

    Optional<Complaint> findFirstByCitizenAndDeletedFalseOrderByCreatedAtDesc(User citizen);

    Optional<Complaint> findByComplaintNoAndCitizenAndDeletedFalse(String complaintNo, User citizen);

    // ─────────────────────────────────────────────────────────────
    // Strict Repository Method Name Alignment with user prompt
    // ─────────────────────────────────────────────────────────────

    @Query("SELECT c FROM Complaint c WHERE c.complaintNo = :complaintNumber AND c.deleted = false")
    Optional<Complaint> findByComplaintNumber(@Param("complaintNumber") String complaintNumber);

    @Query("SELECT c FROM Complaint c WHERE c.citizen = :citizen AND c.deleted = false")
    List<Complaint> findByCitizen(@Param("citizen") User citizen);

    @Query("SELECT c FROM Complaint c WHERE c.currentStatus = :status AND c.deleted = false")
    List<Complaint> findByStatus(@Param("status") ComplaintStatusMaster status);

    /** Used by admin/officer filtering. */
    List<Complaint> findByDepartment(Department department);

    List<Complaint> findByOfficer(User officer);

    List<Complaint> findByCurrentStatusStatusCodeAndDeletedFalseOrderByCreatedAtDesc(String statusCode);

    /** All active complaints — used for Qdrant complaint_history reindex. */
    List<Complaint> findByDeletedFalse();

    /** Paginated variants for the admin panel. */
    Page<Complaint> findByDeletedFalse(Pageable pageable);

    Page<Complaint> findByCurrentStatusStatusCodeAndDeletedFalse(String statusCode, Pageable pageable);

    // ─────────────────────────────────────────────────────────────
    // Aggregate queries for admin dashboard / analytics / reports
    // ─────────────────────────────────────────────────────────────

    long countByDeletedFalse();

    /** Rate-limit support: complaints created by a citizen after a point in time. */
    long countByCitizenAndCreatedAtAfter(User citizen, java.time.LocalDateTime after);

    long countByResolvedAtIsNotNullAndDeletedFalse();

    long countByCurrentStatusStatusCodeAndDeletedFalse(String statusCode);

    List<Complaint> findTop10ByDeletedFalseOrderByCreatedAtDesc();

    @Query("SELECT c.currentStatus.statusCode, COUNT(c) FROM Complaint c " +
           "WHERE c.deleted = false AND c.currentStatus IS NOT NULL " +
           "GROUP BY c.currentStatus.statusCode")
    List<Object[]> countComplaintsByStatus();

    @Query("SELECT c.department.departmentName, COUNT(c) FROM Complaint c " +
           "WHERE c.deleted = false AND c.department IS NOT NULL " +
           "GROUP BY c.department.departmentName")
    List<Object[]> countComplaintsByDepartment();

    @Query("SELECT c.priority.priorityCode, COUNT(c) FROM Complaint c " +
           "WHERE c.deleted = false AND c.priority IS NOT NULL " +
           "GROUP BY c.priority.priorityCode")
    List<Object[]> countComplaintsByPriority();

    // ─────────────────────────────────────────────────────────────
    // SLA breach scanning
    // ─────────────────────────────────────────────────────────────

    /**
     * Open (non-deleted, non-terminal) complaints for the SLA scheduler pass.
     *
     * <p>Deliberately does NOT filter on {@code slaDueAt IS NOT NULL} — rows created
     * before the SLA feature shipped have a null deadline and must be back-filled
     * from {@code createdAt + priority SLA hours} during the first scan.</p>
     *
     * @param terminalComplaintStatuses status codes that stop the SLA clock
     *                                  (RESOLVED / CLOSED / CANCELLED)
     * @param terminalSlaStatuses       SLA statuses already finalised
     *                                  (RESOLVED_WITHIN_SLA / RESOLVED_AFTER_SLA)
     */
    @Query("SELECT c FROM Complaint c " +
           "WHERE c.deleted = false " +
           "AND (c.currentStatus IS NULL OR c.currentStatus.statusCode NOT IN :terminalComplaintStatuses) " +
           "AND (c.slaStatus IS NULL OR c.slaStatus NOT IN :terminalSlaStatuses)")
    List<Complaint> findOpenComplaintsForSlaScan(
            @Param("terminalComplaintStatuses") Collection<String> terminalComplaintStatuses,
            @Param("terminalSlaStatuses") Collection<String> terminalSlaStatuses);

    /** SLA dashboard/report support — count complaints in a given SLA status. */
    long countBySlaStatusAndDeletedFalse(String slaStatus);

    /**
     * Complaints whose verification call-back is due, oldest first.
     *
     * <p>Drives the retry sweep. Only rows still in PENDING with a due
     * {@code verificationNextAttemptAt} are returned, so terminal states are never
     * redialled and an attempt that has not yet come due is left alone.</p>
     */
    @Query("SELECT c FROM Complaint c " +
           "WHERE c.deleted = false " +
           "AND c.verificationStatus = :pendingStatus " +
           "AND c.verificationNextAttemptAt IS NOT NULL " +
           "AND c.verificationNextAttemptAt <= :now " +
           "ORDER BY c.verificationNextAttemptAt ASC")
    List<Complaint> findVerificationCallsDue(@Param("pendingStatus") String pendingStatus,
                                             @Param("now") java.time.LocalDateTime now);

    /** Admin review queue: complaints the citizen denied lodging. */
    List<Complaint> findByVerificationStatusAndDeletedFalseOrderByCreatedAtDesc(String verificationStatus);

    /** Verification dashboard counters. */
    long countByVerificationStatusAndDeletedFalse(String verificationStatus);

    /**
     * Admin review queue, keyed on the flag rather than the status.
     *
     * <p>An admin override clears {@code verificationFlagged} but deliberately leaves
     * {@code verificationStatus = REJECTED} standing as the historical record of what the
     * citizen actually said. Querying by status would therefore return reviewed complaints
     * forever; the flag is what "still needs admin attention" means.</p>
     */
    List<Complaint> findByVerificationFlaggedTrueAndDeletedFalseOrderByCreatedAtDesc();

    /** Size of the outstanding verification review queue. */
    long countByVerificationFlaggedTrueAndDeletedFalse();

    /** Complaints the department may currently act on (gate open). */
    long countByActionAllowedTrueAndDeletedFalse();
}





