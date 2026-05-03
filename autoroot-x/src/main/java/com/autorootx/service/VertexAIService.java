package com.autorootx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

@Service
public class VertexAIService {

    @Value("${gcp.project:my-gcp-project}")
    private String projectId;

    @Value("${gcp.region:us-central1}")
    private String region;

    @Value("${vertex.model:gemini-1.5-pro}")
    private String model;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Send a typed analysis request to Vertex AI Gemini.
     *
     * @param analyzerType one of LOGS, IMAGE, OSS, GENERIC
     * @param input        raw input data to analyze
     * @return the AI-generated analysis text
     */
    public String analyze(String analyzerType, String input) throws Exception {
        String prompt = buildPrompt(analyzerType, input);
        return callGemini(prompt);
    }

    /** Fallback overload used by LogsAnalyzer (passes raw string). */
    public String analyze(String input) throws Exception {
        return analyze("LOGS", input);
    }

    // -----------------------------------------------------------------------
    // Prompt engineering
    // -----------------------------------------------------------------------

    private String buildPrompt(String analyzerType, String input) {
        return switch (analyzerType.toUpperCase()) {
            case "LOGS" -> """
                You are a senior Site Reliability Engineer (SRE) with deep expertise in Google Cloud Platform.

                Analyze the following GCP Cloud Logging error entries and provide a structured root-cause analysis.

                ## Log Data
                %s

                ## Required Response Format (strictly follow this structure)
                **SEVERITY**: [CRITICAL|HIGH|MEDIUM|LOW]
                **CONFIDENCE**: [percentage]

                **ROOT CAUSE**:
                [Concise technical explanation of the underlying cause]

                **IMPACT**:
                [What services, users, or data are affected and how]

                **RECOMMENDED FIX**:
                [Step-by-step remediation with specific commands or configuration changes]

                **PREVENTION**:
                [How to prevent this in the future � monitoring, alerting, or architectural changes]
                """.formatted(input);

            case "IMAGE" -> """
                You are a container security expert specializing in CVE analysis and supply-chain security.

                Analyze the following container image for security vulnerabilities. The image is: %s

                Consider:
                - Known CVEs in common base images (Alpine, Debian, Ubuntu, Red Hat)
                - Outdated package versions
                - Mis-configurations (running as root, world-writable files, exposed secrets)
                - OWASP Container Security Top 10

                ## Required Response Format
                **SEVERITY**: [CRITICAL|HIGH|MEDIUM|LOW] (overall risk)
                **CONFIDENCE**: [percentage]

                **SUMMARY**:
                [Brief overview of the security posture]

                **ROOT CAUSE**:
                [Primary reason for the vulnerability risk]

                **IMPACT**:
                [Potential attack vectors and blast radius]

                **RECOMMENDED FIX**:
                [Specific upgrade commands and Dockerfile changes]
                """.formatted(input);

            case "OSS" -> """
                You are an application security engineer specializing in software composition analysis (SCA).

                Analyze the following open-source dependencies for known vulnerabilities using OSV.dev data:

                ## Dependencies
                %s

                ## Required Response Format
                **SEVERITY**: [CRITICAL|HIGH|MEDIUM|LOW] (highest severity found)
                **CONFIDENCE**: [percentage]

                **SUMMARY**:
                [Overview of dependency health and total risk]

                **ROOT CAUSE**:
                [Which packages introduce the most risk and why]

                **IMPACT**:
                [Data exposure, RCE, DoS, or other attack scenarios]

                **RECOMMENDED FIX**:
                [Specific version upgrades and dependency substitutions]
                """.formatted(input);

            default -> """
                You are a cloud security and reliability expert.

                Analyze the following data and provide a structured root-cause analysis report:

                ## Input
                %s

                ## Required Response Format
                **SEVERITY**: [CRITICAL|HIGH|MEDIUM|LOW]
                **CONFIDENCE**: [percentage]
                **ROOT CAUSE**: [explanation]
                **IMPACT**: [affected systems]
                **RECOMMENDED FIX**: [actionable steps]
                """.formatted(input);
        };
    }

    // -----------------------------------------------------------------------
    // Vertex AI Gemini API call with Application Default Credentials
    // -----------------------------------------------------------------------

    private String callGemini(String prompt) throws Exception {
        String endpoint = String.format(
                "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
                region, projectId, region, model
        );

        String accessToken = getAccessToken();

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        String safePrompt = prompt.replace("\\", "\\\\")
                                  .replace("\"", "\\\"")
                                  .replace("\n", "\\n")
                                  .replace("\r", "\\r")
                                  .replace("\t", "\\t");

        String body = """
                {
                  "contents": [{
                    "parts": [{"text": "%s"}]
                  }],
                  "generationConfig": {
                    "temperature": 0.3,
                    "maxOutputTokens": 2048
                  }
                }
                """.formatted(safePrompt);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes());
        }

        int status = conn.getResponseCode();
        byte[] responseBytes = status < 400
                ? conn.getInputStream().readAllBytes()
                : conn.getErrorStream().readAllBytes();

        String responseBody = new String(responseBytes);

        if (status >= 400) {
            throw new RuntimeException("Vertex AI error " + status + ": " + responseBody);
        }

        return extractText(responseBody);
    }

    /**
     * Get OAuth2 access token using Application Default Credentials (ADC).
     * In GKE/Cloud Run this uses the workload identity / metadata server.
     * Locally, it uses gcloud application-default credentials.
     */
    private String getAccessToken() throws IOException {
        GoogleCredentials credentials = GoogleCredentials
                .getApplicationDefault()
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    private String extractText(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                return parts.get(0).path("text").asText("");
            }
        }
        // Fallback: return raw JSON for debugging
        return json;
    }
}
