package com.SIH.mark1.repository;

import com.SIH.mark1.model.DuplicateComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DuplicateComplaintRepository extends JpaRepository<DuplicateComplaint, Long> {
}
