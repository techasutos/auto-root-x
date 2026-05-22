package com.autorootx.service;

import com.autorootx.exception.ApiException;
import com.autorootx.model.AiUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class VertexAIService {

    private static final Logger log = LoggerFactory.getLogger(VertexAIService.class);

    @Value("${gcp.project:my-gcp-project}")
    private String projectId;

    @Value("${gcp.region:us-central1}")
    private String region;

    @Value("${vertex.model:gemini-1.5-pro}")
    private String model;

    @Value("${vertex.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${vertex.retry.initial-backoff-ms:400}")
    private long initialBackoffMs;

    @Value("${vertex.retry.max-backoff-ms:4000}")
    private long maxBackoffMs;

    @Value("${vertex.circuit.failure-threshold:5}")
    private int circuitFailureThreshold;

    @Value("${vertex.circuit.open-duration-ms:30000}")
    private long circuitOpenDurationMs;

    @Value("${vertex.ratelimit.max-calls-per-minute:60}")
    private int maxCallsPerMinute;

    @Value("${vertex.cost.input-per-1k-usd:0.00035}")
    private double inputCostPer1kUsd;

    @Value("${vertex.cost.output-per-1k-usd:0.00105}")
    private double outputCostPer1kUsd;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MIN_RETRY_ATTEMPTS = 1;

    private final MeterRegistry meterRegistry;

    private final AtomicInteger callsInWindow = new AtomicInteger(0);
    private volatile long windowStartEpochMs = System.currentTimeMillis();

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long circuitOpenUntilEpochMs = 0L;

    public VertexAIService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Send a typed analysis request to Vertex AI Gemini.
     *
     * @param analyzerType one of LOGS, IMAGE, OSS, GENERIC
     * @param input        raw input data to analyze
     * @return the AI-generated analysis text
     */
    public String analyze(String analyzerType, String input) throws Exception {
        return analyzeWithMetrics(analyzerType, input).text();
    }

    public CallResult analyzeWithMetrics(String analyzerType, String input) throws Exception {
        String prompt = buildPrompt(analyzerType, input);
        return callGeminiWithUsage(prompt);
    }

    /** Fallback overload used by LogsAnalyzer (passes raw string). */
    public String analyze(String input) throws Exception {
        return analyze("LOGS", input);
    }

    /**
     * Send a raw prompt to Gemini without wrapping it in a canned analyzer template.
     */
    public String complete(String prompt) throws Exception {
        return completeWithMetrics(prompt).text();
    }

    public CallResult completeWithMetrics(String prompt) throws Exception {
        return callGeminiWithUsage(prompt);
    }

    // -----------------------------------------------------------------------
    // Prompt engineering
    // -----------------------------------------------------------------------

    private String buildPrompt(String analyzerType, String input) {
        return switch (analyzerType.toUpperCase()) {
            case "LOGS" -> """
                You are a senior Site Reliability Engineer (SRE) with deep expertise in Google Cloud Platform.

                Analyze the following GCP Cloud Logging error entries and provide a structured root-cause analysis.

                ## Log Data
                %s

                ## Required Response Format (strictly follow this structure)
                **SEVERITY**: [CRITICAL|HIGH|MEDIUM|LOW]
                **CONFIDENCE**: [percentage]

                **ROOT CAUSE**:
                [Concise technical explanation of the underlying cause]

                **IMPACT**:
                [What services, users, or data are affected and how]

                **RECOMMENDED FIX**:
                [Step-by-step remediation with specific commands or configuration changes]

                **PREVENTION**:
                [How to prevent this in the future � monitoring, alerting, or architectural changes]
                """.formatted(input);

            case "IMAGE" -> """
                You are a container security expert specializing in CVE analysis and supply-chain security.

                Analyze the following container image for security vulnerabilities. The image is: %s

                Consider:
                - Known CVEs in common base images (Alpine, Debian, Ubuntu, Red Hat)
                - Outdated package versions
                - Mis-configurations (running as root, world-writable files, exposed secrets)
                - OWASP Container Security Top 10

                ## Required Response Format
                **SEVERITY**: [CRITICAL|HIGH|MEDIUM|LOW] (overall risk)
                **CONFIDENCE**: [percentage]

                **SUMMARY**:
                [Brief overview of the security posture]

                **ROOT CAUSE**:
                [Primary reason for the vulnerability risk]

                **IMPACT**:
                [Potential attack vectors and blast radius]

                **RECOMMENDED FIX**:
                [Specific upgrade commands and Dockerfile changes]
                """.formatted(input);

            case "OSS" -> """
                You are an application security engineer specializing in software composition analysis (SCA).

                Analyze the following open-source dependencies for known vulnerabilities using OSV.dev data:

                ## Dependencies
                %s

                ## Required Response Format
                **SEVERITY**: [CRITICAL|HIGH|MEDIUM|LOW] (highest severity found)
                **CONFIDENCE**: [percentage]

                **SUMMARY**:
                [Overview of dependency health and total risk]

                **ROOT CAUSE**:
                [Which packages introduce the most risk and why]

                **IMPACT**:
                [Data exposure, RCE, DoS, or other attack scenarios]

                **RECOMMENDED FIX**:
                [Specific version upgrades and dependency substitutions]
                """.formatted(input);

            default -> """
                You are a cloud security and reliability expert.

                Analyze the following data and provide a structured root-cause analysis report:

                ## Input
                %s

                ## Required Response Format
                **SEVERITY**: [CRITICAL|HIGH|MEDIUM|LOW]
                **CONFIDENCE**: [percentage]
                **ROOT CAUSE**: [explanation]
                **IMPACT**: [affected systems]
                **RECOMMENDED FIX**: [actionable steps]
                """.formatted(input);
        };
    }

    // -----------------------------------------------------------------------
    // Vertex AI Gemini API call with Application Default Credentials
    // -----------------------------------------------------------------------

    private CallResult callGeminiWithUsage(String prompt) throws Exception {
        long started = System.currentTimeMillis();

        if (isCircuitOpen(started)) {
            recordFailureMetric("circuit_open", started, 0, 0, 0.0);
            log.warn("event=vertex_call outcome=blocked reason=circuit_open model={} circuit_open_until_ms={}", model, circuitOpenUntilEpochMs);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "VERTEX_CIRCUIT_OPEN: Vertex AI circuit breaker is open. Please retry shortly.");
        }

        if (!tryAcquireCallSlot(started)) {
            recordFailureMetric("rate_limited", started, 0, 0, 0.0);
            log.warn("event=vertex_call outcome=blocked reason=rate_limited model={} max_calls_per_minute={}", model, maxCallsPerMinute);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "VERTEX_RATE_LIMITED: Too many AI requests. Please retry in a minute.");
        }

        int attempts = Math.max(maxRetryAttempts, MIN_RETRY_ATTEMPTS);
        int retriesUsed = 0;
        Exception lastException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                VertexRawResult raw = invokeGeminiOnce(prompt);
                recordSuccess(raw.latencyMs);

                int estimatedPromptTokens = estimateTokenCount(prompt);
                int inputTokens = raw.promptTokens > 0 ? raw.promptTokens : estimatedPromptTokens;
                int outputTokens = raw.candidateTokens > 0 ? raw.candidateTokens : estimateTokenCount(raw.text);
                int totalTokens = raw.totalTokens > 0 ? raw.totalTokens : inputTokens + outputTokens;
                double estimatedCostUsd = estimateCostUsd(inputTokens, outputTokens);

                recordSuccessMetric(raw.latencyMs, inputTokens, outputTokens, estimatedCostUsd);
                log.info(
                        "event=vertex_call outcome=success model={} latency_ms={} retries={} input_tokens={} output_tokens={} total_tokens={} estimated_cost_usd={}",
                        model,
                        raw.latencyMs,
                        retriesUsed,
                        inputTokens,
                        outputTokens,
                        totalTokens,
                        formatUsd(estimatedCostUsd)
                );

                return new CallResult(
                        raw.text,
                        "vertex-ai",
                        model,
                        raw.latencyMs,
                        retriesUsed,
                        inputTokens,
                        outputTokens,
                        totalTokens,
                        estimatedCostUsd,
                        null,
                        false,
                        false
                );
            } catch (VertexHttpException ex) {
                lastException = ex;
                boolean retryable = isRetryableHttpStatus(ex.statusCode);
                if (retryable && attempt < attempts) {
                    retriesUsed++;
                    backoffSleep(attempt);
                    continue;
                }
                recordFailure(System.currentTimeMillis());
                long latencyMs = System.currentTimeMillis() - started;
                String errorClass = ex.statusCode == 429 ? "vertex_429" : "vertex_http_" + ex.statusCode;
                recordFailureMetric(errorClass, started, 0, 0, 0.0);
                log.error(
                        "event=vertex_call outcome=error model={} error_class={} latency_ms={} retries={} http_status={} message={}",
                        model,
                        errorClass,
                        latencyMs,
                        retriesUsed,
                        ex.statusCode,
                        ex.getMessage()
                );
                throw mapToApiException(ex);
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < attempts) {
                    retriesUsed++;
                    backoffSleep(attempt);
                    continue;
                }
                recordFailure(System.currentTimeMillis());
                long latencyMs = System.currentTimeMillis() - started;
                String errorClass = classifyThrowable(ex);
                recordFailureMetric(errorClass, started, 0, 0, 0.0);
                log.error(
                        "event=vertex_call outcome=error model={} error_class={} latency_ms={} retries={} message={}",
                        model,
                        errorClass,
                        latencyMs,
                        retriesUsed,
                        ex.getMessage()
                );
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "VERTEX_CALL_FAILED: " + ex.getMessage());
            }
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY,
                "VERTEX_CALL_FAILED: " + (lastException == null ? "unknown error" : lastException.getMessage()));
    }

    private VertexRawResult invokeGeminiOnce(String prompt) throws Exception {
        long started = System.currentTimeMillis();
        String endpoint = String.format(
                "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
                region, projectId, region, model
        );

        String accessToken = getAccessToken();

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        String safePrompt = prompt.replace("\\", "\\\\")
                                  .replace("\"", "\\\"")
                                  .replace("\n", "\\n")
                                  .replace("\r", "\\r")
                                  .replace("\t", "\\t");

        String body = """
                {
                  "contents": [{
                    "parts": [{"text": "%s"}]
                  }],
                  "generationConfig": {
                    "temperature": 0.3,
                    "maxOutputTokens": 2048
                  }
                }
                """.formatted(safePrompt);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes());
        }

        int status = conn.getResponseCode();
        byte[] responseBytes;
        if (status < 400) {
            responseBytes = conn.getInputStream().readAllBytes();
        } else {
            if (conn.getErrorStream() != null) {
                responseBytes = conn.getErrorStream().readAllBytes();
            } else {
                responseBytes = new byte[0];
            }
        }

        String responseBody = new String(responseBytes);

        if (status >= 400) {
            throw new VertexHttpException(status, "Vertex AI error " + status + ": " + responseBody);
        }

        JsonNode root = MAPPER.readTree(responseBody);
        String text = extractText(root, responseBody);
        JsonNode usage = root.path("usageMetadata");
        int promptTokens = usage.path("promptTokenCount").asInt(-1);
        int candidateTokens = usage.path("candidatesTokenCount").asInt(-1);
        int totalTokens = usage.path("totalTokenCount").asInt(-1);
        long latencyMs = System.currentTimeMillis() - started;

        return new VertexRawResult(text, promptTokens, candidateTokens, totalTokens, latencyMs);
    }

    /**
     * Get OAuth2 access token using Application Default Credentials (ADC).
     * In GKE/Cloud Run this uses the workload identity / metadata server.
     * Locally, it uses gcloud application-default credentials.
     */
    private String getAccessToken() throws IOException {
        GoogleCredentials credentials = GoogleCredentials
                .getApplicationDefault()
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    private String extractText(JsonNode root, String rawJsonFallback) {
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                return parts.get(0).path("text").asText("");
            }
        }
        // Fallback: return raw JSON for debugging
        return rawJsonFallback;
    }

    private synchronized boolean tryAcquireCallSlot(long nowEpochMs) {
        if (maxCallsPerMinute <= 0) {
            return true;
        }

        if (nowEpochMs - windowStartEpochMs >= 60_000L) {
            windowStartEpochMs = nowEpochMs;
            callsInWindow.set(0);
        }

        if (callsInWindow.get() >= maxCallsPerMinute) {
            return false;
        }

        callsInWindow.incrementAndGet();
        return true;
    }

    private synchronized boolean isCircuitOpen(long nowEpochMs) {
        if (circuitOpenUntilEpochMs == 0L) {
            return false;
        }

        if (nowEpochMs >= circuitOpenUntilEpochMs) {
            circuitOpenUntilEpochMs = 0L;
            consecutiveFailures.set(0);
            return false;
        }

        return true;
    }

    private synchronized void recordSuccess(long latencyMs) {
        consecutiveFailures.set(0);
        circuitOpenUntilEpochMs = 0L;
    }

    private synchronized void recordFailure(long nowEpochMs) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= Math.max(1, circuitFailureThreshold)) {
            circuitOpenUntilEpochMs = nowEpochMs + Math.max(1L, circuitOpenDurationMs);
            log.warn("event=vertex_circuit state=open failures={} open_duration_ms={}", failures, circuitOpenDurationMs);
        }
    }

    private void recordSuccessMetric(long latencyMs, int inputTokens, int outputTokens, double estimatedCostUsd) {
        Counter.builder("autorootx.vertex.calls")
                .tag("outcome", "success")
                .tag("error_class", "none")
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("autorootx.vertex.tokens")
                .tag("kind", "input")
                .register(meterRegistry)
                .record(inputTokens);

        DistributionSummary.builder("autorootx.vertex.tokens")
                .tag("kind", "output")
                .register(meterRegistry)
                .record(outputTokens);

        DistributionSummary.builder("autorootx.vertex.cost.usd")
                .register(meterRegistry)
                .record(estimatedCostUsd);

        Timer.builder("autorootx.vertex.latency")
                .tag("outcome", "success")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(latencyMs));
    }

    private void recordFailureMetric(String errorClass, long startedAt, int inputTokens, int outputTokens, double estimatedCostUsd) {
        Counter.builder("autorootx.vertex.calls")
                .tag("outcome", "error")
                .tag("error_class", errorClass)
                .register(meterRegistry)
                .increment();

        Timer.builder("autorootx.vertex.latency")
                .tag("outcome", "error")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(System.currentTimeMillis() - startedAt));

        if (inputTokens > 0) {
            DistributionSummary.builder("autorootx.vertex.tokens")
                    .tag("kind", "input")
                    .register(meterRegistry)
                    .record(inputTokens);
        }

        if (outputTokens > 0) {
            DistributionSummary.builder("autorootx.vertex.tokens")
                    .tag("kind", "output")
                    .register(meterRegistry)
                    .record(outputTokens);
        }

        if (estimatedCostUsd > 0) {
            DistributionSummary.builder("autorootx.vertex.cost.usd")
                    .register(meterRegistry)
                    .record(estimatedCostUsd);
        }
    }

    private int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 4.0);
    }

    private double estimateCostUsd(int inputTokens, int outputTokens) {
        return (inputTokens / 1000.0) * inputCostPer1kUsd + (outputTokens / 1000.0) * outputCostPer1kUsd;
    }

    private boolean isRetryableHttpStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void backoffSleep(int attempt) {
        long waitMs = Math.min(maxBackoffMs, initialBackoffMs * (1L << (attempt - 1)));
        try {
            Thread.sleep(Math.max(1L, waitMs));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private ApiException mapToApiException(VertexHttpException ex) {
        if (ex.statusCode == 429) {
            return new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "VERTEX_429: Vertex AI quota/rate limit reached. Please retry shortly.");
        }
        if (ex.statusCode >= 400 && ex.statusCode < 500) {
            return new ApiException(HttpStatus.BAD_REQUEST,
                    "VERTEX_4XX: Vertex AI rejected the request (" + ex.statusCode + ").");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY,
                "VERTEX_5XX: Vertex AI upstream error (" + ex.statusCode + ").");
    }

    private String classifyThrowable(Exception ex) {
        if (ex instanceof ApiException apiException) {
            if (apiException.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                return "rate_limited";
            }
            if (apiException.getStatus() == HttpStatus.SERVICE_UNAVAILABLE) {
                return "circuit_open";
            }
            return "api_exception";
        }
        if (ex instanceof IOException) {
            return "io_exception";
        }
        return ex.getClass().getSimpleName().toLowerCase();
    }

    private String formatUsd(double amount) {
        return String.format("%.6f", amount);
    }

    public AiUsage toAiUsage(CallResult callResult) {
        AiUsage usage = new AiUsage();
        usage.provider = callResult.provider();
        usage.model = callResult.model();
        usage.callCount = 1;
        usage.latencyMs = callResult.latencyMs();
        usage.retries = callResult.retries();
        usage.inputTokens = callResult.inputTokens();
        usage.outputTokens = callResult.outputTokens();
        usage.totalTokens = callResult.totalTokens();
        usage.estimatedCostUsd = callResult.estimatedCostUsd();
        usage.errorClass = callResult.errorClass();
        usage.rateLimited = callResult.rateLimited();
        usage.circuitOpen = callResult.circuitOpen();
        return usage;
    }

    public AiUsage aggregateUsage(AiUsage first, AiUsage second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }

        AiUsage merged = new AiUsage();
        merged.provider = first.provider;
        merged.model = (first.model != null && second.model != null && !first.model.equals(second.model))
                ? "mixed"
                : (first.model != null ? first.model : second.model);

        merged.callCount = safeInt(first.callCount) + safeInt(second.callCount);
        merged.latencyMs = safeLong(first.latencyMs) + safeLong(second.latencyMs);
        merged.retries = safeInt(first.retries) + safeInt(second.retries);
        merged.inputTokens = safeInt(first.inputTokens) + safeInt(second.inputTokens);
        merged.outputTokens = safeInt(first.outputTokens) + safeInt(second.outputTokens);
        merged.totalTokens = safeInt(first.totalTokens) + safeInt(second.totalTokens);
        merged.estimatedCostUsd = safeDouble(first.estimatedCostUsd) + safeDouble(second.estimatedCostUsd);
        merged.errorClass = first.errorClass != null ? first.errorClass : second.errorClass;
        merged.rateLimited = Boolean.TRUE.equals(first.rateLimited) || Boolean.TRUE.equals(second.rateLimited);
        merged.circuitOpen = Boolean.TRUE.equals(first.circuitOpen) || Boolean.TRUE.equals(second.circuitOpen);
        return merged;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    public AiUsage usageForException(Exception ex) {
        AiUsage usage = new AiUsage();
        usage.provider = "vertex-ai";
        usage.model = model;
        usage.callCount = 1;
        usage.errorClass = classifyThrowable(ex);
        usage.rateLimited = ex instanceof ApiException api && api.getStatus() == HttpStatus.TOO_MANY_REQUESTS;
        usage.circuitOpen = ex instanceof ApiException api && api.getStatus() == HttpStatus.SERVICE_UNAVAILABLE;
        usage.estimatedCostUsd = 0.0;
        usage.inputTokens = 0;
        usage.outputTokens = 0;
        usage.totalTokens = 0;
        usage.retries = 0;
        usage.latencyMs = 0L;
        return usage;
    }

    public record CallResult(
            String text,
            String provider,
            String model,
            long latencyMs,
            int retries,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            double estimatedCostUsd,
            String errorClass,
            boolean rateLimited,
            boolean circuitOpen
    ) {}

    private record VertexRawResult(
            String text,
            int promptTokens,
            int candidateTokens,
            int totalTokens,
            long latencyMs
    ) {}

    private static class VertexHttpException extends RuntimeException {
        private final int statusCode;

        private VertexHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
