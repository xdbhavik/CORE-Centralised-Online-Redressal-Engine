package com.SIH.mark1.ai.validation;

import com.SIH.mark1.model.PriorityMaster;
import com.SIH.mark1.repository.PriorityMasterRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PriorityValidator {

    private final PriorityMasterRepository priorityMasterRepository;

    public PriorityValidator(PriorityMasterRepository priorityMasterRepository) {
        this.priorityMasterRepository = priorityMasterRepository;
    }

    public String validate(String priority) {
        if (priority == null || priority.isBlank()) {
            return "MEDIUM";
        }
        String normalized = priority.toUpperCase(Locale.ROOT);
        return priorityMasterRepository.findAll().stream()
                .map(PriorityMaster::getPriorityCode)
                .filter(code -> code != null && code.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(normalized);
    }
}
