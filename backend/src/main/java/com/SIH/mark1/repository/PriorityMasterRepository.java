package com.SIH.mark1.repository;

import com.SIH.mark1.model.PriorityMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PriorityMasterRepository extends JpaRepository<PriorityMaster, Long> {
    Optional<PriorityMaster> findByPriorityCode(String priorityCode);
}
