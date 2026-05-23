package com.autorootx.orchestrator;

import com.autorootx.exception.ApiException;
import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import com.autorootx.model.Vulnerability;
import com.autorootx.plugin.Analyzer;
import com.autorootx.plugin.AnalyzerMeta;
import com.autorootx.plugin.AnalyzerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);
    private static final String AUTO_ANALYZER = "AUTO";
    private static final List<String> SEVERITY_ORDER = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO", "UNKNOWN", "N/A");

    private final AnalyzerRegistry registry;

    public AnalysisOrchestrator(AnalyzerRegistry registry) {
        this.registry = registry;
    }

    public AnalysisResult analyze(AnalysisRequest req) {
        String analyzerId = normalizeAnalyzerId(req.analyzerId);
        if (AUTO_ANALYZER.equals(analyzerId)) {
            return analyzeAutomatically(req);
        }

        Analyzer analyzer = registry.get(analyzerId);
        try {
            return analyzer.analyze(copyForAnalyzer(req, analyzerId));
        } catch (ApiException apiException) {
            throw apiException;
        } catch (Exception e) {
            return failedResult(analyzerId, e);
        }
    }

    private AnalysisResult analyzeAutomatically(AnalysisRequest req) {
        List<String> analyzerIds = inferAnalyzers(req);
        List<AnalysisResult> results = new ArrayList<>();

        for (String analyzerId : analyzerIds) {
            try {
                Analyzer analyzer = registry.get(analyzerId);
                results.add(labelResult(analyzer, analyzer.analyze(copyForAnalyzer(req, analyzerId))));
            } catch (ApiException apiException) {
                throw apiException;
            } catch (Exception e) {
                results.add(failedResult(analyzerId, e));
            }
        }

        return aggregate(analyzerIds, results);
    }

    private List<String> inferAnalyzers(AnalysisRequest req) {
        Map<String, Object> payload = req.payload == null ? Map.of() : req.payload;
        String text = flatten(payload).toLowerCase(Locale.ROOT);
        Set<String> selected = new LinkedHashSet<>();

        if (hasAnyKey(payload, "logs", "log", "message", "alert", "error", "incident", "description", "problem", "service", "resource")
                || containsAny(text, "exception", "timeout", "latency", "http 5", "503", "crashloop", "pod", "gke", "cloud run", "cloud logging")) {
            selected.add("LOGS");
        }

        if (hasAnyKey(payload, "image", "containerImage", "container_image", "dockerImage", "docker_image", "trivyReport")
                || containsAny(text, "docker", "container image", "artifact registry", "gcr.io", "pkg.dev", ":latest", "trivy")) {
            selected.add("IMAGE");
        }

        if (hasAnyKey(payload, "dependency", "dependencies", "package", "packages", "pom", "packageJson", "package_json", "buildFile", "build_file")
                || containsAny(text, "dependency", "dependencies", "cve-", "log4j", "lodash", "maven", "npm", "pom.xml", "package.json")) {
            selected.add("OSS");
        }

        for (AnalyzerMeta meta : registry.list()) {
            if (selected.contains(meta.id) || List.of("LOGS", "IMAGE", "OSS").contains(meta.id)) {
                continue;
            }
            boolean inputMatched = meta.inputs != null && meta.inputs.stream()
                    .anyMatch(input -> hasAnyKey(payload, input) || text.contains(input.toLowerCase(Locale.ROOT)));
            boolean nameMatched = text.contains(meta.id.toLowerCase(Locale.ROOT))
                    || (meta.name != null && text.contains(meta.name.toLowerCase(Locale.ROOT)));
            if (inputMatched || nameMatched) {
                selected.add(meta.id);
            }
        }

        if (selected.isEmpty()) {
            selected.add("LOGS");
        }

        return selected.stream()
                .sorted(Comparator.comparingInt(this::preferredOrder))
                .toList();
    }

    private AnalysisResult aggregate(List<String> analyzerIds, List<AnalysisResult> results) {
        AnalysisResult aggregate = new AnalysisResult();
        aggregate.severity = highestSeverity(results);
        aggregate.confidence = "AUTO";
        aggregate.summary = buildSummary(analyzerIds, results);
        aggregate.rootCause = combineField(results, "rootCause");
        aggregate.impact = combineField(results, "impact");
        aggregate.fix = combineField(results, "fix");

        List<Vulnerability> vulnerabilities = results.stream()
                .filter(r -> r.vulnerabilities != null)
                .flatMap(r -> r.vulnerabilities.stream())
                .toList();
        aggregate.vulnerabilities = vulnerabilities.isEmpty() ? null : vulnerabilities;

        return aggregate;
    }

    private String buildSummary(List<String> analyzerIds, List<AnalysisResult> results) {
        StringBuilder summary = new StringBuilder();
        summary.append("AUTO analysis routed the problem to: ")
                .append(String.join(", ", analyzerIds))
                .append("\n\n");

        for (int i = 0; i < results.size(); i++) {
            AnalysisResult result = results.get(i);
            summary.append("## ").append(analyzerIds.get(i)).append("\n");
            if (hasText(result.summary)) {
                summary.append(result.summary.trim()).append("\n\n");
            } else {
                summary.append("No summary returned.\n\n");
            }
        }

        return summary.toString().trim();
    }

    private String combineField(List<AnalysisResult> results, String field) {
        List<String> values = new ArrayList<>();
        for (AnalysisResult result : results) {
            String value = switch (field) {
                case "rootCause" -> result.rootCause;
                case "impact" -> result.impact;
                case "fix" -> result.fix;
                default -> null;
            };
            if (hasText(value)) {
                values.add(value.trim());
            }
        }
        return values.isEmpty() ? null : String.join("\n\n", values);
    }

    private String highestSeverity(List<AnalysisResult> results) {
        return results.stream()
                .map(r -> normalizeSeverity(r.severity))
                .min(Comparator.comparingInt(SEVERITY_ORDER::indexOf))
                .orElse("UNKNOWN");
    }

    private AnalysisResult labelResult(Analyzer analyzer, AnalysisResult result) {
        if (result == null) {
            result = new AnalysisResult();
        }
        if (!hasText(result.summary)) {
            result.summary = analyzer.name() + " completed without a summary.";
        }
        return result;
    }

    private AnalysisResult failedResult(String analyzerId, Exception e) {
        log.error("Analyzer {} failed: {}", analyzerId, e.getMessage(), e);
        AnalysisResult err = new AnalysisResult();
        err.summary = "Analysis failed in " + analyzerId + ": " + e.getMessage();
        err.severity = "UNKNOWN";
        err.confidence = "N/A";
        return err;
    }

    private AnalysisRequest copyForAnalyzer(AnalysisRequest req, String analyzerId) {
        AnalysisRequest copy = new AnalysisRequest();
        copy.analyzerId = analyzerId;
        copy.payload = req.payload == null ? Map.of() : req.payload;
        return copy;
    }

    private String normalizeAnalyzerId(String analyzerId) {
        return analyzerId == null ? "" : analyzerId.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSeverity(String severity) {
        String normalized = severity == null ? "UNKNOWN" : severity.trim().toUpperCase(Locale.ROOT);
        return SEVERITY_ORDER.contains(normalized) ? normalized : "UNKNOWN";
    }

    private int preferredOrder(String analyzerId) {
        return switch (analyzerId) {
            case "LOGS" -> 0;
            case "IMAGE" -> 1;
            case "OSS" -> 2;
            default -> 3;
        };
    }

    private boolean hasAnyKey(Map<String, Object> payload, String... keys) {
        Set<String> payloadKeys = payload.keySet().stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        for (String key : keys) {
            if (payloadKeys.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String flatten(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + flatten(entry.getValue()))
                    .reduce("", (left, right) -> left + " " + right);
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder sb = new StringBuilder();
            for (Object item : iterable) {
                sb.append(' ').append(flatten(item));
            }
            return sb.toString();
        }
        return String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
