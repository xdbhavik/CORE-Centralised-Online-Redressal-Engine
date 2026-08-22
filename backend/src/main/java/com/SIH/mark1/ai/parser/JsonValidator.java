package com.SIH.mark1.ai.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonValidator {

    private final ObjectMapper objectMapper;

    public JsonValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isValidJson(String value) {
        try {
            objectMapper.readTree(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
