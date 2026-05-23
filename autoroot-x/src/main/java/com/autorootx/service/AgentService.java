package com.autorootx.service;

import com.autorootx.model.AgentRequest;
import com.autorootx.model.AgentResult;
import com.autorootx.model.AnalysisResult;
import com.autorootx.model.EvidenceBundle;
import com.autorootx.model.EvidenceFinding;
import com.autorootx.service.evidence.EvidenceCollectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final EvidenceCollectorService evidenceCollector;
    private final VertexAIService vertexAIService;

    public AgentService(EvidenceCollectorService evidenceCollector, VertexAIService vertexAIService) {
        this.evidenceCollector = evidenceCollector;
        this.vertexAIService = vertexAIService;
    }

    public AgentResult analyze(AgentRequest request) {
        EvidenceBundle evidence = evidenceCollector.collect(request);
        String evidenceSummary = evidenceCollector.summarize(evidence);

        AnalysisResult analysis = new AnalysisResult();
        try {
            VertexAIService.CallResult call = vertexAIService.completeWithMetrics(buildAgentPrompt(request, evidenceSummary));
            analysis.summary = call.text();
            analysis.aiUsage = vertexAIService.toAiUsage(call);
        } catch (Exception e) {
            log.error("Agent Gemini analysis failed: {}", e.getMessage(), e);
            analysis.summary = fallbackSummary(evidence, e);
            analysis.aiUsage = vertexAIService.usageForException(e);
        }

        analysis.evidence = evidence.findings;
        analysis.evidenceSources = evidence.selectedSources;
        analysis.severity = highestSeverity(evidence.findings);
        analysis.confidence = evidence.findings.isEmpty() ? "LOW" : "EVIDENCE_BACKED";

        AgentResult result = new AgentResult();
        result.selectedAnalyzerId = "AUTO";
        result.selectedAnalyzerName = "Vertex AI Agent / Gemini Router";
        result.reason = "Collected evidence from matching observability, security, image, dependency, and platform sources before asking Gemini for RCA/remediation.";
        result.mode = "agent";
        result.payload = buildPayload(request, evidence);
        result.analysis = analysis;
        result.selectedSources = evidence.selectedSources;
        result.evidenceBundle = evidence;
        return result;
    }

    private String buildAgentPrompt(AgentRequest request, String evidenceSummary) {
        return """
                You are the AutoRoot-X AI orchestrator.

                Your job:
                1. Route the problem mentally across GCP evidence sources.
                2. Use only the provided evidence for factual claims.
                3. Clearly label gaps where a source adapter is not connected yet.
                4. Produce a concise root-cause analysis and remediation plan.

                Control plane:
                React + TSX UI -> Spring Boot AI Orchestrator -> Vertex AI/Gemini Router -> Evidence sources.

                Evidence sources represented here:
                Cloud Logging, Cloud Trace, Cloud Monitoring, Security Command Center,
                Artifact Analysis, OSV, deps.dev, GKE Insights, Trivy sidecar.

                Problem:
                %s

                Context:
                %s

                Evidence bundle:
                %s

                Required response format:
                **SEVERITY**: [CRITICAL|HIGH|MEDIUM|LOW]
                **CONFIDENCE**: [HIGH|MEDIUM|LOW]
                **ROOT CAUSE**:
                [Most likely cause, with source names in parentheses]
                **IMPACT**:
                [Affected service/users/security posture]
                **RECOMMENDED FIX**:
                [Prioritized steps, commands/configs where possible]
                **EVIDENCE GAPS**:
                [Sources that should be connected or checked next]
                """.formatted(nvl(request.problem), nvl(request.context), evidenceSummary);
    }

    private Map<String, Object> buildPayload(AgentRequest request, EvidenceBundle evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("problem", request.problem);
        payload.put("context", request.context);
        payload.put("hints", request.hints == null ? Map.of() : request.hints);
        payload.put("selectedSources", evidence.selectedSources);
        payload.put("findingCount", evidence.findings.size());
        return payload;
    }

    private String fallbackSummary(EvidenceBundle evidence, Exception e) {
        return "Gemini analysis unavailable: " + e.getMessage()
                + "\n\nEvidence collected locally:\n"
                + evidenceCollector.summarize(evidence);
    }

    private String highestSeverity(List<EvidenceFinding> findings) {
        if (findings.stream().anyMatch(f -> "CRITICAL".equalsIgnoreCase(f.severity))) return "CRITICAL";
        if (findings.stream().anyMatch(f -> "HIGH".equalsIgnoreCase(f.severity))) return "HIGH";
        if (findings.stream().anyMatch(f -> "MEDIUM".equalsIgnoreCase(f.severity))) return "MEDIUM";
        return findings.isEmpty() ? "UNKNOWN" : "LOW";
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
