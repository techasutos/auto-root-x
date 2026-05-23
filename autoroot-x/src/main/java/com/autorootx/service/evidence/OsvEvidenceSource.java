package com.autorootx.service.evidence;

import com.autorootx.model.EvidenceFinding;
import com.autorootx.service.OssService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OsvEvidenceSource implements EvidenceSource {

    private final OssService oss;

    public OsvEvidenceSource(OssService oss) {
        this.oss = oss;
    }

    @Override public String id() { return "osv"; }
    @Override public String label() { return "OSV"; }
    @Override public String category() { return "SECURITY"; }

    @Override
    public boolean supports(EvidenceContext context) {
        return context.hint("dependency").isPresent()
                || context.hint("dependencies").isPresent()
                || context.containsAny("dependency", "dependencies", "log4j", "lodash", "maven", "npm", "cve-");
    }

    @Override
    public List<EvidenceFinding> collect(EvidenceContext context) {
        List<String> dependencies = new ArrayList<>();
        context.hint("dependency").ifPresent(value -> dependencies.addAll(EvidenceSupport.toStrings(value)));
        context.hint("dependencies").ifPresent(value -> dependencies.addAll(EvidenceSupport.toStrings(value)));

        if (dependencies.isEmpty()) {
            dependencies.addAll(extractKnownPackages(context.searchableText()));
        }

        List<EvidenceFinding> findings = new ArrayList<>();
        for (String dependency : dependencies.stream().filter(s -> !s.isBlank()).distinct().limit(5).toList()) {
            try {
                String result = oss.scan(dependency);
                findings.add(EvidenceSupport.finding(
                        id(),
                        category(),
                        "OSV dependency scan: " + dependency,
                        result.contains("\"vulns\"") ? "HIGH" : "LOW",
                        dependency,
                        "OSV returned vulnerability metadata for dependency.",
                        result
                ));
            } catch (Exception e) {
                findings.add(EvidenceSupport.finding(
                        id(),
                        category(),
                        "OSV scan unavailable: " + dependency,
                        "LOW",
                        dependency,
                        "OSV lookup failed; Gemini should treat this as missing evidence.",
                        e.getMessage()
                ));
            }
        }
        return findings;
    }

    private List<String> extractKnownPackages(String text) {
        List<String> packages = new ArrayList<>();
        if (text.contains("log4j")) packages.add("log4j-core");
        if (text.contains("lodash")) packages.add("lodash");
        if (text.contains("spring")) packages.add("spring-core");
        return packages;
    }
}
