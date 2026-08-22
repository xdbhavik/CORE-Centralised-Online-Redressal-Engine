package com.SIH.mark1.ai.chatbot;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ChatIntentService {

    public ChatIntent detect(String message) {
        String text = normalize(message);

        boolean complaint = containsAny(text,
                "meri complaint", "my complaint", "complaint ka", "complaint ki", "complaint ke",
                "grv-", "cmp-", "status", "pending", "assigned", "history", "timeline");
        boolean knowledge = containsAny(text,
                "sla", "kitne time", "time me", "rules", "rule", "sop", "kaise", "process", "procedure",
                "normal", "expected resolution", "solve honi chahiye");

        if (complaint && knowledge) {
            return ChatIntent.HYBRID;
        }
        if (containsAny(text, "history", "timeline", "itihaas")) {
            return ChatIntent.COMPLAINT_HISTORY;
        }
        if (containsAny(text, "kis officer", "officer ko", "officer ke paas", "assigned officer", "kisko assigned")) {
            return ChatIntent.ASSIGNED_OFFICER;
        }
        if (containsAny(text, "kis department", "department ke paas", "department ko", "which department")) {
            return ChatIntent.DEPARTMENT;
        }
        if (containsAny(text, "meri complaints", "my complaints", "complaints dikhao", "all complaints")) {
            return ChatIntent.COMPLAINT_LIST;
        }
        if (containsAny(text, "register", "create complaint", "complaint banana", "complaint register", "nayi complaint")) {
            return ChatIntent.CREATE_COMPLAINT;
        }
        if (complaint || containsAny(text, "kya hua", "status kya", "pending hai", "resolved hai")) {
            return ChatIntent.COMPLAINT_STATUS;
        }
        return ChatIntent.GENERAL_KNOWLEDGE;
    }

    private String normalize(String message) {
        if (message == null) {
            return "";
        }
        return message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
