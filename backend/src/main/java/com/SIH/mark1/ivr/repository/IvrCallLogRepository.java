package com.SIH.mark1.ivr.repository;

import com.SIH.mark1.ivr.model.IvrCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IvrCallLogRepository extends JpaRepository<IvrCallLog, Long> {

    /** Full event timeline for one call, oldest first. */
    List<IvrCallLog> findByCallSidOrderByCreatedAtAsc(String callSid);

    List<IvrCallLog> findTop50ByOrderByCreatedAtDesc();
}
