package com.SIH.mark1.controller;

import com.SIH.mark1.dto.request.OtpSendRequest;
import com.SIH.mark1.dto.request.OtpVerifyRequest;
import com.SIH.mark1.dto.request.PasswordResetRequest;
import com.SIH.mark1.dto.response.OtpResponse;
import com.SIH.mark1.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * DEV-ONLY legacy mock OTP endpoints (OTP stored in DB, no real SMS).
 * Production uses Firebase phone auth — this controller stays DISABLED unless
 * app.dev.mock-otp-enabled=true is set explicitly for local testing.
 */
@RestController
@RequestMapping("/api/v1/otp")
@ConditionalOnProperty(name = "app.dev.mock-otp-enabled", havingValue = "true", matchIfMissing = false)
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/send")
    public ResponseEntity<OtpResponse> sendOtp(@Valid @RequestBody OtpSendRequest req) {
        OtpResponse resp = otpService.sendOtp(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        OtpResponse resp = otpService.verifyOtp(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<OtpResponse> resetPassword(@Valid @RequestBody PasswordResetRequest req) {
        otpService.resetPassword(req);
        return ResponseEntity.ok(OtpResponse.builder().success(true).message("Password reset successfully").build());
    }
}