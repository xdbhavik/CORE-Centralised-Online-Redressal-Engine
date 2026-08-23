package com.SIH.mark1.ivr.repository;

import com.SIH.mark1.ivr.model.IvrCallState;
import com.SIH.mark1.ivr.model.IvrSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IvrSessionRepository extends JpaRepository<IvrSession, Long> {

    /** Primary lookup: every provider webhook carries the call identifier. */
    Optional<IvrSession> findByCallSid(String callSid);

    /** Most recent session for a caller, used to resume context across calls. */
    Optional<IvrSession> findFirstByMobileOrderByCreatedAtDesc(String mobile);

    /** Sessions awaiting a specific step, e.g. transcription retries. */
    List<IvrSession> findByState(IvrCallState state);

    /** All sessions tied to a complaint (registration call plus verification calls). */
    List<IvrSession> findByComplaintIdOrderByCreatedAtDesc(Long complaintId);

    boolean existsByCallSid(String callSid);
}
