package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;
import com.autorootx.service.GcpLogService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CloudLoggingEvidenceSource implements EvidenceSource {

    private final GcpLogService logs;

    public CloudLoggingEvidenceSource(GcpLogService logs) {
        this.logs = logs;
    }

    @Override public String id() { return "cloud-logging"; }
    @Override public String label() { return "Cloud Logging"; }
    @Override public String category() { return "OBSERVABILITY"; }

    @Override
    public boolean supports(EvidenceContext context) {
        return context.hint("logs").isPresent()
                || context.containsAny("log", "error", "exception", "timeout", "503", "crashloop", "failed", "gke", "cloud run");
    }

    @Override
    public List<EvidenceFinding> collect(EvidenceContext context) {
        List<String> entries = context.hint("logs")
                .map(EvidenceSupport::toStrings)
                .filter(list -> !list.isEmpty())
                .orElseGet(logs::fetchErrors);

        List<EvidenceFinding> findings = new ArrayList<>();
        for (String entry : entries.stream().limit(8).toList()) {
            findings.add(EvidenceSupport.finding(
                    id(),
                    category(),
                    "Relevant log entry",
                    severityFromText(entry),
                    "",
                    "Cloud Logging evidence for the reported problem",
                    entry
            ));
        }
        return findings;
    }

    private String severityFromText(String entry) {
        String text = entry == null ? "" : entry.toUpperCase();
        if (text.contains("CRITICAL") || text.contains("FATAL")) return "CRITICAL";
        if (text.contains("ERROR") || text.contains("EXCEPTION") || text.contains("503")) return "HIGH";
        if (text.contains("WARN") || text.contains("TIMEOUT")) return "MEDIUM";
        return "LOW";
    }
}
