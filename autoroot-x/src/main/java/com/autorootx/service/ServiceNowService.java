package com.autorootx.service;

import com.autorootx.model.ServiceNowTicketRequest;
import com.autorootx.model.ServiceNowTicketResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class ServiceNowService {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${servicenow.instance-url:}")
    private String instanceUrl;

    @Value("${servicenow.username:}")
    private String username;

    @Value("${servicenow.password:}")
    private String password;

    @Value("${servicenow.mock-mode:true}")
    private boolean mockMode;

    /**
     * Create a ServiceNow incident ticket.
     * If servicenow.mock-mode=true (default) returns a mock ticket — useful
     * when a real ServiceNow instance isn't configured.
     */
    public ServiceNowTicketResponse createIncident(ServiceNowTicketRequest req) {
        if (mockMode || instanceUrl.isBlank()) {
            return mockTicket(req);
        }
        try {
            return callServiceNow(req);
        } catch (Exception e) {
            log.error("ServiceNow API call failed, returning mock", e);
            ServiceNowTicketResponse mock = mockTicket(req);
            mock.message = "Warning: ServiceNow API unavailable (" + e.getMessage() + "). Returning simulated ticket.";
            return mock;
        }
    }

    // -----------------------------------------------------------------------
    // ServiceNow Table API
    // -----------------------------------------------------------------------

    private ServiceNowTicketResponse callServiceNow(ServiceNowTicketRequest req) throws Exception {
        String url = instanceUrl.replaceAll("/+$", "") + "/api/now/table/incident";

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        String credentials = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + credentials);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);

        String body = buildIncidentJson(req);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        byte[] responseBytes = status < 400
                ? conn.getInputStream().readAllBytes()
                : conn.getErrorStream().readAllBytes();
        String responseBody = new String(responseBytes, StandardCharsets.UTF_8);

        if (status >= 400) {
            throw new RuntimeException("ServiceNow error " + status + ": " + responseBody);
        }

        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode result = root.path("result");

        ServiceNowTicketResponse resp = new ServiceNowTicketResponse();
        resp.ticketId = result.path("number").asText("INC-UNKNOWN");
        resp.status = result.path("state").asText("1"); // 1=New
        resp.priority = result.path("priority").asText(severityToPriority(req.severity));
        resp.url = instanceUrl.replaceAll("/+$", "") + "/nav_to.do?uri=incident.do?sys_id=" + result.path("sys_id").asText();
        resp.createdAt = Instant.now().toString();
        resp.message = "Ticket created successfully";
        return resp;
    }

    private String buildIncidentJson(ServiceNowTicketRequest req) {
        String urgency = severityToUrgency(req.severity);
        String priority = severityToPriority(req.severity);

        String description = "[AutoRoot-X Automated Incident]\n\n"
                + "Analyzer: " + nvl(req.analyzerType) + "\n"
                + "Severity: " + req.severity + "\n"
                + "Affected Component: " + nvl(req.affectedComponent) + "\n\n"
                + "Description:\n" + req.description + "\n\n"
                + (req.analysisSummary != null && !req.analysisSummary.isBlank()
                    ? "AI Analysis Summary:\n" + req.analysisSummary
                    : "");

        return """
                {
                  "short_description": %s,
                  "description": %s,
                  "urgency": %s,
                  "priority": %s,
                  "category": "software",
                  "subcategory": "security",
                  "caller_id": "autoroot-x"
                }
                """.formatted(
                        jsonString(req.title),
                        jsonString(description),
                        jsonString(urgency),
                        jsonString(priority));
    }

    // -----------------------------------------------------------------------
    // Mock mode
    // -----------------------------------------------------------------------

    private ServiceNowTicketResponse mockTicket(ServiceNowTicketRequest req) {
        String id = "INC" + String.format("%07d", Math.abs(UUID.randomUUID().hashCode() % 10_000_000));
        ServiceNowTicketResponse resp = new ServiceNowTicketResponse();
        resp.ticketId = id;
        resp.status = "New";
        resp.priority = severityToPriority(req.severity);
        resp.url = "https://demo.service-now.com/incident.do?number=" + id;
        resp.createdAt = Instant.now().toString();
        resp.message = "Mock ticket created (configure servicenow.* properties for production)";
        return resp;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String severityToUrgency(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase()) {
            case "CRITICAL" -> "1";
            case "HIGH"     -> "2";
            case "MEDIUM"   -> "3";
            default         -> "4";
        };
    }

    private String severityToPriority(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase()) {
            case "CRITICAL" -> "1 - Critical";
            case "HIGH"     -> "2 - High";
            case "MEDIUM"   -> "3 - Moderate";
            default         -> "4 - Low";
        };
    }

    private String nvl(String s) { return s == null ? "N/A" : s; }

    private String jsonString(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r") + "\"";
    }
}
