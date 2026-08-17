package com.SIH.mark1.repository;

import com.SIH.mark1.model.AiAnalysis;
import com.SIH.mark1.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {
    Optional<AiAnalysis> findByComplaint(Complaint complaint);
}
