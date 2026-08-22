package com.SIH.mark1.ai.duplicate;

import com.SIH.mark1.ai.config.DuplicateProperties;
import com.SIH.mark1.ai.dto.AIResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DuplicateDecisionEngine {

    private final DuplicateProperties properties;

    public DuplicateDecisionEngine(DuplicateProperties properties) {
        this.properties = properties;
    }

    public DuplicateDetectionResult decide(AIResponse analysis,
                                           ResourceExtractor.ExtractedResource resource,
                                           String areaKey,
                                           List<DuplicateCandidate> candidates) {
        ComplaintScope scope = ComplaintScope.from(analysis != null ? analysis.scope() : null);
        if (candidates == null || candidates.isEmpty()) {
            return DuplicateDetectionResult.notDuplicate(scope, "No matching complaint candidate found.");
        }

        DuplicateCandidate best = selectBestCandidate(candidates);
        if (best == null) {
            return DuplicateDetectionResult.notDuplicate(scope, "No eligible active complaint candidate found.");
        }

        if (best.similarity() < properties.threshold()) {
            return new DuplicateDetectionResult(
                    DuplicateDecision.NOT_DUPLICATE,
                    best.complaintId(),
                    best.complaintNumber(),
                    best.status(),
                    best.similarity(),
                    scope,
                    false,
                    false,
                    List.of("Similarity below duplicate threshold."));
        }

        boolean sameCategory = same(analysis != null ? analysis.category() : null, best.category());
        boolean sameDepartment = same(analysis != null ? analysis.department() : null, best.department());
        boolean resourceKnown = resource != null && resource.hasIdentifier();
        boolean resourceMatch = resourceKnown && same(resource.key(), best.resourceKey());
        boolean candidateResourceKnown = best.resourceKey() != null && !best.resourceKey().isBlank();
        boolean locationMatch = areaKey != null && same(areaKey, best.areaKey());
        boolean closedOrResolved = isResolvedOrClosed(best.status());
        boolean active = isActiveStatus(best.status());
        List<String> reasons = new ArrayList<>();
        reasons.add("Similarity " + String.format("%.2f", best.similarity()));
        if (sameDepartment) reasons.add("Same department");
        if (sameCategory) reasons.add("Same category");
        if (resourceMatch) reasons.add("Same resource");
        if (locationMatch) reasons.add("Same area/location");
        if (active) reasons.add("Existing complaint is active: " + best.status());
        if (closedOrResolved) reasons.add("Existing complaint is resolved/closed; treat as possible recurrence.");

        DuplicateDecision decision = switch (scope) {
            case INDIVIDUAL -> decideIndividual(best.similarity(), sameCategory, resourceKnown, candidateResourceKnown, resourceMatch, active, closedOrResolved);
            case LOCAL_AREA -> decideArea(best.similarity(), sameCategory, locationMatch, active, closedOrResolved);
            case PUBLIC_INFRASTRUCTURE -> decideArea(best.similarity(), sameCategory, locationMatch, active, closedOrResolved);
        };

        return new DuplicateDetectionResult(
                decision,
                best.complaintId(),
                best.complaintNumber(),
                best.status(),
                best.similarity(),
                scope,
                resourceMatch,
                locationMatch,
                reasons);
    }

    DuplicateCandidate selectBestCandidate(List<DuplicateCandidate> candidates) {
        for (DuplicateCandidate candidate : candidates) {
            if (isRejected(candidate.status())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private DuplicateDecision decideIndividual(double similarity,
                                               boolean sameCategory,
                                               boolean resourceKnown,
                                               boolean candidateResourceKnown,
                                               boolean resourceMatch,
                                               boolean active,
                                               boolean closedOrResolved) {
        if (closedOrResolved && similarity >= properties.highConfidence() && sameCategory) {
            return DuplicateDecision.POSSIBLE_RECURRING_ISSUE;
        }
        if (resourceKnown && candidateResourceKnown && !resourceMatch) {
            return DuplicateDecision.NOT_DUPLICATE;
        }
        if (similarity >= properties.highConfidence() && sameCategory && resourceMatch && active) {
            return DuplicateDecision.DUPLICATE;
        }
        if (similarity >= properties.highConfidence() && sameCategory) {
            return DuplicateDecision.POSSIBLE_DUPLICATE;
        }
        if (similarity >= properties.threshold()) {
            return DuplicateDecision.POSSIBLE_DUPLICATE;
        }
        return DuplicateDecision.NOT_DUPLICATE;
    }

    private DuplicateDecision decideArea(double similarity,
                                         boolean sameCategory,
                                         boolean locationMatch,
                                         boolean active,
                                         boolean closedOrResolved) {
        if (closedOrResolved && similarity >= properties.highConfidence() && sameCategory && locationMatch) {
            return DuplicateDecision.POSSIBLE_RECURRING_ISSUE;
        }
        if (similarity >= properties.highConfidence() && sameCategory && locationMatch && active) {
            return DuplicateDecision.DUPLICATE;
        }
        if (similarity >= properties.threshold() && sameCategory) {
            return DuplicateDecision.POSSIBLE_DUPLICATE;
        }
        return DuplicateDecision.NOT_DUPLICATE;
    }

    private boolean isActiveStatus(String status) {
        return status != null && List.of("REGISTERED", "AI_ANALYZED", "ASSIGNED", "IN_PROGRESS", "REOPENED")
                .stream()
                .anyMatch(active -> active.equalsIgnoreCase(status));
    }

    private boolean isResolvedOrClosed(String status) {
        return status != null && List.of("RESOLVED", "CLOSED").stream().anyMatch(done -> done.equalsIgnoreCase(status));
    }

    private boolean isRejected(String status) {
        return status != null && "REJECTED".equalsIgnoreCase(status);
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
