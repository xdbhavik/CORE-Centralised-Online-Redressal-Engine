package com.SIH.mark1.dto.request;

import com.SIH.mark1.model.PreferredLanguage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FirebaseRegisterRequest {

    @NotBlank(message = "Firebase ID token is required")
    private String idToken;

    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotNull(message = "Preferred language is required")
    private PreferredLanguage preferredLanguage;
}
