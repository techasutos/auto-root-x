package com.autorootx.orchestrator;

import com.autorootx.exception.ApiException;
import com.autorootx.plugin.*;
import com.autorootx.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);

    private final AnalyzerRegistry registry;

    public AnalysisOrchestrator(AnalyzerRegistry registry) {
        this.registry = registry;
    }

    public AnalysisResult analyze(AnalysisRequest req) {
        Analyzer analyzer = registry.get(req.analyzerId);
        try {
            return analyzer.analyze(req);
        } catch (ApiException apiException) {
            throw apiException;
        } catch (Exception e) {
            log.error("Analyzer {} failed: {}", req.analyzerId, e.getMessage(), e);
            AnalysisResult err = new AnalysisResult();
            err.summary = "Analysis failed: " + e.getMessage();
            err.severity = "UNKNOWN";
            err.confidence = "N/A";
            return err;
        }
    }
}