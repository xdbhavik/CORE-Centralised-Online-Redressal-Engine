package com.SIH.mark1.controller;

import com.SIH.mark1.dto.request.FirebaseLoginRequest;
import com.SIH.mark1.dto.request.FirebaseRegisterRequest;
import com.SIH.mark1.dto.request.FirebaseResetPasswordRequest;
import com.SIH.mark1.dto.request.LoginRequest;
import com.SIH.mark1.dto.request.RegisterRequest;
import com.SIH.mark1.dto.response.LoginResponse;
import com.SIH.mark1.dto.response.ProfileResponse;
import com.SIH.mark1.service.AuthenticationService;
import com.SIH.mark1.service.CustomUserDetailsService;
import com.SIH.mark1.service.FirebaseAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final FirebaseAuthService firebaseAuthService;

    public AuthenticationController(AuthenticationService authenticationService,
                                    FirebaseAuthService firebaseAuthService) {
        this.authenticationService = authenticationService;
        this.firebaseAuthService = firebaseAuthService;
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        LoginResponse resp = authenticationService.register(req);
        return ResponseEntity.ok(resp);
    }

    /**
     * Firebase Phone-OTP based registration.
     * Client (Flutter) verifies the OTP with Firebase SDK and sends the resulting
     * Firebase ID token here. The server verifies it and creates the user.
     */
    @PostMapping("/firebase/register")
    public ResponseEntity<LoginResponse> registerWithFirebase(@Valid @RequestBody FirebaseRegisterRequest req) {
        LoginResponse resp = firebaseAuthService.register(req);
        return ResponseEntity.ok(resp);
    }

    /**
     * Firebase Phone-OTP login for existing users.
     * Client completes the OTP challenge with the Firebase SDK; the verified
     * phone number inside the ID token identifies the account.
     */
    @PostMapping("/firebase/login")
    public ResponseEntity<LoginResponse> loginWithFirebase(@Valid @RequestBody FirebaseLoginRequest req) {
        LoginResponse resp = firebaseAuthService.login(req);
        return ResponseEntity.ok(resp);
    }

    /**
     * Password reset via real Firebase OTP. The ID token's verified phone
     * number proves ownership of the account.
     */
    @PostMapping("/firebase/reset-password")
    public ResponseEntity<java.util.Map<String, Object>> resetPasswordWithFirebase(
            @Valid @RequestBody FirebaseResetPasswordRequest req) {
        firebaseAuthService.resetPassword(req);
        return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Password reset successfully"));
    }


    @PostMapping("/admin/bootstrap")
    public ResponseEntity<LoginResponse> createInitialAdmin(@Valid @RequestBody RegisterRequest req) {
        LoginResponse resp = authenticationService.createInitialAdmin(req);
        return ResponseEntity.ok(resp);
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        LoginResponse resp = authenticationService.login(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<LoginResponse> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        LoginResponse resp = authenticationService.refreshToken(refreshToken);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestParam(value = "refreshToken", required = false) String refreshToken) {
        authenticationService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        ProfileResponse p = authenticationService.getProfile(username);
        return ResponseEntity.ok(p);
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(@RequestBody ProfileResponse update) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        ProfileResponse p = authenticationService.updateProfile(username, update);
        return ResponseEntity.ok(p);
    }
}

