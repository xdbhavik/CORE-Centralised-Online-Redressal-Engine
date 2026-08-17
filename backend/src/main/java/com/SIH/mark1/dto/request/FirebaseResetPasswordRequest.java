package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Password reset backed by a real Firebase-verified phone number.
 * The client completes the OTP challenge with the Firebase SDK and sends the
 * resulting ID token — the phone number inside it authorizes the reset.
 */
@Data
public class FirebaseResetPasswordRequest {

    @NotBlank(message = "Firebase ID token is required")
    private String idToken;

    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;
}
