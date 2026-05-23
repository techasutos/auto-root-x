package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArtifactAnalysisEvidenceSource implements EvidenceSource {
    @Override public String id() { return "artifact-analysis"; }
    @Override public String label() { return "Artifact Analysis"; }
    @Override public String category() { return "SECURITY"; }

    @Override
    public boolean supports(EvidenceContext context) {
        return context.hint("artifactAnalysis").isPresent()
                || context.hint("artifactFindings").isPresent()
                || context.hint("image").isPresent()
                || context.containsAny("artifact registry", "artifact analysis", "container image", "gcr.io", "pkg.dev");
    }

    @Override
    public List<EvidenceFinding> collect(EvidenceContext context) {
        String raw = EvidenceSupport.firstNonBlank(
                context.hint("artifactAnalysis").map(String::valueOf).orElse(""),
                context.hint("artifactFindings").map(String::valueOf).orElse("")
        );
        String image = context.hintString("image").orElse("");

        return List.of(EvidenceSupport.finding(
                id(),
                category(),
                "Artifact vulnerability context",
                severityFromText(raw),
                image,
                raw.isBlank()
                        ? "Image context detected. Connect Artifact Analysis API for live container vulnerability metadata."
                        : "Artifact Analysis finding supplied in request hints.",
                raw
        ));
    }

    private String severityFromText(String raw) {
        String text = raw == null ? "" : raw.toUpperCase();
        if (text.contains("CRITICAL")) return "CRITICAL";
        if (text.contains("HIGH")) return "HIGH";
        if (text.contains("MEDIUM")) return "MEDIUM";
        return "LOW";
    }
}
