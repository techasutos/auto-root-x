package com.autorootx.plugin;

import com.autorootx.exception.ApiException;
import com.autorootx.model.AdminPluginCreateRequest;
import com.autorootx.model.AdminPluginStatus;
import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AnalyzerRegistry {

    private final Map<String, Analyzer> analyzers;
    private final Set<String> enabled;
    private final Set<String> dynamicAnalyzers;

    public AnalyzerRegistry(List<Analyzer> list) {
        this.analyzers = new ConcurrentHashMap<>(list.stream()
                .collect(Collectors.toMap(Analyzer::id, a -> a)));
        this.enabled = Collections.synchronizedSet(new HashSet<>(analyzers.keySet()));
        this.dynamicAnalyzers = Collections.synchronizedSet(new HashSet<>());
    }

    public List<AnalyzerMeta> list() {
        synchronized (enabled) {
            return analyzers.values().stream()
                    .filter(a -> enabled.contains(a.id()))
                    .map(this::meta)
                    .sorted((a, b) -> a.id.compareToIgnoreCase(b.id))
                    .toList();
        }
    }

    public List<AdminPluginStatus> listAllWithStatus() {
        synchronized (enabled) {
            synchronized (dynamicAnalyzers) {
                return analyzers.values().stream()
                        .map(this::status)
                        .sorted((a, b) -> a.id.compareToIgnoreCase(b.id))
                        .toList();
            }
        }
    }

    public void enable(String id) {
        Analyzer analyzer = analyzers.get(id);
        if (analyzer == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Plugin not found: " + id);
        }
        enabled.add(id);
    }

    public void disable(String id) {
        Analyzer analyzer = analyzers.get(id);
        if (analyzer == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Plugin not found: " + id);
        }
        enabled.remove(id);
    }

    public void addDynamic(AdminPluginCreateRequest req) {
        String id = req.id.trim().toUpperCase();
        if (analyzers.containsKey(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Plugin already exists: " + id);
        }

        List<String> normalizedInputs = req.inputs.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (normalizedInputs.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "inputs must have at least one value");
        }

        String template = req.summaryTemplate == null || req.summaryTemplate.isBlank()
                ? "Dynamic plugin result"
                : req.summaryTemplate;

        Analyzer dynamic = new DynamicAnalyzer(
                id,
                req.name.trim(),
                req.category.trim().toUpperCase(),
                normalizedInputs,
                template
        );

        analyzers.put(id, dynamic);
        enabled.add(id);
        dynamicAnalyzers.add(id);
    }

    public Analyzer get(String id) {
        Analyzer analyzer = analyzers.get(id);
        if (analyzer == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Analyzer not found: " + id);
        }

        if (!enabled.contains(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Plugin disabled: " + id);
        }
        return analyzer;
    }

    private AdminPluginStatus status(Analyzer a) {
        AdminPluginStatus s = new AdminPluginStatus();
        s.id = a.id();
        s.name = a.name();
        s.category = a.category();
        s.inputs = a.inputs();
        s.enabled = enabled.contains(a.id());
        s.dynamic = dynamicAnalyzers.contains(a.id());
        return s;
    }

    private AnalyzerMeta meta(Analyzer a) {
        AnalyzerMeta m = new AnalyzerMeta();
        m.id = a.id();
        m.name = a.name();
        m.category = a.category();
        m.inputs = a.inputs();
        return m;
    }

    private static class DynamicAnalyzer implements Analyzer {

        private final String id;
        private final String name;
        private final String category;
        private final List<String> inputs;
        private final String summaryTemplate;

        private DynamicAnalyzer(String id, String name, String category, List<String> inputs, String summaryTemplate) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.inputs = new ArrayList<>(inputs);
            this.summaryTemplate = summaryTemplate;
        }

        @Override
        public String id() { return id; }

        @Override
        public String name() { return name; }

        @Override
        public String category() { return category; }

        @Override
        public List<String> inputs() { return inputs; }

        @Override
        public AnalysisResult analyze(AnalysisRequest request) {
            AnalysisResult result = new AnalysisResult();
            result.summary = summaryTemplate + " for " + id + " with payload keys " + request.payload.keySet();
            result.rootCause = "Dynamic plugin " + id + " executed";
            result.impact = "Depends on custom plugin behavior";
            result.fix = "Replace dynamic plugin with a typed analyzer when ready";
            result.severity = "INFO";
            result.confidence = "80%";
            return result;
        }
    }
}