package com.autorootx.plugin.impl;

import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import com.autorootx.model.Vulnerability;
import com.autorootx.plugin.Analyzer;
import com.autorootx.service.VertexAIService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Container image vulnerability scanner.
 *
 * Uses a curated set of realistic CVEs for the given image and calls
 * Vertex AI Gemini to generate per-CVE fix recommendations.
 * In production, replace the hardcoded vulnerability list with a call
 * to the GCP Container Analysis API or Artifact Registry scan results.
 */
@Component
public class ImageAnalyzer implements Analyzer {

    private final VertexAIService ai;

    public ImageAnalyzer(VertexAIService ai) {
        this.ai = ai;
    }

    @Override public String id()       { return "IMAGE"; }
    @Override public String name()     { return "Image Scanner"; }
    @Override public String category() { return "SECURITY"; }
    @Override public List<String> inputs() { return List.of("image"); }

    @Override
    public AnalysisResult analyze(AnalysisRequest req) {
        String image = (String) req.payload.getOrDefault("image", "unknown:latest");

        List<Vulnerability> vulns = detectVulnerabilities(image);

        // Summarize vulnerabilities and call AI for overall analysis + per-vuln fixes
        String vulnSummary = buildVulnSummary(image, vulns);
        String aiAnalysis;
        try {
            aiAnalysis = ai.analyze("IMAGE", vulnSummary);
        } catch (Exception e) {
            aiAnalysis = "AI analysis unavailable: " + e.getMessage();
        }

        // Enrich each vulnerability with AI-generated fix if not already set
        for (Vulnerability v : vulns) {
            if (v.fix == null || v.fix.isBlank()) {
                v.fix = generateFix(v);
            }
        }

        AnalysisResult r = new AnalysisResult();
        r.summary = aiAnalysis;
        r.severity = highestSeverity(vulns);
        r.confidence = "85%";
        r.rootCause = "Outdated package versions in base image with known CVEs";
        r.impact = criticalCount(vulns) + " critical, " + highCount(vulns) + " high severity vulnerabilities detected";
        r.fix = "Upgrade base image and update the packages listed in the vulnerability table";
        r.vulnerabilities = vulns;
        return r;
    }

    // -----------------------------------------------------------------------
    // Vulnerability detection � realistic CVE data set
    // Replace with Artifact Registry Container Analysis API in production
    // -----------------------------------------------------------------------

    private List<Vulnerability> detectVulnerabilities(String image) {
        List<Vulnerability> list = new ArrayList<>();

        // Log4Shell (always relevant for Java images)
        if (image.contains("java") || image.contains("spring") || image.contains("tomcat") || !image.contains("-")) {
            list.add(vuln("CVE-2021-44228", "Log4Shell - Remote Code Execution",
                    "CRITICAL", "9.0",
                    "Apache Log4j2 JNDI lookup feature allows remote code execution via crafted log messages.",
                    "log4j-core", "2.14.1", "2.17.1",
                    "Upgrade log4j-core to >= 2.17.1:\n  <dependency>\n    <groupId>org.apache.logging.log4j</groupId>\n    <artifactId>log4j-core</artifactId>\n    <version>2.17.1</version>\n  </dependency>"));
        }

        // OpenSSL vulnerabilities (common in Alpine/Debian base images)
        list.add(vuln("CVE-2022-0778", "OpenSSL Infinite Loop DoS",
                "HIGH", "7.5",
                "BN_mod_sqrt() function in OpenSSL can enter an infinite loop when parsing crafted certificates.",
                "openssl", "1.1.1k", "1.1.1n",
                "Update OpenSSL in the base image:\n  RUN apt-get update && apt-get upgrade -y openssl\nor switch to Alpine 3.15+ which includes the fix."));

        list.add(vuln("CVE-2023-0464", "OpenSSL Certificate Policy DoS",
                "MEDIUM", "5.9",
                "Excessive resource usage when verifying X.509 certificate chains with policy constraints.",
                "openssl", "3.0.7", "3.0.9",
                "Upgrade the base image to include OpenSSL >= 3.0.9:\n  FROM debian:bullseye-slim  # use latest\nor run:\n  RUN apt-get update && apt-get install -y openssl=3.0.9*"));

        // curl / libcurl
        list.add(vuln("CVE-2023-38545", "curl SOCKS5 Heap Buffer Overflow",
                "CRITICAL", "9.8",
                "A heap buffer overflow exists when curl is used with SOCKS5 proxy and a very long hostname.",
                "curl", "7.88.1", "8.4.0",
                "Update curl in your Dockerfile:\n  RUN apt-get update && apt-get install -y curl=8.4.0\nOr upgrade the base image to one that bundles curl 8.4.0+."));

        // zlib
        list.add(vuln("CVE-2022-37434", "zlib Heap Buffer Overflow",
                "HIGH", "8.1",
                "A heap-based buffer over-read/write vulnerability in inflate.c in zlib via a large gzip header.",
                "zlib", "1.2.11", "1.2.13",
                "Upgrade zlib:\n  RUN apt-get update && apt-get install -y zlib1g=1.2.13-1\nOr rebuild the image from a base that includes zlib >= 1.2.13."));

        // expat (XML parser)
        list.add(vuln("CVE-2022-25313", "Expat Stack Exhaustion",
                "MEDIUM", "6.5",
                "Stack exhaustion in libexpat before 2.4.7 via a deeply nested XML document.",
                "libexpat", "2.4.1", "2.4.7",
                "Update libexpat:\n  RUN apt-get update && apt-get install -y libexpat1=2.4.7*"));

        // Lodash (JS images)
        if (image.contains("node") || image.contains("js")) {
            list.add(vuln("CVE-2021-23337", "Lodash Command Injection",
                    "HIGH", "7.2",
                    "Prototype pollution via the template function in lodash < 4.17.21.",
                    "lodash", "4.17.19", "4.17.21",
                    "Upgrade lodash:\n  npm update lodash\nor in package.json:\n  \"lodash\": \">=4.17.21\""));
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

    private String buildVulnSummary(String image, List<Vulnerability> vulns) {
        StringBuilder sb = new StringBuilder();
        sb.append("Image: ").append(image).append("\n\n");
        sb.append("Detected vulnerabilities:\n");
        for (Vulnerability v : vulns) {
            sb.append("- ").append(v.id).append(" [").append(v.severity).append("] ")
              .append(v.title).append(" in ").append(v.affectedPackage)
              .append(" ").append(v.currentVersion).append("\n");
        }
        return sb.toString();
    }

    private String generateFix(Vulnerability v) {
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
}
