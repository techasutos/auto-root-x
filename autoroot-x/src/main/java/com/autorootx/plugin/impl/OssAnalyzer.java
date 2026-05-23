package com.autorootx.plugin.impl;

import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import com.autorootx.plugin.Analyzer;
import com.autorootx.service.OssService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OssAnalyzer implements Analyzer {

    private final OssService osv;

    public OssAnalyzer(OssService osv) {
        this.osv = osv;
    }

    public String id() { return "OSS"; }
    public String name() { return "Dependency Scanner"; }
    public String category() { return "SECURITY"; }
    public List<String> inputs() { return List.of("dependency", "dependencies", "package", "packages"); }

    public AnalysisResult analyze(AnalysisRequest req) {
        try {
            List<String> dependencies = extractDependencies(req.payload);
            if (dependencies.isEmpty()) {
                throw new IllegalArgumentException("Provide dependency or dependencies in the payload");
            }

            AnalysisResult r = new AnalysisResult();
            r.summary = scanAll(dependencies);
            r.severity = r.summary.contains("\"vulns\"") ? "HIGH" : "LOW";
            r.confidence = "OSV";
            return r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String scanAll(List<String> dependencies) throws Exception {
        StringBuilder result = new StringBuilder();
        for (String dependency : dependencies) {
            result.append("## ").append(dependency).append("\n")
                    .append(osv.scan(dependency)).append("\n\n");
        }
        return result.toString().trim();
    }

    private List<String> extractDependencies(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }

        List<String> dependencies = new ArrayList<>();
        for (String key : List.of("dependency", "dependencies", "package", "packages")) {
            Object value = payload.get(key);
            if (value == null) {
                continue;
            }
            addDependency(dependencies, value);
        }
        return dependencies.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private void addDependency(List<String> dependencies, Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addDependency(dependencies, item);
            }
            return;
        }

        if (value instanceof Map<?, ?> map) {
            Object name = map.get("name");
            if (name != null) {
                dependencies.add(String.valueOf(name));
            }
            return;
        }

        String text = String.valueOf(value);
        if (text.contains(",")) {
            for (String part : text.split(",")) {
                dependencies.add(part);
            }
            return;
        }
        dependencies.add(text);
    }
}
