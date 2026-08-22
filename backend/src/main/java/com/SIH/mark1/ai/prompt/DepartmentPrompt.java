package com.SIH.mark1.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class DepartmentPrompt {
    public String instruction() {
        return "Predict the most suitable government department from retrieved context only.";
    }
}
