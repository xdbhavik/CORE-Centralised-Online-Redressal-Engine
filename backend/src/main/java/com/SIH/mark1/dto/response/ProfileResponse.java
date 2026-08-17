package com.SIH.mark1.dto.response;

import com.SIH.mark1.model.PreferredLanguage;
import com.SIH.mark1.model.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProfileResponse {
    private Long id;
    private String name;
    private String mobile;
    private String email;
    private UserRole role;
    private PreferredLanguage language;
}
