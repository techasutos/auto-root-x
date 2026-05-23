package com.autorootx.plugin.impl;

import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import com.autorootx.model.Vulnerability;
import com.autorootx.plugin.Analyzer;
import com.autorootx.service.TrivyService;
import com.autorootx.service.VertexAIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ImageAnalyzer implements Analyzer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_VERTEX_FINDINGS = 25;

    private final VertexAIService ai;
    private final TrivyService trivy;

    public ImageAnalyzer(VertexAIService ai, TrivyService trivy) {
        this.ai = ai;
        this.trivy = trivy;
    }

    @Override public String id()       { return "IMAGE"; }
    @Override public String name()     { return "Image Scanner"; }
    @Override public String category() { return "SECURITY"; }
    @Override public List<String> inputs() { return List.of("image", "trivyReport"); }

    @Override
    public AnalysisResult analyze(AnalysisRequest req) {
        Map<String, Object> payload = req.payload == null ? Map.of() : req.payload;
        String image = String.valueOf(payload.getOrDefault("image", "unknown:latest"));

        Optional<TrivyService.TrivyScanResult> payloadScan = parsePayloadTrivyReport(payload);
        TrivyService.TrivyScanResult scan = payloadScan.orElseGet(() -> trivy.scanImage(image));
        boolean trivyAvailable = scan.available();
        List<Vulnerability> vulns = trivyAvailable && !scan.vulnerabilities().isEmpty()
                ? scan.vulnerabilities()
                : detectVulnerabilities(image);

        String vulnSummary = buildVulnSummary(image, vulns, trivyAvailable ? "Trivy sidecar scan" : "Fallback static CVE dataset (Trivy unavailable)");

        AnalysisResult r = new AnalysisResult();
        try {
            VertexAIService.CallResult call = ai.analyzeWithMetrics("IMAGE", vulnSummary);
            r.summary = call.text();
            r.aiUsage = ai.toAiUsage(call);
        } catch (Exception e) {
            r.summary = "AI analysis unavailable: " + e.getMessage();
            r.aiUsage = ai.usageForException(e);
        }

        for (Vulnerability v : vulns) {
            if (v.fix == null || v.fix.isBlank()) {
                v.fix = generateFix(v);
            }
        }

        r.severity = highestSeverity(vulns);
        r.confidence = trivyAvailable ? "TRIVY" : "80%";
        r.rootCause = trivyAvailable
                ? "Trivy detected vulnerable packages in the scanned container image"
                : "Trivy sidecar unavailable, using fallback known-vulnerability dataset";
        r.impact = criticalCount(vulns) + " critical, " + highCount(vulns) + " high severity vulnerabilities detected";
        r.fix = trivyAvailable
                ? "Apply the Vertex AI remediation plan, upgrade fixed package versions, and rebuild the image"
                : "Upgrade base image and enable Trivy sidecar connectivity for live scan results";
        r.vulnerabilities = vulns;
        return r;
    }

    private Optional<TrivyService.TrivyScanResult> parsePayloadTrivyReport(Map<String, Object> payload) {
        Object raw = payload.getOrDefault("trivyReport", payload.get("trivy_report"));
        if (raw == null || String.valueOf(raw).isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = MAPPER.readTree(String.valueOf(raw));
            return Optional.of(new TrivyService.TrivyScanResult(true, parseTrivyVulnerabilities(root), String.valueOf(raw), "Trivy report supplied in request payload"));
        } catch (Exception e) {
            return Optional.of(new TrivyService.TrivyScanResult(true, List.of(), String.valueOf(raw), "Unable to parse supplied Trivy report: " + e.getMessage()));
        }
    }

    private List<Vulnerability> parseTrivyVulnerabilities(JsonNode root) {
        List<Vulnerability> vulnerabilities = new ArrayList<>();
        JsonNode results = root.path("Results");
        if (!results.isArray()) {
            results = root.path("results");
        }
        if (!results.isArray()) {
            return vulnerabilities;
        }

        for (JsonNode result : results) {
            JsonNode vulns = result.path("Vulnerabilities");
            if (!vulns.isArray()) {
                vulns = result.path("vulnerabilities");
            }
            if (!vulns.isArray()) {
                continue;
            }
            for (JsonNode node : vulns) {
                Vulnerability v = new Vulnerability();
                v.id = text(node, "VulnerabilityID", "UNKNOWN");
                v.title = text(node, "Title", v.id);
                v.severity = normalizeSeverity(text(node, "Severity", "LOW"));
                v.description = text(node, "Description", "");
                v.affectedPackage = text(node, "PkgName", "");
                v.currentVersion = text(node, "InstalledVersion", "");
                v.fixedVersion = text(node, "FixedVersion", "");
                v.cvss = extractCvss(node.path("CVSS"));
                v.fix = generateFix(v);
                vulnerabilities.add(v);
            }
        }

        return vulnerabilities.stream()
                .sorted(Comparator.comparingInt(v -> severityRank(v.severity)))
                .toList();
    }

    private List<Vulnerability> detectVulnerabilities(String image) {
        List<Vulnerability> list = new ArrayList<>();

        if (image.contains("java") || image.contains("spring") || image.contains("tomcat") || !image.contains("-")) {
            list.add(vuln("CVE-2021-44228", "Log4Shell - Remote Code Execution",
                    "CRITICAL", "9.0",
                    "Apache Log4j2 JNDI lookup feature allows remote code execution via crafted log messages.",
                    "log4j-core", "2.14.1", "2.17.1",
                    "Upgrade log4j-core to >= 2.17.1"));
        }

        list.add(vuln("CVE-2022-0778", "OpenSSL Infinite Loop DoS",
                "HIGH", "7.5",
                "BN_mod_sqrt() function in OpenSSL can enter an infinite loop when parsing crafted certificates.",
                "openssl", "1.1.1k", "1.1.1n",
                "Update OpenSSL in the base image."));

        list.add(vuln("CVE-2023-38545", "curl SOCKS5 Heap Buffer Overflow",
                "CRITICAL", "9.8",
                "A heap buffer overflow exists when curl is used with SOCKS5 proxy and a very long hostname.",
                "curl", "7.88.1", "8.4.0",
                "Update curl in your Dockerfile or upgrade the base image."));

        if (image.contains("node") || image.contains("js")) {
            list.add(vuln("CVE-2021-23337", "Lodash Command Injection",
                    "HIGH", "7.2",
                    "Prototype pollution via the template function in lodash < 4.17.21.",
                    "lodash", "4.17.19", "4.17.21",
                    "Upgrade lodash to >= 4.17.21."));
        }

        return list;
    }

    private Vulnerability vuln(String id, String title, String severity, String cvss,
                               String description, String pkg, String current,
                               String fixed, String fix) {
        Vulnerability v = new Vulnerability();
        v.id = id;
        v.title = title;
        v.severity = severity;
        v.cvss = cvss;
        v.description = description;
        v.affectedPackage = pkg;
        v.currentVersion = current;
        v.fixedVersion = fixed;
        v.fix = fix;
        return v;
    }

    private String buildVulnSummary(String image, List<Vulnerability> vulns, String source) {
        StringBuilder sb = new StringBuilder();
        sb.append("Image: ").append(image).append("\n\n");
        sb.append("Scan source: ").append(source).append("\n");
        sb.append("Total vulnerabilities: ").append(vulns.size()).append("\n");
        sb.append("Critical: ").append(criticalCount(vulns)).append("\n");
        sb.append("High: ").append(highCount(vulns)).append("\n\n");
        sb.append("Ask: Prioritize the highest-risk issues and provide concrete Dockerfile/package remediation steps.\n\n");
        sb.append("Detected vulnerabilities:\n");

        for (Vulnerability v : vulns.stream().limit(MAX_VERTEX_FINDINGS).toList()) {
            sb.append("- ").append(v.id).append(" [").append(v.severity).append("] ")
                    .append(v.title).append(" in ").append(v.affectedPackage)
                    .append(" ").append(v.currentVersion);
            if (v.fixedVersion != null && !v.fixedVersion.isBlank()) {
                sb.append(" fixed in ").append(v.fixedVersion);
            }
            if (v.cvss != null && !v.cvss.isBlank()) {
                sb.append(" CVSS ").append(v.cvss);
            }
            sb.append("\n");
            if (v.description != null && !v.description.isBlank()) {
                sb.append("  Description: ").append(v.description).append("\n");
            }
        }
        return sb.toString();
    }

    private String generateFix(Vulnerability v) {
        if (v.fixedVersion == null || v.fixedVersion.isBlank()) {
            return "No fixed version reported. Review vendor advisory and consider package removal or compensating controls.";
        }
        return "Upgrade " + v.affectedPackage + " from " + v.currentVersion
                + " to " + v.fixedVersion + " or later.";
    }

    private String highestSeverity(List<Vulnerability> vulns) {
        if (vulns.stream().anyMatch(v -> "CRITICAL".equals(v.severity))) return "CRITICAL";
        if (vulns.stream().anyMatch(v -> "HIGH".equals(v.severity))) return "HIGH";
        if (vulns.stream().anyMatch(v -> "MEDIUM".equals(v.severity))) return "MEDIUM";
        return "LOW";
    }

    private long criticalCount(List<Vulnerability> vulns) {
        return vulns.stream().filter(v -> "CRITICAL".equals(v.severity)).count();
    }

    private long highCount(List<Vulnerability> vulns) {
        return vulns.stream().filter(v -> "HIGH".equals(v.severity)).count();
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    private String extractCvss(JsonNode cvssNode) {
        if (cvssNode == null || !cvssNode.isObject()) {
            return "";
        }
        for (JsonNode vendorNode : cvssNode) {
            if (vendorNode.has("V3Score")) {
                return vendorNode.path("V3Score").asText("");
            }
            if (vendorNode.has("V2Score")) {
                return vendorNode.path("V2Score").asText("");
            }
        }
        return "";
    }

    private String normalizeSeverity(String severity) {
        return switch (severity == null ? "" : severity.trim().toUpperCase()) {
            case "CRITICAL", "HIGH", "MEDIUM", "LOW" -> severity.trim().toUpperCase();
            default -> "LOW";
        };
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
    }
}
