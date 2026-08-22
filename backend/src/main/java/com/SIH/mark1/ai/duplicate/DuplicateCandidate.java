package com.SIH.mark1.ai.duplicate;

public record DuplicateCandidate(
        Long complaintId,
        String complaintNumber,
        double similarity,
        String department,
        String category,
        String scope,
        String resourceType,
        String resourceKey,
        String areaKey,
        String status
) {
}
