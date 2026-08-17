package com.SIH.mark1.dto.response;

import com.SIH.mark1.model.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String name;
    private String mobile;
    private String email;
    private UserRole role;
}
