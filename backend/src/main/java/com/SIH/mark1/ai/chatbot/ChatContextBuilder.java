package com.SIH.mark1.ai.chatbot;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ChatContextBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public String complaintContext(ComplaintChatContext context) {
        if (context == null) {
            return "No complaint context available.";
        }
        return """
                Complaint Number: %s
                Title: %s
                Status: %s
                Department: %s
                Category: %s
                Assigned Officer: %s
                Created At: %s
                Last Updated At: %s
                Timeline:
                %s
                """.formatted(
                value(context.complaintNumber()),
                value(context.title()),
                value(context.status()),
                value(context.department()),
                value(context.category()),
                context.hasOfficer() ? context.officer() : "Not assigned",
                context.createdAt() == null ? "Unavailable" : DATE_TIME.format(context.createdAt()),
                context.updatedAt() == null ? "Unavailable" : DATE_TIME.format(context.updatedAt()),
                timeline(context.timeline())
        );
    }

    private String timeline(List<ComplaintChatContext.TimelineItem> timeline) {
        if (timeline == null || timeline.isEmpty()) {
            return "No timeline available.";
        }
        StringBuilder builder = new StringBuilder();
        for (ComplaintChatContext.TimelineItem item : timeline) {
            builder.append("- ")
                    .append(DATE_TIME.format(item.time()))
                    .append(": ")
                    .append(item.event())
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "Unavailable" : value;
    }
}
