package com.autorootx.orchestrator;

import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import com.autorootx.plugin.Analyzer;
import com.autorootx.plugin.AnalyzerRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisOrchestratorTests {

    @Test
    void autoRoutesImageAndDependencyPayloadToSecurityAnalyzers() {
        RecordingAnalyzer logs = analyzer("LOGS", "LOW", "logs summary", List.of("logs"));
        RecordingAnalyzer image = analyzer("IMAGE", "CRITICAL", "image summary", List.of("image"));
        RecordingAnalyzer oss = analyzer("OSS", "HIGH", "oss summary", List.of("dependencies"));
        AnalysisOrchestrator orchestrator = orchestrator(logs, image, oss);

        AnalysisRequest request = request("AUTO", Map.of(
                "image", "us-central1-docker.pkg.dev/demo/apps/api:latest",
                "dependencies", List.of(Map.of("name", "log4j-core", "version", "2.14.1"))
        ));

        AnalysisResult result = orchestrator.analyze(request);

        assertThat(image.calls).containsExactly("IMAGE");
        assertThat(oss.calls).containsExactly("OSS");
        assertThat(logs.calls).isEmpty();
        assertThat(result.severity).isEqualTo("CRITICAL");
        assertThat(result.summary).contains("AUTO analysis routed the problem to: IMAGE, OSS");
        assertThat(result.summary).contains("image summary", "oss summary");
    }

    @Test
    void autoRoutesIncidentTextToLogsAnalyzer() {
        RecordingAnalyzer logs = analyzer("LOGS", "HIGH", "logs summary", List.of("problem"));
        RecordingAnalyzer image = analyzer("IMAGE", "LOW", "image summary", List.of("image"));
        RecordingAnalyzer oss = analyzer("OSS", "LOW", "oss summary", List.of("dependencies"));
        AnalysisOrchestrator orchestrator = orchestrator(logs, image, oss);

        AnalysisRequest request = request("AUTO", Map.of(
                "problem", "GKE payment pod has HTTP 503 spike and SQL timeout exceptions"
        ));

        AnalysisResult result = orchestrator.analyze(request);

        assertThat(logs.calls).containsExactly("LOGS");
        assertThat(image.calls).isEmpty();
        assertThat(oss.calls).isEmpty();
        assertThat(result.severity).isEqualTo("HIGH");
        assertThat(result.summary).contains("AUTO analysis routed the problem to: LOGS");
    }

    @Test
    void directAnalyzerIdsAreNormalized() {
        RecordingAnalyzer logs = analyzer("LOGS", "LOW", "logs summary", List.of("logs"));
        AnalysisOrchestrator orchestrator = orchestrator(logs);

        AnalysisResult result = orchestrator.analyze(request("logs", Map.of("logs", "one error")));

        assertThat(logs.calls).containsExactly("LOGS");
        assertThat(result.summary).isEqualTo("logs summary");
    }

    private AnalysisOrchestrator orchestrator(Analyzer... analyzers) {
        return new AnalysisOrchestrator(new AnalyzerRegistry(List.of(analyzers)));
    }

    private AnalysisRequest request(String analyzerId, Map<String, Object> payload) {
        AnalysisRequest request = new AnalysisRequest();
        request.analyzerId = analyzerId;
        request.payload = payload;
        return request;
    }

    private RecordingAnalyzer analyzer(String id, String severity, String summary, List<String> inputs) {
        return new RecordingAnalyzer(id, severity, summary, inputs);
    }

    private static class RecordingAnalyzer implements Analyzer {
        private final String id;
        private final String severity;
        private final String summary;
        private final List<String> inputs;
        private final List<String> calls = new ArrayList<>();

        private RecordingAnalyzer(String id, String severity, String summary, List<String> inputs) {
            this.id = id;
            this.severity = severity;
            this.summary = summary;
            this.inputs = inputs;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return id + " Analyzer";
        }

        @Override
        public String category() {
            return "TEST";
        }

        @Override
        public List<String> inputs() {
            return inputs;
        }

        @Override
        public AnalysisResult analyze(AnalysisRequest request) {
            calls.add(request.analyzerId);
            AnalysisResult result = new AnalysisResult();
            result.summary = summary;
            result.severity = severity;
            result.confidence = "100%";
            result.rootCause = id + " root cause";
            result.impact = id + " impact";
            result.fix = id + " fix";
            return result;
        }
    }
}
