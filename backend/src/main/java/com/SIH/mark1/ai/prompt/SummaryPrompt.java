package com.SIH.mark1.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class SummaryPrompt {
    public String instruction() {
        return "Summarize the complaint in one clear officer-friendly line.";
    }
}
