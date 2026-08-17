package com.SIH.mark1.repository;

import com.SIH.mark1.model.Feedback;
import com.SIH.mark1.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByComplaint(Complaint complaint);
}
