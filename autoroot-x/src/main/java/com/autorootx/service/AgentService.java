package com.autorootx.service;

import com.autorootx.model.AgentRequest;
import com.autorootx.model.AgentResult;
import com.autorootx.model.AiUsage;
import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import com.autorootx.plugin.Analyzer;
import com.autorootx.plugin.AnalyzerMeta;
import com.autorootx.plugin.AnalyzerRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnalyzerRegistry registry;
    private final VertexAIService vertexAIService;

    public AgentService(AnalyzerRegistry registry, VertexAIService vertexAIService) {
        this.registry = registry;
        this.vertexAIService = vertexAIService;
    }

    public AgentResult analyze(AgentRequest request) {
        List<AnalyzerMeta> availableAnalyzers = registry.list();
        AgentDecision decision = decide(request, availableAnalyzers);

        AgentResult result;
        if (!"GENERIC".equalsIgnoreCase(decision.selectedAnalyzerId)) {
            result = runAnalyzer(decision);
        } else {
            result = runDirectGemini(decision, request);
        }

        if (result.analysis != null) {
            result.analysis.aiUsage = vertexAIService.aggregateUsage(result.analysis.aiUsage, decision.routingUsage);
        }
        return result;
    }

    private AgentResult runAnalyzer(AgentDecision decision) {
        Analyzer analyzer = registry.get(decision.selectedAnalyzerId);
        AnalysisRequest analysisRequest = new AnalysisRequest();
        analysisRequest.analyzerId = decision.selectedAnalyzerId;
        analysisRequest.payload = decision.payload;

        AnalysisResult analysis = analyzer.analyze(analysisRequest);

        AgentResult result = new AgentResult();
        result.selectedAnalyzerId = decision.selectedAnalyzerId;
        result.selectedAnalyzerName = decision.selectedAnalyzerName;
        result.reason = decision.reason;
        result.mode = "tool";
        result.payload = decision.payload;
        result.analysis = analysis;
        return result;
    }

    private AgentResult runDirectGemini(AgentDecision decision, AgentRequest request) {
        String prompt = """
                You are the AutoRoot-X incident triage agent.

                User problem:
                %s

                Optional context:
                %s

                Provide a concise root-cause analysis and concrete remediation.
                Required format:
                SEVERITY: [CRITICAL|HIGH|MEDIUM|LOW]
                CONFIDENCE: [percentage]
                ROOT CAUSE: [short explanation]
                IMPACT: [short explanation]
                RECOMMENDED FIX: [actionable steps]
                """.formatted(nvl(request.problem), nvl(request.context));

        AnalysisResult analysis = new AnalysisResult();
        try {
            VertexAIService.CallResult call = vertexAIService.completeWithMetrics(prompt);
            analysis.summary = call.text();
            analysis.aiUsage = vertexAIService.toAiUsage(call);
            analysis.severity = "UNKNOWN";
            analysis.confidence = "N/A";
        } catch (Exception e) {
            log.error("Direct Gemini analysis failed: {}", e.getMessage(), e);
            analysis.summary = "Direct Gemini analysis unavailable: " + e.getMessage();
            analysis.severity = "UNKNOWN";
            analysis.confidence = "N/A";
            analysis.aiUsage = vertexAIService.usageForException(e);
        }

        AgentResult result = new AgentResult();
        result.selectedAnalyzerId = decision.selectedAnalyzerId;
        result.selectedAnalyzerName = decision.selectedAnalyzerName;
        result.reason = decision.reason;
        result.mode = "direct";
        result.payload = decision.payload;
        result.analysis = analysis;
        return result;
    }

    private AgentDecision decide(AgentRequest request, List<AnalyzerMeta> availableAnalyzers) {
        String prompt = buildDecisionPrompt(request, availableAnalyzers);

        try {
            VertexAIService.CallResult call = vertexAIService.completeWithMetrics(prompt);
            String raw = call.text();
            JsonNode root = extractJson(raw);

            AgentDecision decision = new AgentDecision();
            decision.selectedAnalyzerId = text(root, "selectedAnalyzerId", "GENERIC").toUpperCase();
            decision.selectedAnalyzerName = text(root, "selectedAnalyzerName", "General Gemini Triage");
            decision.reason = text(root, "reason", "Gemini routed the request to a direct analysis path.");
            decision.payload = toPayload(root.path("payload"));
            decision.routingUsage = vertexAIService.toAiUsage(call);

            if (!"GENERIC".equals(decision.selectedAnalyzerId) && !registryContains(availableAnalyzers, decision.selectedAnalyzerId)) {
                decision.selectedAnalyzerId = "GENERIC";
                decision.selectedAnalyzerName = "General Gemini Triage";
                decision.reason = "Selected analyzer was unavailable, so the request fell back to direct Gemini analysis.";
                decision.payload = Map.of();
            }

            if ("IMAGE".equals(decision.selectedAnalyzerId) && blankString(decision.payload.get("image"))) {
                decision.selectedAnalyzerId = "GENERIC";
                decision.selectedAnalyzerName = "General Gemini Triage";
                decision.reason = "Gemini could not extract an image reference, so the request fell back to direct analysis.";
                decision.payload = Map.of();
            }

            if ("OSS".equals(decision.selectedAnalyzerId) && blankString(decision.payload.get("dependency"))) {
                decision.selectedAnalyzerId = "GENERIC";
                decision.selectedAnalyzerName = "General Gemini Triage";
                decision.reason = "Gemini could not extract a dependency coordinate, so the request fell back to direct analysis.";
                decision.payload = Map.of();
            }

            return decision;
        } catch (Exception e) {
            log.warn("Agent routing failed, falling back to direct Gemini analysis: {}", e.getMessage());
            AgentDecision fallback = new AgentDecision();
            fallback.selectedAnalyzerId = "GENERIC";
            fallback.selectedAnalyzerName = "General Gemini Triage";
            fallback.reason = "Routing failed, so the request used direct Gemini analysis.";
            fallback.payload = Map.of();
            fallback.routingUsage = vertexAIService.usageForException(e);
            return fallback;
        }
    }

    private String buildDecisionPrompt(AgentRequest request, List<AnalyzerMeta> availableAnalyzers) {
        StringBuilder catalog = new StringBuilder();
        for (AnalyzerMeta analyzer : availableAnalyzers) {
            catalog.append("- ")
                    .append(analyzer.id)
                    .append(" | ")
                    .append(analyzer.name)
                    .append(" | category=")
                    .append(analyzer.category)
                    .append(" | inputs=")
                    .append(analyzer.inputs)
                    .append("\n");
        }

        return """
                You are the routing brain for AutoRoot-X.

                Choose exactly one of the enabled analyzers when it fits the problem. If none fit, return GENERIC.

                Available analyzers:
                %s

                User problem:
                %s

                Optional context:
                %s

                Return STRICT JSON ONLY with this schema:
                {
                  "selectedAnalyzerId": "LOGS|IMAGE|OSS|GENERIC",
                  "selectedAnalyzerName": "string",
                  "reason": "short explanation",
                  "payload": { "image": "...", "dependency": "..." }
                }

                Payload rules:
                - LOGS uses an empty payload.
                - IMAGE should include "image" if an image reference is mentioned.
                - OSS should include "dependency" if a Maven dependency coordinate is mentioned.
                - GENERIC should use an empty payload.
                """.formatted(catalog, nvl(request.problem), nvl(request.context));
    }

    private JsonNode extractJson(String raw) throws Exception {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return MAPPER.readTree(raw.substring(start, end + 1));
        }
        throw new IllegalArgumentException("Agent response did not contain JSON");
    }

    private Map<String, Object> toPayload(JsonNode node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return payload;
        }
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                payload.put(entry.getKey(), value.asText());
            } else if (value.isNumber()) {
                payload.put(entry.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                payload.put(entry.getKey(), value.asBoolean());
            } else if (!value.isNull()) {
                payload.put(entry.getKey(), value.toString());
            }
        });
        return payload;
    }

    private boolean registryContains(List<AnalyzerMeta> availableAnalyzers, String analyzerId) {
        for (AnalyzerMeta analyzer : availableAnalyzers) {
            if (analyzer.id != null && analyzer.id.equalsIgnoreCase(analyzerId)) {
                return true;
            }
        }
        return false;
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.hasNonNull(field)) {
            return fallback;
        }
        String value = node.path(field).asText();
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean blankString(Object value) {
        return value == null || value.toString().isBlank();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private static class AgentDecision {
        String selectedAnalyzerId;
        String selectedAnalyzerName;
        String reason;
        Map<String, Object> payload;
        AiUsage routingUsage;
    }
}