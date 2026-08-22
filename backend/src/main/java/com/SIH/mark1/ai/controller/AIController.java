package com.SIH.mark1.ai.controller;

import com.SIH.mark1.ai.dto.AIResponse;
import com.SIH.mark1.ai.dto.ChatRequest;
import com.SIH.mark1.ai.dto.ChatResponse;
import com.SIH.mark1.ai.dto.DuplicateCheckRequest;
import com.SIH.mark1.ai.dto.DuplicateCheckResponse;
import com.SIH.mark1.ai.dto.RagSearchResponse;
import com.SIH.mark1.ai.dto.SummaryRequest;
import com.SIH.mark1.ai.dto.SummaryResponse;
import com.SIH.mark1.ai.dto.TranslationRequest;
import com.SIH.mark1.ai.dto.TranslationResponse;
import com.SIH.mark1.ai.rag.RAGService;
import com.SIH.mark1.ai.service.AIService;
import com.SIH.mark1.ai.service.ChatAssistantService;
import com.SIH.mark1.ai.service.DuplicateDetectionService;
import com.SIH.mark1.ai.service.SummarizationService;
import com.SIH.mark1.ai.service.TranslationService;
import com.SIH.mark1.dto.request.CreateComplaintRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AIController {

    private final AIService aiService;
    private final ChatAssistantService chatAssistantService;
    private final TranslationService translationService;
    private final SummarizationService summarizationService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final RAGService ragService;

    public AIController(AIService aiService,
                        ChatAssistantService chatAssistantService,
                        TranslationService translationService,
                        SummarizationService summarizationService,
                        DuplicateDetectionService duplicateDetectionService,
                        RAGService ragService) {
        this.aiService = aiService;
        this.chatAssistantService = chatAssistantService;
        this.translationService = translationService;
        this.summarizationService = summarizationService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.ragService = ragService;
    }

    @PostMapping("/ai/analyze/{complaintId}")
    public AIResponse analyzeByComplaintId(@PathVariable Long complaintId) {
        return aiService.analyze(complaintId);
    }

    @PostMapping("/ai/analyze")
    public AIResponse analyze(@Valid @RequestBody CreateComplaintRequest request) {
        return aiService.analyze(request);
    }

    @PostMapping("/ai/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatAssistantService.chat(request);
    }

    @PostMapping("/ai/translate")
    public TranslationResponse translate(@Valid @RequestBody TranslationRequest request) {
        return translationService.translate(request);
    }

    @PostMapping("/ai/summarize")
    public SummaryResponse summarize(@Valid @RequestBody SummaryRequest request) {
        return summarizationService.summarize(request);
    }

    @PostMapping("/ai/check-duplicate")
    public DuplicateCheckResponse checkDuplicate(@Valid @RequestBody DuplicateCheckRequest request) {
        return duplicateDetectionService.check(request);
    }

    @PostMapping("/rag/reindex")
    public Map<String, Object> reindex() {
        ragService.reindex();
        return Map.of("indexedChunks", ragService.indexedChunks());
    }

    @GetMapping("/rag/search")
    public RagSearchResponse search(@RequestParam String query) {
        RAGService.RagContext context = ragService.retrieveContext(query);
        return new RagSearchResponse(query, context.chunks().stream()
                .map(result -> new RagSearchResponse.SearchResult(
                        result.source(),
                        result.content(),
                        result.score()
                ))
                .toList());
    }
}
