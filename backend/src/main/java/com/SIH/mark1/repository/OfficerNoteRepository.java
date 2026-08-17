package com.SIH.mark1.repository;

import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.OfficerNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OfficerNoteRepository extends JpaRepository<OfficerNote, Long> {
    List<OfficerNote> findByComplaintOrderByCreatedAtDesc(Complaint complaint);
}
