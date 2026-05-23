package com.autorootx.plugin.impl;

import com.autorootx.model.AnalysisRequest;
import com.autorootx.model.AnalysisResult;
import com.autorootx.service.VertexAIService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImageAnalyzerTests {

    @Test
    void appendsTrivyScanSummaryToVertexPrompt() {
        CapturingVertexAIService ai = new CapturingVertexAIService();
        ImageAnalyzer analyzer = new ImageAnalyzer(ai);

        AnalysisRequest request = new AnalysisRequest();
        request.analyzerId = "IMAGE";
        request.payload = Map.of(
                "image", "us-central1-docker.pkg.dev/demo/apps/api:latest",
                "trivyReport", """
                        {
                          "SchemaVersion": 2,
                          "ArtifactName": "us-central1-docker.pkg.dev/demo/apps/api:latest",
                          "Results": [{
                            "Target": "debian:12",
                            "Vulnerabilities": [{
                              "VulnerabilityID": "CVE-2023-38545",
                              "PkgName": "curl",
                              "InstalledVersion": "7.88.1",
                              "FixedVersion": "8.4.0",
                              "Severity": "CRITICAL",
                              "Title": "curl SOCKS5 heap buffer overflow",
                              "Description": "A heap buffer overflow exists in curl.",
                              "CVSS": {
                                "nvd": {
                                  "V3Score": 9.8
                                }
                              }
                            }]
                          }]
                        }
                        """
        );

        AnalysisResult result = analyzer.analyze(request);

        assertThat(ai.analyzerType).isEqualTo("IMAGE");
        assertThat(ai.prompt).contains("Scan source: Trivy sidecar scan");
        assertThat(ai.prompt).contains("CVE-2023-38545", "curl", "fixed in 8.4.0");
        assertThat(result.confidence).isEqualTo("TRIVY");
        assertThat(result.severity).isEqualTo("CRITICAL");
        assertThat(result.vulnerabilities).hasSize(1);
        assertThat(result.vulnerabilities.get(0).affectedPackage).isEqualTo("curl");
        assertThat(result.summary).isEqualTo("vertex remediation");
    }

    private static class CapturingVertexAIService extends VertexAIService {
        private String analyzerType;
        private String prompt;

        @Override
        public String analyze(String analyzerType, String input) {
            this.analyzerType = analyzerType;
            this.prompt = input;
            return "vertex remediation";
        }
    }
}
