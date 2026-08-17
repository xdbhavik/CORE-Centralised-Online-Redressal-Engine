package com.SIH.mark1.repository;

import com.SIH.mark1.model.ComplaintStatusMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplaintStatusMasterRepository extends JpaRepository<ComplaintStatusMaster, Long> {

    Optional<ComplaintStatusMaster> findByStatusCode(String statusCode);
}
