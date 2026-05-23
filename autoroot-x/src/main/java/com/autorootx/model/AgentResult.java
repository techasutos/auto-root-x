package com.autorootx.model;

import java.util.List;
import java.util.Map;

public class AgentResult {
    public String selectedAnalyzerId;
    public String selectedAnalyzerName;
    public String reason;
    public String mode;
    public Map<String, Object> payload;
    public AnalysisResult analysis;
    public List<String> selectedSources;
    public EvidenceBundle evidenceBundle;
}
