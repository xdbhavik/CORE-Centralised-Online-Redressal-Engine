package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DepartmentRequest {
    @NotBlank
    private String departmentName;
    private String description;
    private String contactEmail;
    private String contactPhone;
    private Boolean active;
}
