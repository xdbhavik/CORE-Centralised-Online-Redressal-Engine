package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DepartmentResponse {
    private Long departmentId;
    private String departmentName;
    private String description;
    private String contactEmail;
    private String contactPhone;
    private Boolean active;
}
