package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A single entry in the complaint's status timeline.
 *
 * Example response:
 * {
 *   "status":  "REGISTERED",
 *   "time":    "2026-08-06T10:00",
 *   "remarks": "Complaint registered successfully"
 * }
 */
@Data
@Builder
public class TimelineDTO {

    /** Status code, e.g. REGISTERED, ASSIGNED, IN_PROGRESS, RESOLVED. */
    private String        status;

    /** Timestamp when this status was set. */
    private LocalDateTime time;

    /** Optional remark added by officer or system. */
    private String        remarks;
}
