package com.SIH.mark1.ai.service;

import com.SIH.mark1.ai.client.QdrantClient;
import com.SIH.mark1.ai.config.DuplicateProperties;
import com.SIH.mark1.ai.dto.AIResponse;
import com.SIH.mark1.ai.dto.DuplicateCheckRequest;
import com.SIH.mark1.ai.dto.DuplicateCheckResponse;
import com.SIH.mark1.ai.duplicate.ComplaintEmbeddingTextBuilder;
import com.SIH.mark1.ai.duplicate.ComplaintScope;
import com.SIH.mark1.ai.duplicate.DuplicateCandidate;
import com.SIH.mark1.ai.duplicate.DuplicateDecision;
import com.SIH.mark1.ai.duplicate.DuplicateDecisionEngine;
import com.SIH.mark1.ai.duplicate.DuplicateDetectionResult;
import com.SIH.mark1.ai.duplicate.ResourceExtractor;
import com.SIH.mark1.ai.embedding.EmbeddingGenerator;
import com.SIH.mark1.ai.qdrant.QdrantSearchResult;
import com.SIH.mark1.dto.request.CreateComplaintRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Duplicate complaint detection using Qdrant ANN search.
 * Vector similarity is used for candidate generation; the {@link DuplicateDecisionEngine}
 * applies scope, resource, location, and status rules for the final decision.
 */
@Service
public class DuplicateDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionService.class);
    private static final String COLLECTION = "complaint_history";

    private final QdrantClient qdrantClient;
    private final EmbeddingGenerator embeddingGenerator;
    private final ComplaintEmbeddingTextBuilder embeddingTextBuilder;
    private final ResourceExtractor resourceExtractor;
    private final DuplicateDecisionEngine decisionEngine;
    private final DuplicateProperties duplicateProperties;
    private final AIService aiService;

    public DuplicateDetectionService(QdrantClient qdrantClient,
                                      EmbeddingGenerator embeddingGenerator,
                                      ComplaintEmbeddingTextBuilder embeddingTextBuilder,
                                      ResourceExtractor resourceExtractor,
                                      DuplicateDecisionEngine decisionEngine,
                                      DuplicateProperties duplicateProperties,
                                      AIService aiService) {
        this.qdrantClient = qdrantClient;
        this.embeddingGenerator = embeddingGenerator;
        this.embeddingTextBuilder = embeddingTextBuilder;
        this.resourceExtractor = resourceExtractor;
        this.decisionEngine = decisionEngine;
        this.duplicateProperties = duplicateProperties;
        this.aiService = aiService;
    }

    public DuplicateCheckResponse check(DuplicateCheckRequest request) {
        CreateComplaintRequest complaintRequest = toComplaintRequest(request);
        AIResponse analysis = analyzeSafely(complaintRequest);
        DuplicateDetectionResult result = check(complaintRequest, analysis);
        return toResponse(result);
    }

    public DuplicateDetectionResult check(CreateComplaintRequest request, AIResponse analysis) {
        if (!duplicateProperties.enabled()) {
            return DuplicateDetectionResult.unavailable("Duplicate detection is disabled.");
        }
        if (!qdrantClient.isConfigured()) {
            log.debug("Qdrant not configured — duplicate detection unavailable.");
            return DuplicateDetectionResult.unavailable("Qdrant not configured — duplicate check skipped.");
        }

        String text = embeddingTextBuilder.build(request, analysis);
        double[] embedding = embeddingGenerator.generate(text);
        float[] floatVector = toFloat(embedding);

        List<QdrantSearchResult> results = qdrantClient.searchPoints(
                COLLECTION, floatVector, duplicateProperties.topK(), true);

        if (results.isEmpty()) {
            return DuplicateDetectionResult.notDuplicate(
                    ComplaintScope.from(analysis != null ? analysis.scope() : null),
                    "No existing complaints found.");
        }

        ResourceExtractor.ExtractedResource resource = resourceExtractor.fromAnalysis(analysis, text);
        String areaKey = resourceExtractor.areaKey(
                request.getAddress(), null, null, null, null);
        List<DuplicateCandidate> candidates = results.stream()
                .map(this::toCandidate)
                .filter(candidate -> candidate.complaintId() != null)
                .toList();

        DuplicateDetectionResult result = decisionEngine.decide(analysis, resource, areaKey, candidates);
        if (result.decision() == DuplicateDecision.DUPLICATE) {
            log.info("Duplicate detected: complaint_id={}, score={}", result.existingComplaintId(), result.similarity());
        }
        return result;
    }

    private CreateComplaintRequest toComplaintRequest(DuplicateCheckRequest request) {
        CreateComplaintRequest complaintRequest = new CreateComplaintRequest();
        complaintRequest.setTitle(request.title());
        complaintRequest.setDescription(request.description());
        complaintRequest.setAddress(request.address());
        if (request.latitude() != null) {
            complaintRequest.setLatitude(BigDecimal.valueOf(request.latitude()));
        }
        if (request.longitude() != null) {
            complaintRequest.setLongitude(BigDecimal.valueOf(request.longitude()));
        }
        return complaintRequest;
    }

    private AIResponse analyzeSafely(CreateComplaintRequest request) {
        try {
            return aiService.analyze(request);
        } catch (Exception ex) {
            log.warn("AI analysis unavailable for duplicate check: {}", ex.getMessage());
            return new AIResponse(
                    "Complaint Analysis",
                    request.getDescription(),
                    "UNKNOWN",
                    "UNKNOWN",
                    "MEDIUM",
                    0);
        }
    }

    private DuplicateCheckResponse toResponse(DuplicateDetectionResult result) {
        boolean duplicate = result.decision() == DuplicateDecision.DUPLICATE;
        return new DuplicateCheckResponse(
                duplicate,
                result.existingComplaintId(),
                result.similarity(),
                result.reasons(),
                result.decision().name(),
                result.existingComplaintNumber(),
                result.existingStatus(),
                result.scope() != null ? result.scope().name() : null,
                result.resourceMatch(),
                result.locationMatch());
    }

    private float[] toFloat(double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            floats[i] = (float) doubles[i];
        }
        return floats;
    }

    private Long parseComplaintId(String pointId) {
        try {
            return Long.parseLong(pointId);
        } catch (NumberFormatException ex) {
            log.warn("Could not parse complaint ID from Qdrant point ID: {}", pointId);
            return null;
        }
    }

    private DuplicateCandidate toCandidate(QdrantSearchResult result) {
        return new DuplicateCandidate(
                parseLong(firstPayload(result, "complaintId")) != null
                        ? parseLong(firstPayload(result, "complaintId"))
                        : parseComplaintId(result.id()),
                firstPayload(result, "complaintNumber", "complaintNo"),
                result.score(),
                firstPayload(result, "department", "departmentName"),
                firstPayload(result, "category", "categoryName"),
                firstPayload(result, "scope"),
                firstPayload(result, "resourceType"),
                firstPayload(result, "resourceKey"),
                firstPayload(result, "areaKey"),
                firstPayload(result, "status", "statusCode")
        );
    }

    private String firstPayload(QdrantSearchResult result, String... keys) {
        for (String key : keys) {
            String value = result.payloadString(key);
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
