package com.autorootx.controller;

import com.autorootx.model.*;
import com.autorootx.plugin.*;
import com.autorootx.orchestrator.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class AnalysisController {

    private final AnalyzerRegistry registry;
    private final AnalysisOrchestrator orchestrator;

    public AnalysisController(AnalyzerRegistry r, AnalysisOrchestrator o) {
        this.registry = r;
        this.orchestrator = o;
    }

    @GetMapping("/plugins")
    public List<AnalyzerMeta> plugins() {
        return registry.list();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/analyze")
    public AnalysisResult analyze(@Valid @RequestBody AnalysisRequest req) {
        return orchestrator.analyze(req);
    }
}