package com.SIH.mark1.repository;

import com.SIH.mark1.model.AssignedByType;
import com.SIH.mark1.model.AssignmentStatus;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintAssignment;
import com.SIH.mark1.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintAssignmentRepository extends JpaRepository<ComplaintAssignment, Long> {
    Optional<ComplaintAssignment> findByComplaint(Complaint complaint);
    List<ComplaintAssignment> findByOfficer(User officer);
    long countByOfficerAndAssignmentStatus(User officer, AssignmentStatus assignmentStatus);
    List<ComplaintAssignment> findByAssignedByTypeOrderByAssignedAtDesc(AssignedByType assignedByType);
    long countByAssignedByType(AssignedByType assignedByType);
    long countByAssignedByTypeAndAssignedAtAfter(AssignedByType assignedByType, java.time.LocalDateTime after);
    /** Stats must not count cancelled assignments (e.g. citizen cancelled the complaint). */
    long countByAssignedByTypeAndAssignmentStatusNot(AssignedByType assignedByType, AssignmentStatus assignmentStatus);
    long countByAssignedByTypeAndAssignedAtAfterAndAssignmentStatusNot(AssignedByType assignedByType, java.time.LocalDateTime after, AssignmentStatus assignmentStatus);

    /** Overdue / due-today queues for the admin escalation view. */
    List<ComplaintAssignment> findByDueDateBeforeAndAssignmentStatusIn(java.time.LocalDate date, List<AssignmentStatus> statuses);
    List<ComplaintAssignment> findByDueDateAndAssignmentStatusIn(java.time.LocalDate date, List<AssignmentStatus> statuses);
}
