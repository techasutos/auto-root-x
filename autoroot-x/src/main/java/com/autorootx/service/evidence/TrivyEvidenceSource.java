package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;
import com.autorootx.model.Vulnerability;
import com.autorootx.service.TrivyService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrivyEvidenceSource implements EvidenceSource {

    private final TrivyService trivy;

    public TrivyEvidenceSource(TrivyService trivy) {
        this.trivy = trivy;
    }

    @Override public String id() { return "trivy"; }
    @Override public String label() { return "Trivy Sidecar"; }
    @Override public String category() { return "SECURITY"; }

    @Override
    public boolean supports(EvidenceContext context) {
        return context.hint("image").isPresent()
                || context.containsAny("container image", "docker", "trivy", "gcr.io", "pkg.dev", ":latest");
    }

    @Override
    public List<EvidenceFinding> collect(EvidenceContext context) {
        String image = context.hintString("image").orElse("");
        if (image.isBlank()) {
            image = extractImage(context.searchableText());
        }
        if (image.isBlank()) {
            return List.of();
        }

        String imageRef = image;
        TrivyService.TrivyScanResult scan = trivy.scanImage(imageRef);
        if (!scan.available()) {
            return List.of(EvidenceSupport.finding(
                    id(),
                    category(),
                    "Trivy unavailable",
                    "LOW",
                    imageRef,
                    "Trivy sidecar did not return a live report: " + scan.message(),
                    scan.rawResponse()
            ));
        }

        return scan.vulnerabilities().stream()
                .limit(20)
                .map(v -> fromVulnerability(imageRef, v))
                .toList();
    }

    private EvidenceFinding fromVulnerability(String image, Vulnerability v) {
        return EvidenceSupport.finding(
                id(),
                category(),
                v.id + " " + v.title,
                v.severity,
                image,
                v.affectedPackage + " " + v.currentVersion + " fixed in " + v.fixedVersion,
                v.description
        );
    }

    private String extractImage(String text) {
        String[] parts = text.split("\\s+");
        for (String part : parts) {
            String cleaned = part.replaceAll("[,;()\"']", "");
            if ((cleaned.contains("gcr.io") || cleaned.contains("pkg.dev") || cleaned.contains("/")) && cleaned.contains(":")) {
                return cleaned;
            }
        }
        return "";
    }
}
