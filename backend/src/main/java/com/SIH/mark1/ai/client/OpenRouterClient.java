package com.SIH.mark1.ai.client;

import com.SIH.mark1.ai.config.AIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client for the OpenAI-compatible chat-completions endpoint (currently NVIDIA NIM).
 *
 * <p>The previous version ended every failure path in {@code catch (Exception ignored)}. An
 * expired API key, a renamed model, a 429, or a timeout all produced the same thing: an empty
 * Optional and total silence in the logs. The assistant would fall back to a canned reply and
 * look "broken for no reason", with nothing to diagnose from. Every failure is now logged with
 * its HTTP status and response body, and the last failure is retained so
 * {@code /api/v1/ai/health} can report it.</p>
 *
 * <p>Also adds a bounded retry with backoff for transient faults (429 / 5xx), which are
 * routine on free inference tiers, while deliberately <em>not</em> retrying 4xx auth errors —
 * retrying a bad key just multiplies the latency of a guaranteed failure.</p>
 */
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 400L;

    private final AIProperties properties;
    private final RestClient restClient;
    private final String publicUrl;

    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong failedCalls = new AtomicLong();
    private volatile String lastError;
    private volatile Instant lastErrorAt;
    private volatile Instant lastSuccessAt;

    public OpenRouterClient(AIProperties properties,
                            RestClient aiRestClient,
                            @Value("${app.public-url:http://localhost:8080}") String publicUrl) {
        this.properties = properties;
        this.restClient = aiRestClient;
        this.publicUrl = publicUrl;
    }

    /** Whether a base URL and API key are present. Does not prove the key is valid. */
    public boolean isConfigured() {
        AIProperties.ChatModelConfig chat = properties.chat();
        return chat != null
                && chat.baseUrl() != null && !chat.baseUrl().isBlank()
                && chat.apiKey() != null && !chat.apiKey().isBlank();
    }

    public Optional<String> complete(String prompt) {
        return complete(prompt, 0.1d);
    }

    /**
     * Sends a single-turn completion request.
     *
     * @return the assistant text, or empty if the call could not be completed — in which case
     *         the reason has been logged and is available via {@link #lastError()}.
     */
    public Optional<String> complete(String prompt, double temperature) {
        if (!isConfigured()) {
            recordFailure("LLM not configured: ai.chat.base-url or ai.chat.api-key is missing");
            log.error("❌ LLM call skipped — ai.chat.api-key/base-url not configured. "
                    + "The assistant will use canned fallback replies.");
            return Optional.empty();
        }

        String model = properties.chat().model();
        String url = properties.chat().baseUrl() + "/chat/completions";
        long startedAt = System.currentTimeMillis();
        totalCalls.incrementAndGet();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Map<?, ?> response = restClient.post()
                        .uri(url)
                        .header("Authorization", "Bearer " + properties.chat().apiKey())
                        .header("HTTP-Referer", publicUrl)
                        .header("X-Title", "AI Based Grievance System")
                        .body(Map.of(
                                "model", model,
                                "messages", List.of(Map.of("role", "user", "content", prompt)),
                                "temperature", temperature
                        ))
                        // Surface the server's own error text instead of a generic exception.
                        .exchange((request, clientResponse) -> {
                            HttpStatusCode status = clientResponse.getStatusCode();
                            if (status.isError()) {
                                String body = readBody(clientResponse);
                                throw new LlmCallException(status.value(), body);
                            }
                            return clientResponse.bodyTo(Map.class);
                        });

                Optional<String> content = extractContent(response);
                if (content.isEmpty()) {
                    recordFailure("LLM returned no content (model=" + model + ")");
                    log.warn("⚠️  LLM responded without usable content. model={} response={}",
                            model, truncate(String.valueOf(response), 500));
                    return Optional.empty();
                }

                lastSuccessAt = Instant.now();
                log.debug("✅ LLM ok: model={} attempt={} latency={}ms chars={}",
                        model, attempt, System.currentTimeMillis() - startedAt, content.get().length());
                return content;

            } catch (LlmCallException ex) {
                boolean retryable = ex.status() == 429 || ex.status() >= 500;
                String detail = "HTTP " + ex.status() + " from " + url
                        + " (model=" + model + "): " + truncate(ex.body(), 400);

                if (!retryable || attempt == MAX_ATTEMPTS) {
                    recordFailure(detail);
                    if (ex.status() == 401 || ex.status() == 403) {
                        log.error("❌ LLM rejected credentials — check ai.chat.api-key. {}", detail);
                    } else if (ex.status() == 404) {
                        log.error("❌ LLM model or endpoint not found — check ai.chat.model "
                                + "and ai.chat.base-url. {}", detail);
                    } else {
                        log.error("❌ LLM call failed after {} attempt(s). {}", attempt, detail);
                    }
                    return Optional.empty();
                }
                log.warn("⚠️  Transient LLM failure (attempt {}/{}), retrying. {}",
                        attempt, MAX_ATTEMPTS, detail);
                sleepBackoff(attempt);

            } catch (Exception ex) {
                // Network/timeout/parse failures. Retry transient ones, but always log.
                String detail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                if (attempt == MAX_ATTEMPTS) {
                    recordFailure(detail);
                    log.error("❌ LLM call failed after {} attempt(s) to {} (model={}). {}",
                            attempt, url, model, detail);
                    return Optional.empty();
                }
                log.warn("⚠️  LLM call error (attempt {}/{}), retrying. {}",
                        attempt, MAX_ATTEMPTS, detail);
                sleepBackoff(attempt);
            }
        }
        return Optional.empty();
    }

    // ──────────────────────────────────────────
    // Diagnostics, consumed by the AI health endpoint
    // ──────────────────────────────────────────

    public String lastError() {
        return lastError;
    }

    public Instant lastErrorAt() {
        return lastErrorAt;
    }

    public Instant lastSuccessAt() {
        return lastSuccessAt;
    }

    public long totalCalls() {
        return totalCalls.get();
    }

    public long failedCalls() {
        return failedCalls.get();
    }

    /** Cheap liveness probe used by the health endpoint. */
    public boolean probe() {
        return complete("Reply with the single word: OK", 0.0d).isPresent();
    }

    // ──────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────

    private Optional<String> extractContent(Map<?, ?> response) {
        Object choices = response == null ? null : response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> choice) {
            Object message = choice.get("message");
            if (message instanceof Map<?, ?> messageMap) {
                Object content = messageMap.get("content");
                if (content != null && !content.toString().isBlank()) {
                    return Optional.of(content.toString());
                }
            }
        }
        return Optional.empty();
    }

    private void recordFailure(String detail) {
        failedCalls.incrementAndGet();
        this.lastError = detail;
        this.lastErrorAt = Instant.now();
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Duration.ofMillis(INITIAL_BACKOFF_MILLIS * (1L << (attempt - 1))));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String readBody(org.springframework.http.client.ClientHttpResponse response) {
        try (var stream = response.getBody()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "<unreadable response body: " + ex.getMessage() + ">";
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max) + "…";
    }

    /** Carries the HTTP status and server message so callers can log something actionable. */
    private static class LlmCallException extends RuntimeException {
        private final int status;
        private final String body;

        LlmCallException(int status, String body) {
            super("LLM HTTP " + status);
            this.status = status;
            this.body = body;
        }

        int status() {
            return status;
        }

        String body() {
            return body;
        }
    }
}
