package com.SIH.mark1.dto.response;

import com.SIH.mark1.model.PreferredLanguage;
import com.SIH.mark1.model.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OfficerResponse {
    private Long userId;
    private String name;
    private String mobile;
    private String email;
    private PreferredLanguage language;
    private UserRole role;
    private Boolean active;
    private Long departmentId;
    private String departmentName;
    private Long wardId;
}
