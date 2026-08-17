package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotNull
    private Long departmentId;
    @NotBlank
    private String categoryName;
    private String description;
    private Long defaultPriorityId;
    private Boolean active;
}
