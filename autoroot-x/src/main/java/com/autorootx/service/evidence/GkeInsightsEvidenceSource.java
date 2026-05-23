package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GkeInsightsEvidenceSource implements EvidenceSource {
    @Override public String id() { return "gke-insights"; }
    @Override public String label() { return "GKE Insights"; }
    @Override public String category() { return "PLATFORM"; }

    @Override
    public boolean supports(EvidenceContext context) {
        return context.hint("gkeInsights").isPresent()
                || context.hint("pod").isPresent()
                || context.hint("namespace").isPresent()
                || context.containsAny("gke", "kubernetes", "pod", "deployment", "namespace", "crashloop", "node");
    }

    @Override
    public List<EvidenceFinding> collect(EvidenceContext context) {
        String raw = context.hint("gkeInsights").map(String::valueOf).orElse("");
        String resource = EvidenceSupport.firstNonBlank(
                context.hintString("pod").orElse(""),
                context.hintString("deployment").orElse(""),
                context.hintString("namespace").orElse(""),
                context.hintString("cluster").orElse("")
        );

        return List.of(EvidenceSupport.finding(
                id(),
                category(),
                "GKE workload context",
                context.containsAny("crashloop", "failed", "unhealthy") ? "HIGH" : "LOW",
                resource,
                raw.isBlank()
                        ? "Problem indicates GKE workload involvement. Connect GKE insights or Kubernetes API for live workload state."
                        : "GKE insight evidence supplied in request hints.",
                raw
        ));
    }
}
