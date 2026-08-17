package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Admin justification for overriding a citizen's verification answer.
 *
 * <p>The reason is mandatory, mirroring the existing reassignment rule. An override either
 * contradicts a citizen's explicit denial or closes their complaint as fake — both are
 * exactly the actions that must never appear in the audit trail unexplained.</p>
 */
@Getter
@Setter
public class VerificationOverrideRequest {

    /** Capped at 500 to match the {@code verification_remarks} column it is stored in. */
    @NotBlank(message = "Reason is required for a verification override")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
