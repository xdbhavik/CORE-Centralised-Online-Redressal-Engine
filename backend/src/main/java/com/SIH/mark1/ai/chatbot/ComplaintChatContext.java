package com.SIH.mark1.ai.chatbot;

import java.time.LocalDateTime;
import java.util.List;

public record ComplaintChatContext(
        String complaintNumber,
        String title,
        String status,
        String department,
        String category,
        String officer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TimelineItem> timeline
) {
    public boolean hasOfficer() {
        return officer != null && !officer.isBlank();
    }

    public record TimelineItem(LocalDateTime time, String event) {
    }
}
