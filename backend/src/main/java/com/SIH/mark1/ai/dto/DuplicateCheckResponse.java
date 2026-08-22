package com.SIH.mark1.ai.dto;

import java.util.List;

public record DuplicateCheckResponse(
        boolean duplicate,
        Long existingComplaintId,
        double similarity,
        List<String> reasons,
        String decision,
        String existingComplaintNumber,
        String existingStatus,
        String scope,
        boolean resourceMatch,
        boolean locationMatch
) {
    public DuplicateCheckResponse(boolean duplicate, Long existingComplaintId, double similarity, String reason) {
        this(duplicate, existingComplaintId, similarity,
                reason == null ? List.of() : List.of(reason),
                duplicate ? "DUPLICATE" : "NOT_DUPLICATE",
                null, null, null, false, false);
    }
}
