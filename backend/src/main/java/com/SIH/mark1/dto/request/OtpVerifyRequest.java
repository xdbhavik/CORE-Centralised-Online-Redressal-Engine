package com.SIH.mark1.dto.request;

import com.SIH.mark1.model.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OtpVerifyRequest {
    @NotBlank
    private String mobile;

    @NotBlank
    private String otp;

    @NotNull
    private OtpPurpose purpose;
}