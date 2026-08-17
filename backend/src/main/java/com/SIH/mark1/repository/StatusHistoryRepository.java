package com.SIH.mark1.repository;

import com.SIH.mark1.model.StatusHistory;
import com.SIH.mark1.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findByComplaintOrderByCreatedAtDesc(Complaint complaint);
}
