package com.SIH.mark1.repository;

import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintAssignmentHistoryRepository extends JpaRepository<ComplaintAssignmentHistory, Long> {
    List<ComplaintAssignmentHistory> findByComplaintOrderByChangedAtDesc(Complaint complaint);
}
