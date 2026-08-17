package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponse {
    private Long categoryId;
    private String categoryName;
    private String description;
    private Long departmentId;
    private String departmentName;
    private Long defaultPriorityId;
    private String defaultPriorityCode;
    private Boolean active;
}
