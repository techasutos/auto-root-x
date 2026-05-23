package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DepsDevEvidenceSource implements EvidenceSource {
    @Override public String id() { return "deps-dev"; }
    @Override public String label() { return "deps.dev"; }
    @Override public String category() { return "SECURITY"; }

    @Override
    public boolean supports(EvidenceContext context) {
        return context.hint("dependency").isPresent()
                || context.hint("dependencies").isPresent()
                || context.containsAny("dependency", "dependencies", "deps.dev", "maven", "npm");
    }

    @Override
    public List<EvidenceFinding> collect(EvidenceContext context) {
        List<String> dependencies = new ArrayList<>();
        context.hint("dependency").ifPresent(value -> dependencies.addAll(EvidenceSupport.toStrings(value)));
        context.hint("dependencies").ifPresent(value -> dependencies.addAll(EvidenceSupport.toStrings(value)));

        if (dependencies.isEmpty()) {
            return List.of(EvidenceSupport.finding(
                    id(),
                    category(),
                    "Dependency graph context",
                    "LOW",
                    "",
                    "Dependency issue detected. Connect deps.dev API for package graph, version, and dependent metadata.",
                    ""
            ));
        }

        return dependencies.stream()
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(5)
                .map(dep -> EvidenceSupport.finding(
                        id(),
                        category(),
                        "deps.dev package context: " + dep,
                        "LOW",
                        dep,
                        "Use deps.dev for dependency graph and version context.",
                        "https://deps.dev/search?q=" + dep.replace(" ", "%20")
                ))
                .toList();
    }
}
