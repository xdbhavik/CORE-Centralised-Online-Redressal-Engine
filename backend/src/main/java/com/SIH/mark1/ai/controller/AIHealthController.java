package com.SIH.mark1.ai.controller;

import com.SIH.mark1.ai.client.OpenRouterClient;
import com.SIH.mark1.ai.client.QdrantClient;
import com.SIH.mark1.ai.qdrant.RagProperties;
import com.SIH.mark1.ai.rag.EmbeddingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostics for the AI subsystem.
 *
 * <p>Exists because the assistant had no way to tell you <em>why</em> it was misbehaving.
 * Every dependency (LLM, Qdrant, embedding model) failed softly into a canned reply, so a
 * dead API key and a working system looked identical from the outside. This endpoint answers
 * the three questions that actually matter when the chatbot "doesn't work":</p>
 *
 * <ol>
 *   <li>Is the LLM reachable, and if not, what did it say? (status code + body)</li>
 *   <li>Is Qdrant up, and do the configured collections exist?</li>
 *   <li>Are we running true semantic embeddings, or silently degraded to the lexical
 *       fallback — the single most likely cause of "RAG returns nothing useful"?</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AIHealthController {

    private final OpenRouterClient llmClient;
    private final QdrantClient qdrantClient;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;

    public AIHealthController(OpenRouterClient llmClient,
                              QdrantClient qdrantClient,
                              EmbeddingService embeddingService,
                              RagProperties ragProperties) {
        this.llmClient = llmClient;
        this.qdrantClient = qdrantClient;
        this.embeddingService = embeddingService;
        this.ragProperties = ragProperties;
    }

    /**
     * Reports subsystem status.
     *
     * @param probeLlm when true, issues one real (billable) completion to verify the API key
     *                 end-to-end. Off by default so monitoring cannot burn quota.
     */
    @GetMapping("/health")
    public Map<String, Object> health(@RequestParam(defaultValue = "false") boolean probeLlm) {
        Map<String, Object> report = new LinkedHashMap<>();

        report.put("embedding", embeddingStatus());
        report.put("qdrant", qdrantStatus());
        report.put("llm", llmStatus(probeLlm));
        report.put("status", overallStatus());

        return report;
    }

    private Map<String, Object> embeddingStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean semantic = embeddingService.isSemantic();
        status.put("model", embeddingService.modelId());
        status.put("dimensions", embeddingService.dimensions());
        status.put("semantic", semantic);
        status.put("state", semantic ? "UP" : "DEGRADED");
        if (!semantic) {
            status.put("impact", "Lexical fallback active: a Hindi/Hinglish query cannot match "
                    + "an English document. Check startup logs for the model load failure.");
        }
        return status;
    }

    private Map<String, Object> qdrantStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean configured = qdrantClient.isConfigured();
        status.put("configured", configured);
        if (!configured) {
            status.put("state", "DOWN");
            status.put("impact", "ai.qdrant.base-url is not set — no retrieval is possible.");
            return status;
        }

        boolean reachable = qdrantClient.ping();
        status.put("reachable", reachable);
        status.put("state", reachable ? "UP" : "DOWN");

        Map<String, Object> collections = new LinkedHashMap<>();
        if (reachable) {
            for (String name : ragProperties.safeCollections()) {
                collections.put(name, qdrantClient.collectionExists(name) ? "EXISTS" : "MISSING");
            }
        }
        status.put("collections", collections);
        status.put("minScore", ragProperties.resolvedMinScore());
        return status;
    }

    private Map<String, Object> llmStatus(boolean probe) {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean configured = llmClient.isConfigured();
        status.put("configured", configured);
        status.put("totalCalls", llmClient.totalCalls());
        status.put("failedCalls", llmClient.failedCalls());
        status.put("lastSuccessAt", String.valueOf(llmClient.lastSuccessAt()));

        if (llmClient.lastError() != null) {
            status.put("lastError", llmClient.lastError());
            status.put("lastErrorAt", String.valueOf(llmClient.lastErrorAt()));
        }

        if (!configured) {
            status.put("state", "DOWN");
            status.put("impact", "ai.chat.api-key/base-url missing — replies fall back to canned text.");
            return status;
        }

        if (probe) {
            boolean ok = llmClient.probe();
            status.put("probed", true);
            status.put("state", ok ? "UP" : "DOWN");
            if (!ok) {
                status.put("impact", "Live probe failed — see lastError for the provider's response.");
            }
        } else {
            status.put("probed", false);
            status.put("state", "CONFIGURED");
            status.put("hint", "Call /api/v1/ai/health?probeLlm=true to verify the key end-to-end.");
        }
        return status;
    }

    /** Worst-of rollup: DOWN if retrieval is impossible, DEGRADED if quality is compromised. */
    private String overallStatus() {
        if (!qdrantClient.isConfigured() || !qdrantClient.ping() || !llmClient.isConfigured()) {
            return "DOWN";
        }
        if (!embeddingService.isSemantic()) {
            return "DEGRADED";
        }
        return "UP";
    }
}
