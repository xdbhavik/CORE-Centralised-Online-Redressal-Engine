package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String mobile;

    @NotBlank
    private String password;
}
