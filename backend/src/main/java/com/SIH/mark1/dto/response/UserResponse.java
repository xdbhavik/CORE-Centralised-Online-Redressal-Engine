package com.SIH.mark1.dto.response;

import com.SIH.mark1.model.PreferredLanguage;
import com.SIH.mark1.model.UserRole;
import lombok.Builder;
import lombok.Data;

/**
 * Summary view of a user returned to admins.
 * Deliberately excludes the password hash.
 */
@Data
@Builder
public class UserResponse {
    private Long userId;
    private String name;
    private String mobile;
    private String email;
    private PreferredLanguage language;
    private UserRole role;
    private Boolean active;
    private String address;
}
