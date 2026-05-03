package com.autorootx.plugin;

import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;

import java.util.List;

public interface Analyzer {

    String id();
    String name();
    String category(); // OBSERVABILITY / SECURITY
    List<String> inputs();

    AnalysisResult analyze(AnalysisRequest request);
}