package com.autorootx.model;

import java.util.List;

public class AnalysisResult {
    public String summary;
    public String rootCause;
    public String impact;
    public String fix;

    public String severity;
    public String confidence;
    public AiUsage aiUsage;

    /** Populated by ImageAnalyzer with structured vulnerability entries. */
    public List<Vulnerability> vulnerabilities;
}