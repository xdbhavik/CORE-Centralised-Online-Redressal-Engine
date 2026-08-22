package com.SIH.mark1.ai.duplicate;

import java.util.List;

public record DuplicateDetectionResult(
        DuplicateDecision decision,
        Long existingComplaintId,
        String existingComplaintNumber,
        String existingStatus,
        double similarity,
        ComplaintScope scope,
        boolean resourceMatch,
        boolean locationMatch,
        List<String> reasons
) {
    public static DuplicateDetectionResult unavailable(String reason) {
        return new DuplicateDetectionResult(
                DuplicateDecision.UNAVAILABLE,
                null,
                null,
                null,
                0.0,
                ComplaintScope.LOCAL_AREA,
                false,
                false,
                List.of(reason));
    }

    public static DuplicateDetectionResult notDuplicate(ComplaintScope scope, String reason) {
        return new DuplicateDetectionResult(
                DuplicateDecision.NOT_DUPLICATE,
                null,
                null,
                null,
                0.0,
                scope,
                false,
                false,
                List.of(reason));
    }
}
