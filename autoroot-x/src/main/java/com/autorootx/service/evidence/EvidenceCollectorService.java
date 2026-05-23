package com.autorootx.service.evidence;

import com.autorootx.model.AgentRequest;
import com.autorootx.model.EvidenceBundle;
import com.autorootx.model.EvidenceFinding;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class EvidenceCollectorService {

    private final List<EvidenceSource> sources;

    public EvidenceCollectorService(List<EvidenceSource> sources) {
        this.sources = sources.stream()
                .sorted(Comparator.comparing(EvidenceSource::id))
                .toList();
    }

    public EvidenceBundle collect(AgentRequest request) {
        EvidenceContext context = new EvidenceContext(request);
        EvidenceBundle bundle = new EvidenceBundle();

        for (EvidenceSource source : sources) {
            boolean selected = context.requestedSource(source.id()) || source.supports(context);
            if (!selected) {
                continue;
            }

            bundle.selectedSources.add(source.id());
            try {
                List<EvidenceFinding> findings = source.collect(context);
                if (findings != null) {
                    bundle.findings.addAll(findings);
                }
            } catch (Exception e) {
                bundle.warnings.add(source.id() + ": " + e.getMessage());
            }
        }

        if (bundle.selectedSources.isEmpty()) {
            bundle.warnings.add("No evidence source matched the problem. Gemini used only the submitted problem and context.");
        }

        return bundle;
    }

    public String summarize(EvidenceBundle bundle) {
        StringBuilder sb = new StringBuilder();
        sb.append("Selected sources: ").append(bundle.selectedSources).append("\n\n");

        for (EvidenceFinding finding : bundle.findings) {
            sb.append("Source: ").append(finding.source).append("\n")
                    .append("Category: ").append(finding.category).append("\n")
                    .append("Severity: ").append(finding.severity).append("\n")
                    .append("Title: ").append(finding.title).append("\n");
            if (finding.resource != null && !finding.resource.isBlank()) {
                sb.append("Resource: ").append(finding.resource).append("\n");
            }
            if (finding.summary != null && !finding.summary.isBlank()) {
                sb.append("Summary: ").append(finding.summary).append("\n");
            }
            if (finding.raw != null && !finding.raw.isBlank()) {
                sb.append("Raw: ").append(finding.raw).append("\n");
            }
            sb.append("\n");
        }

        if (!bundle.warnings.isEmpty()) {
            sb.append("Warnings:\n");
            for (String warning : bundle.warnings) {
                sb.append("- ").append(warning).append("\n");
            }
        }

        return sb.toString().trim();
    }
}
