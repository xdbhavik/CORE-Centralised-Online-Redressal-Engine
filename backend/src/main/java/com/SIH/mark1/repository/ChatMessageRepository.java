package com.SIH.mark1.repository;

import com.SIH.mark1.model.ChatMessage;
import com.SIH.mark1.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByComplaint(Complaint complaint);
}
