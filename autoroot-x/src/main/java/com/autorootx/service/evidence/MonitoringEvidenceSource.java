package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MonitoringEvidenceSource implements EvidenceSource {
    @Override public String id() { return "monitoring"; }
    @Override public String label() { return "Cloud Monitoring"; }
    @Override public String category() { return "OBSERVABILITY"; }

    @Override
    public boolean supports(EvidenceContext context) {
        return context.hint("monitoring").isPresent()
                || context.hint("metrics").isPresent()
                || context.hint("alert").isPresent()
                || context.containsAny("alert", "slo", "latency", "cpu", "memory", "metric", "monitoring");
    }

    @Override
    public List<EvidenceFinding> collect(EvidenceContext context) {
        String raw = EvidenceSupport.firstNonBlank(
                context.hint("monitoring").map(String::valueOf).orElse(""),
                context.hint("metrics").map(String::valueOf).orElse(""),
                context.hint("alert").map(String::valueOf).orElse("")
        );

        return List.of(EvidenceSupport.finding(
                id(),
                category(),
                "Monitoring signal",
                severityFromText(raw),
                context.hintString("resource").orElse(""),
                raw.isBlank()
                        ? "Problem text indicates an alert or metric issue. Connect Cloud Monitoring API for live time-series evidence."
                        : "Cloud Monitoring alert/metric evidence supplied in request hints.",
                raw
        ));
    }

    private String severityFromText(String raw) {
        String text = raw == null ? "" : raw.toUpperCase();
        if (text.contains("CRITICAL") || text.contains("P0")) return "CRITICAL";
        if (text.contains("HIGH") || text.contains("P1")) return "HIGH";
        if (text.contains("WARNING") || text.contains("P2")) return "MEDIUM";
        return "LOW";
    }
}
