package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Current assignment mode shown on the admin panel switch.
 */
@Data
@Builder
public class AutoAssignmentSettingResponse {
    /** true = AI mode, false = MANUAL mode */
    private boolean enabled;
    /** Human readable mode label: "AI" or "MANUAL" */
    private String mode;
    private String description;
    private LocalDateTime updatedAt;
}
