package com.SIH.mark1.service.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Mock OTP client for testing purposes.
 * Generates a 6-digit OTP locally.
 * In production, replace with a real SMS gateway (Twilio, MSG91, Fast2SMS, etc.)
 * or Firebase client-side SDK.
 *
 * In mock mode, sessionInfo returned by sendOtp() IS the generated OTP itself.
 * verifyOtp() simply compares the stored sessionInfo (OTP) with the submitted OTP.
 */
@Component
public class MockOtpClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String sendOtp(String phoneNumber) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        System.out.println("======================================");
        System.out.println("[MOCK OTP] OTP for " + phoneNumber + " is: " + otp);
        System.out.println("======================================");
        return otp; // sessionInfo = the OTP itself in mock mode
    }

    public String verifyOtp(String sessionInfo, String otp) {
        // sessionInfo is the OTP that was generated and stored in DB
        if (sessionInfo != null && sessionInfo.equals(otp)) {
            return "mock-verified-token";
        }
        throw new IllegalArgumentException("Invalid OTP");
    }

    public String formatPhoneNumber(String mobile) {
        if (!mobile.startsWith("+")) {
            return "+91" + mobile;
        }
        return mobile;
    }
}
