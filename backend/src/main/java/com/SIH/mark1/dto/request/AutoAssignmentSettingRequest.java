package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload for switching the assignment mode.
 * enabled = true  → AI auto-assigns new complaints to department officers
 * enabled = false → complaints wait in the admin manual assignment queue
 */
@Data
public class AutoAssignmentSettingRequest {

    @NotNull(message = "enabled flag is required")
    private Boolean enabled;
}
