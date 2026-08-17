package com.SIH.mark1.dto.request;

import com.SIH.mark1.model.PreferredLanguage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OfficerRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String mobile;
    @Email
    private String email;
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
    @NotNull
    private PreferredLanguage language;
    @NotNull
    private Long departmentId;
    private Long wardId;
    private Boolean active;
}
