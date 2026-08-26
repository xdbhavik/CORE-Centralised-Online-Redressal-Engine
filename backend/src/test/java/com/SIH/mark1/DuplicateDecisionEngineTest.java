package com.SIH.mark1;

import com.SIH.mark1.ai.config.DuplicateProperties;
import com.SIH.mark1.ai.dto.AIResponse;
import com.SIH.mark1.ai.duplicate.DuplicateCandidate;
import com.SIH.mark1.ai.duplicate.DuplicateDecision;
import com.SIH.mark1.ai.duplicate.DuplicateDecisionEngine;
import com.SIH.mark1.ai.duplicate.DuplicateDetectionResult;
import com.SIH.mark1.ai.duplicate.ResourceExtractor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class DuplicateDecisionEngineTest {

    private DuplicateDecisionEngine engine;
    private ResourceExtractor extractor;

    @BeforeEach
    void setUp() {
        engine = new DuplicateDecisionEngine(new DuplicateProperties(true, 10, 0.82, 0.90));
        extractor = new ResourceExtractor();
    }

    @Test
    void highSimilarityIndividualComplaintsWithDifferentMetersAreNotDuplicate() {
        AIResponse analysis = new AIResponse(
                "Meter Fault",
                "The electricity meter is not working.",
                "Electricity Department",
                "Power Supply",
                "HIGH",
                92,
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                "MTR002");
        ResourceExtractor.ExtractedResource newResource = extractor.extract(
                "Mere ghar ka electricity meter MTR002 kaam nahi kar raha.",
                analysis.department(),
                analysis.category());
        ResourceExtractor.ExtractedResource existingResource = extractor.extract(
                "Mere ghar ka electricity meter MTR001 kaam nahi kar raha.",
                analysis.department(),
                analysis.category());

        DuplicateCandidate candidate = new DuplicateCandidate(
                1L,
                "GRV-2026-000001",
                0.96,
                "Electricity Department",
                "Power Supply",
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                existingResource.key(),
                null,
                "IN_PROGRESS");

        DuplicateDetectionResult result = engine.decide(analysis, newResource, null, List.of(candidate));

        Assertions.assertEquals(DuplicateDecision.NOT_DUPLICATE, result.decision());
    }

    @Test
    void highSimilarityIndividualComplaintsWithSameMeterAreDuplicate() {
        AIResponse analysis = new AIResponse(
                "Meter Fault",
                "The electricity meter is still not working.",
                "Electricity Department",
                "Power Supply",
                "HIGH",
                92,
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                "MTR001");
        ResourceExtractor.ExtractedResource resource = extractor.extract(
                "Electric meter MTR001 abhi bhi kaam nahi kar raha.",
                analysis.department(),
                analysis.category());

        DuplicateCandidate candidate = new DuplicateCandidate(
                1L,
                "GRV-2026-000001",
                0.96,
                "Electricity Department",
                "Power Supply",
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                resource.key(),
                null,
                "IN_PROGRESS");

        DuplicateDetectionResult result = engine.decide(analysis, resource, null, List.of(candidate));

        Assertions.assertEquals(DuplicateDecision.DUPLICATE, result.decision());
    }

    @Test
    void individualComplaintWithoutResourceIdIsPossibleDuplicate() {
        AIResponse analysis = new AIResponse(
                "Meter Fault",
                "The electricity meter is not working.",
                "Electricity Department",
                "Power Supply",
                "HIGH",
                91,
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                null);
        ResourceExtractor.ExtractedResource resource = extractor.extract(
                "Mere ghar ka electric meter kaam nahi kar raha.",
                analysis.department(),
                analysis.category());

        DuplicateCandidate candidate = new DuplicateCandidate(
                2L,
                "GRV-2026-000002",
                0.91,
                "Electricity Department",
                "Power Supply",
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                null,
                null,
                "IN_PROGRESS");

        DuplicateDetectionResult result = engine.decide(analysis, resource, null, List.of(candidate));

        Assertions.assertEquals(DuplicateDecision.POSSIBLE_DUPLICATE, result.decision());
    }

    @Test
    void resolvedComplaintWithHighSimilarityIsPossibleRecurringIssue() {
        AIResponse analysis = new AIResponse(
                "Meter Fault",
                "The electricity meter stopped working again.",
                "Electricity Department",
                "Power Supply",
                "HIGH",
                92,
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                "MTR001");
        ResourceExtractor.ExtractedResource resource = extractor.extract(
                "Electric meter MTR001 abhi bhi kaam nahi kar raha.",
                analysis.department(),
                analysis.category());

        DuplicateCandidate candidate = new DuplicateCandidate(
                1L,
                "GRV-2026-000001",
                0.94,
                "Electricity Department",
                "Power Supply",
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                resource.key(),
                null,
                "RESOLVED");

        DuplicateDetectionResult result = engine.decide(analysis, resource, null, List.of(candidate));

        Assertions.assertEquals(DuplicateDecision.POSSIBLE_RECURRING_ISSUE, result.decision());
    }

    @Test
    void rejectedComplaintIsSkipped() {
        AIResponse analysis = new AIResponse(
                "Meter Fault",
                "The electricity meter is not working.",
                "Electricity Department",
                "Power Supply",
                "HIGH",
                95,
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                "MTR001");
        ResourceExtractor.ExtractedResource resource = extractor.extract(
                "Electric meter MTR001 kaam nahi kar raha.",
                analysis.department(),
                analysis.category());

        DuplicateCandidate rejected = new DuplicateCandidate(
                1L,
                "GRV-2026-000001",
                0.99,
                "Electricity Department",
                "Power Supply",
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                resource.key(),
                null,
                "REJECTED");

        DuplicateDetectionResult result = engine.decide(analysis, resource, null, List.of(rejected));

        Assertions.assertEquals(DuplicateDecision.NOT_DUPLICATE, result.decision());
    }

    @Test
    void localAreaComplaintWithSameLocationIsDuplicate() {
        AIResponse analysis = new AIResponse(
                "Streetlight Issue",
                "Streetlights are not working in the colony.",
                "Electricity Department",
                "Street Lighting",
                "MEDIUM",
                88,
                "LOCAL_AREA",
                "UNKNOWN",
                null);
        String areaKey = extractor.areaKey("Shreeji Society, Ward 12", null, null, null, null);

        DuplicateCandidate candidate = new DuplicateCandidate(
                3L,
                "GRV-2026-000003",
                0.93,
                "Electricity Department",
                "Street Lighting",
                "LOCAL_AREA",
                "UNKNOWN",
                null,
                areaKey,
                "IN_PROGRESS");

        DuplicateDetectionResult result = engine.decide(analysis, extractor.extract("", null, null), areaKey, List.of(candidate));

        Assertions.assertEquals(DuplicateDecision.DUPLICATE, result.decision());
    }

    @Test
    void similarityBelowThresholdIsNotDuplicate() {
        AIResponse analysis = new AIResponse(
                "Meter Fault",
                "The electricity meter is not working.",
                "Electricity Department",
                "Power Supply",
                "HIGH",
                80,
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                "MTR001");
        ResourceExtractor.ExtractedResource resource = extractor.extract(
                "Electric meter MTR001 kaam nahi kar raha.",
                analysis.department(),
                analysis.category());

        DuplicateCandidate candidate = new DuplicateCandidate(
                1L,
                "GRV-2026-000001",
                0.75,
                "Electricity Department",
                "Power Supply",
                "INDIVIDUAL",
                "ELECTRICITY_METER",
                resource.key(),
                null,
                "IN_PROGRESS");

        DuplicateDetectionResult result = engine.decide(analysis, resource, null, List.of(candidate));

        Assertions.assertEquals(DuplicateDecision.NOT_DUPLICATE, result.decision());
    }
}
