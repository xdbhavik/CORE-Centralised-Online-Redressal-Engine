package com.SIH.mark1.ivr.service;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single source of truth for phone-number normalisation across the telephony module.
 *
 * <p>Extracted so calls and SMS cannot drift apart: if two services each carried their own
 * E.164 logic, a number that dials successfully could still fail to receive the follow-up
 * SMS, which is precisely the case verification depends on.</p>
 */
@Component
public class PhoneNumberFormatter {

    private static final String INDIA_DIALLING_CODE = "+91";

    /**
     * Normalises an Indian mobile number to E.164.
     *
     * <p>Stored numbers are inconsistent — some rows hold bare 10-digit numbers, others carry
     * a {@code +91} or {@code 0} prefix — and telephony providers reject anything that is not
     * strictly E.164, so normalising here prevents avoidable call failures.</p>
     *
     * @return the E.164 number, or empty when the input cannot be interpreted as one
     */
    public Optional<String> toE164(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return Optional.empty();
        }
        String digits = mobile.replaceAll("[^0-9]", "");

        if (digits.length() == 10) {
            return Optional.of(INDIA_DIALLING_CODE + digits);
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            return Optional.of(INDIA_DIALLING_CODE + digits.substring(1));
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return Optional.of("+" + digits);
        }
        if (digits.length() == 13 && digits.startsWith("091")) {
            return Optional.of("+" + digits.substring(1));
        }
        return Optional.empty();
    }

    /** Last 10 digits of a number, matching how mobiles are stored on {@code User}. */
    public String toLocalTenDigits(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    /** Masks a phone number for logs so call and SMS records are not a PII leak. */
    public String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
