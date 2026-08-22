package com.SIH.mark1.ai.prompt;

import org.springframework.stereotype.Component;

/**
 * Builds structured prompts for complaint analysis.
 * <p>
 * Uses a System + User message pattern to:
 * - Establish strict role and output contract (JSON only)
 * - Inject the retrieved Qdrant context
 * - Minimize hallucination by enforcing "use only retrieved knowledge"
 */
@Component
public class ComplaintPromptBuilder {

    public String build(String complaint, String context) {
        String safeContext = (context == null || context.isBlank())
                ? "No relevant knowledge found. If uncertain, use UNKNOWN."
                : context;

        return """
                SYSTEM:
                You are an expert Indian Government Grievance Analyzer AI.

                Your ONLY job is to analyze the complaint and return a single JSON object.

                STRICT RULES:
                1. Return ONLY valid JSON — no markdown, no explanation, no extra text.
                2. Use ONLY the Retrieved Knowledge below to determine department and category.
                3. If Retrieved Knowledge is insufficient or unclear, set department to "UNKNOWN".
                4. The "summary" field MUST always be in English, regardless of complaint language.
                5. "confidence" must be an integer between 0 and 100.
                6. "priority" must be one of: LOW, MEDIUM, HIGH, CRITICAL.
                7. "scope" must be one of: INDIVIDUAL, LOCAL_AREA, PUBLIC_INFRASTRUCTURE.
                8. Extract resourceType/resourceIdentifier only when the complaint names a specific resource such as meter ID, water connection ID or property ID. Do not invent identifiers.

                REQUIRED JSON SCHEMA (return exactly these fields):
                {
                  "title": "Short complaint title (max 10 words, English)",
                  "summary": "Complaint summary in English (max 150 chars)",
                  "department": "Exact department name or UNKNOWN",
                  "category": "Exact category name or UNKNOWN",
                  "priority": "LOW | MEDIUM | HIGH | CRITICAL",
                  "confidence": 0-100,
                  "scope": "INDIVIDUAL | LOCAL_AREA | PUBLIC_INFRASTRUCTURE",
                  "resourceType": "ELECTRICITY_METER | WATER_CONNECTION | PROPERTY | UNKNOWN",
                  "resourceIdentifier": "exact resource id or null"
                }

                USER:
                Complaint Text:
                %s

                Retrieved Knowledge:
                %s

                Return JSON:
                """.formatted(complaint, safeContext);
    }
}
