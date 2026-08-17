package com.SIH.mark1.repository;

import com.SIH.mark1.model.ComplaintMedia;
import com.SIH.mark1.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintMediaRepository extends JpaRepository<ComplaintMedia, Long> {
    List<ComplaintMedia> findByComplaint(Complaint complaint);
}
