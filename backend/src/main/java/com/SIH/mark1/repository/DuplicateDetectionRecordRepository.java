package com.SIH.mark1.repository;

import com.SIH.mark1.model.DuplicateDetectionRecord;
import com.SIH.mark1.model.DuplicateReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DuplicateDetectionRecordRepository extends JpaRepository<DuplicateDetectionRecord, Long> {

    List<DuplicateDetectionRecord> findByReviewStatusOrderByCreatedAtDesc(DuplicateReviewStatus reviewStatus);

    Optional<DuplicateDetectionRecord> findFirstByComplaintComplaintIdAndReviewStatus(
            Long complaintId, DuplicateReviewStatus reviewStatus);

    List<DuplicateDetectionRecord> findByComplaintComplaintId(Long complaintId);
}