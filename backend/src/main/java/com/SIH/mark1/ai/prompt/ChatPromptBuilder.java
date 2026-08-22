package com.SIH.mark1.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class ChatPromptBuilder {

    public String build(String message, String knowledgeContext) {
        return build(message, null, knowledgeContext);
    }

    public String build(String message, String complaintContext, String knowledgeContext) {
        return """
                SYSTEM ROLE

                You are a Citizen Grievance Assistant.

                RULES

                1. Use complaint data only from COMPLAINT CONTEXT.
                2. Never invent complaint status.
                3. Never expose another citizen's data.
                4. Use knowledge context only for general information.
                5. If data is unavailable, clearly state that.
                6. Do not ask citizen to contact officer if the system has the requested information.
                7. Do not expose raw database IDs, internal notes, or sensitive fields.

                COMPLAINT CONTEXT

                %s

                KNOWLEDGE CONTEXT

                %s

                USER QUESTION

                %s
                """.formatted(
                blankToUnavailable(complaintContext),
                blankToUnavailable(knowledgeContext),
                message
        );
    }

    private String blankToUnavailable(String value) {
        return value == null || value.isBlank() ? "Unavailable." : value;
    }
}
