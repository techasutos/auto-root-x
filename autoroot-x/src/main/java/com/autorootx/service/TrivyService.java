package com.autorootx.service;

import com.autorootx.model.Vulnerability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class TrivyService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${trivy.enabled:true}")
    private boolean trivyEnabled;

    @Value("${trivy.report-path:/var/run/autorootx/trivy/report.json}")
    private String trivyReportPath;

    @Value("${trivy.require-image-match:false}")
    private boolean requireImageMatch;

    public TrivyScanResult scanImage(String imageRef) {
        if (!trivyEnabled) {
            return new TrivyScanResult(false, List.of(), "", "Trivy integration disabled");
        }

        try {
            Path reportPath = Path.of(trivyReportPath);
            if (!Files.isRegularFile(reportPath)) {
                return new TrivyScanResult(false, List.of(), "", "Trivy report not found at " + trivyReportPath);
            }

            String json = Files.readString(reportPath);
            JsonNode root = MAPPER.readTree(json);
            String artifactName = root.path("ArtifactName").asText("");
            if (requireImageMatch && imageRef != null && !imageRef.isBlank() && !artifactName.isBlank() && !artifactName.equals(imageRef)) {
                return new TrivyScanResult(false, List.of(), json,
                        "Trivy report image mismatch. Requested " + imageRef + " but report is for " + artifactName);
            }

            return new TrivyScanResult(true, parseVulnerabilities(root), json, "Trivy report loaded from " + trivyReportPath);
        } catch (Exception e) {
            return new TrivyScanResult(false, List.of(), "", e.getMessage());
        }
    }

    private List<Vulnerability> parseVulnerabilities(JsonNode root) {
        List<Vulnerability> out = new ArrayList<>();
        JsonNode results = root.path("Results");
        if (!results.isArray()) {
            results = root.path("results");
        }
        if (!results.isArray()) {
            return out;
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
                v.id = node.path("VulnerabilityID").asText("UNKNOWN");
                v.title = node.path("Title").asText(v.id);
                v.severity = node.path("Severity").asText("UNKNOWN");
                v.description = node.path("Description").asText("");
                v.affectedPackage = node.path("PkgName").asText("");
                v.currentVersion = node.path("InstalledVersion").asText("");
                v.fixedVersion = node.path("FixedVersion").asText("");
                v.cvss = extractCvss(node.path("CVSS"));
                v.fix = (v.fixedVersion != null && !v.fixedVersion.isBlank())
                        ? "Upgrade " + v.affectedPackage + " to " + v.fixedVersion
                        : "No fixed version published yet. Apply mitigations and monitor vendor advisories.";
                out.add(v);
            }
        }

        return out;
    }

    private String extractCvss(JsonNode cvssNode) {
        if (cvssNode == null || !cvssNode.isObject()) {
            return "";
        }

        Iterator<String> names = cvssNode.fieldNames();
        while (names.hasNext()) {
            String vendor = names.next();
            JsonNode vendorNode = cvssNode.path(vendor);
            if (vendorNode.has("V3Score")) {
                return vendorNode.path("V3Score").asText("");
            }
            if (vendorNode.has("V2Score")) {
                return vendorNode.path("V2Score").asText("");
            }
        }
        return "";
    }

    public record TrivyScanResult(boolean available, List<Vulnerability> vulnerabilities, String rawResponse, String message) {}
}
