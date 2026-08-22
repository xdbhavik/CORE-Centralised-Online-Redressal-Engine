package com.SIH.mark1.ai.service;

import com.SIH.mark1.ai.dto.SummaryRequest;
import com.SIH.mark1.ai.dto.SummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class SummarizationService {

    public SummaryResponse summarize(SummaryRequest request) {
        String cleaned = request.text().replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 140) {
            return new SummaryResponse(cleaned);
        }
        int sentenceEnd = cleaned.indexOf('.');
        if (sentenceEnd > 40 && sentenceEnd < 140) {
            return new SummaryResponse(cleaned.substring(0, sentenceEnd + 1));
        }
        return new SummaryResponse(cleaned.substring(0, 137).trim() + "...");
    }
}
