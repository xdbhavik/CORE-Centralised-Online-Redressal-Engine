package com.SIH.mark1.ai.validation;

import com.SIH.mark1.repository.DepartmentRepository;
import org.springframework.stereotype.Component;

@Component
public class DepartmentValidator {

    private final DepartmentRepository departmentRepository;

    public DepartmentValidator(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public String validate(String department, int confidence) {
        if (department == null || department.isBlank() || confidence < 70) {
            return "UNKNOWN";
        }
        return departmentRepository.findByDepartmentName(department).isPresent() ? department : "UNKNOWN";
    }
}
