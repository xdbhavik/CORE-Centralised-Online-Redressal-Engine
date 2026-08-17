package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Firebase Phone-OTP login for EXISTING users.
 * Client verifies the OTP with the Firebase SDK and sends the resulting ID token here.
 */
@Data
public class FirebaseLoginRequest {

    @NotBlank(message = "Firebase ID token is required")
    private String idToken;
}
