package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SecurityCommandCenterEvidenceSource implements EvidenceSource {
    @Override public String id() { return "scc"; }
    @Override public String label() { return "Security Command Center"; }
    @Override public String category() { return "SECURITY"; }

    @Override
    public boolean supports(EvidenceContext context) {
        return context.hint("scc").isPresent()
                || context.hint("securityFindings").isPresent()
                || context.containsAny("security command center", "scc", "finding", "misconfiguration", "attack path", "exposure");
    }

    @Override
    public List<EvidenceFinding> collect(EvidenceContext context) {
        Object evidence = context.hint("scc")
                .or(() -> context.hint("securityFindings"))
                .orElse("");

        return EvidenceSupport.toStrings(evidence).stream()
                .map(raw -> EvidenceSupport.finding(
                        id(),
                        category(),
                        "SCC finding",
                        severityFromText(raw),
                        "",
                        "Security Command Center finding supplied as evidence.",
                        raw
                ))
                .toList();
    }

    private String severityFromText(String raw) {
        String text = raw == null ? "" : raw.toUpperCase();
        if (text.contains("CRITICAL")) return "CRITICAL";
        if (text.contains("HIGH")) return "HIGH";
        if (text.contains("MEDIUM")) return "MEDIUM";
        return "LOW";
    }
}
