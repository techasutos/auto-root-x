package com.autorootx.plugin.impl;

import com.autorootx.plugin.Analyzer;
import com.autorootx.model.*;
import com.autorootx.service.GcpLogService;
import com.autorootx.service.VertexAIService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LogsAnalyzer implements Analyzer {

    private final GcpLogService logs;
    private final VertexAIService ai;

    public LogsAnalyzer(GcpLogService l, VertexAIService ai) {
        this.logs = l;
        this.ai = ai;
    }

    public String id() { return "LOGS"; }
    public String name() { return "Logs Analyzer"; }
    public String category() { return "OBSERVABILITY"; }
    public List<String> inputs() { return List.of("logs", "log", "message", "alert", "problem"); }

    public AnalysisResult analyze(AnalysisRequest req) {
        List<String> logData = extractLogs(req.payload);
        if (logData.isEmpty()) {
            logData = logs.fetchErrors();
        }

        String aiResult;
        AnalysisResult r = new AnalysisResult();
        try {
            VertexAIService.CallResult call = ai.analyzeWithMetrics("LOGS", String.join("\n", logData));
            aiResult = call.text();
            r.aiUsage = ai.toAiUsage(call);
        } catch (Exception e) {
            aiResult = "AI analysis unavailable: " + e.getMessage()
                    + "\n\nRaw log entries fetched:\n" + String.join("\n", logData);
            r.aiUsage = ai.usageForException(e);
        }

        r.summary = aiResult;
        r.severity = "UNKNOWN";
        r.confidence = "N/A";
        return r;
    }

    private List<String> extractLogs(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }

        List<String> entries = new ArrayList<>();
        for (String key : List.of("logs", "log", "message", "alert", "problem", "description", "error")) {
            Object value = payload.get(key);
            if (value == null) {
                continue;
            }
            addValue(entries, value);
        }
        return entries;
    }

    private void addValue(List<String> entries, Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addValue(entries, item);
            }
            return;
        }

        if (value instanceof Map<?, ?> map) {
            entries.add(map.toString());
            return;
        }

        String text = String.valueOf(value);
        if (!text.isBlank()) {
            entries.add(text);
        }
    }
}
