package com.autorootx.service;

import com.autorootx.model.Vulnerability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class TrivyService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${trivy.url:http://localhost:4954}")
    private String trivyUrl;

    @Value("${trivy.enabled:true}")
    private boolean trivyEnabled;

    @Value("${trivy.scan-endpoint:/v1/scan}")
    private String trivyScanEndpoint;

    @Value("${trivy.expected-major:0}")
    private int trivyExpectedMajor;

    public TrivyScanResult scanImage(String imageRef) {
        if (!trivyEnabled || imageRef == null || imageRef.isBlank()) {
            return new TrivyScanResult(false, List.of(), "");
        }

        try {
            String baseUrl = trivyUrl.replaceAll("/+$", "");
            String health = get(baseUrl + "/healthz");
            if (health == null) {
                return new TrivyScanResult(false, List.of(), "Trivy sidecar health check failed");
            }

            String versionJson = get(baseUrl + "/version");
            if (versionJson == null || !isCompatibleVersion(versionJson)) {
                return new TrivyScanResult(false, List.of(), "Unsupported Trivy version or unable to resolve Trivy version");
            }

            String endpoint = baseUrl + normalizeEndpoint(trivyScanEndpoint);
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);

            String body = """
                    {
                      "target": %s
                    }
                    """.formatted(jsonString(imageRef));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            byte[] responseBytes = status < 400
                    ? conn.getInputStream().readAllBytes()
                    : conn.getErrorStream() != null ? conn.getErrorStream().readAllBytes() : new byte[0];
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);

            if (status >= 400) {
                return new TrivyScanResult(false, List.of(), responseBody);
            }

            List<Vulnerability> vulnerabilities = parseVulnerabilities(responseBody);
            return new TrivyScanResult(true, vulnerabilities, responseBody);
        } catch (Exception e) {
            return new TrivyScanResult(false, List.of(), e.getMessage());
        }
    }

    private String get(String endpoint) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(10_000);
            int status = conn.getResponseCode();
            if (status >= 400) {
                return null;
            }
            try (InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isCompatibleVersion(String versionJson) {
        try {
            JsonNode node = MAPPER.readTree(versionJson);
            String version = node.path("Version").asText("");
            if (version.isBlank()) {
                return false;
            }
            String clean = version.startsWith("v") ? version.substring(1) : version;
            String[] parts = clean.split("\\.");
            if (parts.length == 0) {
                return false;
            }
            int major = Integer.parseInt(parts[0]);
            return major == trivyExpectedMajor;
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "/v1/scan";
        }
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    private List<Vulnerability> parseVulnerabilities(String json) throws Exception {
        List<Vulnerability> out = new ArrayList<>();
        JsonNode root = MAPPER.readTree(json);
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

    private String jsonString(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    public record TrivyScanResult(boolean available, List<Vulnerability> vulnerabilities, String rawResponse) {}
}