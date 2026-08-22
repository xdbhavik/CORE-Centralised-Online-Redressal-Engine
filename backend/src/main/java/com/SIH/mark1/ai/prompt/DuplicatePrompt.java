package com.SIH.mark1.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class DuplicatePrompt {
    public String instruction() {
        return "Compare complaint text and location to identify likely duplicate grievances.";
    }
}
