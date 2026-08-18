package com.SIH.mark1.service;

import com.SIH.mark1.dto.request.OtpSendRequest;
import com.SIH.mark1.dto.request.OtpVerifyRequest;
import com.SIH.mark1.dto.request.PasswordResetRequest;
import com.SIH.mark1.dto.response.OtpResponse;
import com.SIH.mark1.model.OtpPurpose;
import com.SIH.mark1.model.OtpVerification;
import com.SIH.mark1.model.User;
import com.SIH.mark1.repository.OtpRepository;
import com.SIH.mark1.repository.UserRepository;
import com.SIH.mark1.service.otp.MockOtpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final MockOtpClient mockOtpClient;
    private final OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final long RESEND_COOLDOWN_SECONDS = 60;

    public OtpResponse sendOtp(OtpSendRequest req) {
        String formattedMobile = mockOtpClient.formatPhoneNumber(req.getMobile());
        Optional<OtpVerification> existing = otpRepository
                .findTopByMobileAndPurposeAndVerifiedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        formattedMobile, req.getPurpose(), LocalDateTime.now());
        if (existing.isPresent()) {
            OtpVerification ov = existing.get();
            long secondsSinceCreated = Duration.between(ov.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (secondsSinceCreated < RESEND_COOLDOWN_SECONDS) {
                long waitSeconds = RESEND_COOLDOWN_SECONDS - secondsSinceCreated;
                return OtpResponse.builder().success(false)
                        .message("Please wait " + waitSeconds + " seconds")
                        .expiresInMinutes(OTP_EXPIRY_MINUTES)
                        .sessionInfo(ov.getSessionInfo()).build();
            }
        }
        String sessionInfo = mockOtpClient.sendOtp(formattedMobile);
        otpRepository.findByMobileAndVerifiedFalseAndExpiresAtBefore(formattedMobile, LocalDateTime.now())
                .forEach(otp -> { otp.setVerified(true); otpRepository.save(otp); });
        OtpVerification otpVerification = OtpVerification.builder()
                .mobile(formattedMobile).sessionInfo(sessionInfo)
                .purpose(req.getPurpose())
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .attemptCount(0).maxAttempts(MAX_ATTEMPTS).verified(false).build();
        otpRepository.save(otpVerification);
        return OtpResponse.builder().success(true)
                .message("OTP sent successfully")
                .expiresInMinutes(OTP_EXPIRY_MINUTES)
                .sessionInfo(sessionInfo).build();
    }

    @Transactional
    public OtpResponse verifyOtp(OtpVerifyRequest req) {
        String formattedMobile = mockOtpClient.formatPhoneNumber(req.getMobile());
        OtpVerification otpVerification = otpRepository
                .findTopByMobileAndPurposeAndVerifiedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        formattedMobile, req.getPurpose(), LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("OTP expired or not found"));
        if (otpVerification.isVerified()) throw new IllegalArgumentException("OTP already used");
        if (otpVerification.getAttemptCount() >= otpVerification.getMaxAttempts()) throw new IllegalArgumentException("Max attempts exceeded");
        if (LocalDateTime.now().isAfter(otpVerification.getExpiresAt())) throw new IllegalArgumentException("OTP expired");
        otpVerification.setAttemptCount(otpVerification.getAttemptCount() + 1);
        otpRepository.save(otpVerification);
        try {
            String idToken = mockOtpClient.verifyOtp(otpVerification.getSessionInfo(), req.getOtp());
            otpVerification.setVerified(true);
            otpRepository.save(otpVerification);
            return OtpResponse.builder().success(true)
                    .message("OTP verified")
                    .expiresInMinutes(0)
                    .sessionInfo(idToken).build();
        } catch (Exception e) {
            int remaining = MAX_ATTEMPTS - otpVerification.getAttemptCount();
            throw new IllegalArgumentException("Invalid OTP. " + remaining + " attempts remaining");
        }
    }

    @Transactional
    public void resetPassword(PasswordResetRequest req) {
        String formattedMobile = mockOtpClient.formatPhoneNumber(req.getMobile());
        OtpVerification otpVerification = otpRepository
                .findTopByMobileAndPurposeAndVerifiedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        formattedMobile, OtpPurpose.PASSWORD_RESET, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("OTP expired or not found"));
        if (!otpVerification.isVerified()) throw new IllegalArgumentException("Please verify OTP first");
        User user = userRepository.findByMobile(formattedMobile)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        otpVerification.setVerified(true);
        otpRepository.save(otpVerification);
    }

    public boolean isMobileVerifiedForRegistration(String mobile) {
        String formattedMobile = mockOtpClient.formatPhoneNumber(mobile);
        return otpRepository
                .findTopByMobileAndPurposeAndVerifiedTrueAndExpiresAtAfterOrderByCreatedAtDesc(
                        formattedMobile, OtpPurpose.REGISTRATION, LocalDateTime.now())
                .isPresent();
    }
}