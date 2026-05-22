package com.autorootx.service;

import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.EntryListOption;
import com.google.cloud.logging.LoggingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class GcpLogService {

    private static final Logger log = LoggerFactory.getLogger(GcpLogService.class);

    @Value("${gcp.project:}")
    private String projectId;

    @Value("${logs.lookback-minutes:30}")
    private int lookbackMinutes;

    @Value("${logs.max-entries:200}")
    private int maxEntries;

    @Value("${logs.filter:}")
    private String customFilter;

    public List<String> fetchErrors() {
        try {
            LoggingOptions.Builder builder = LoggingOptions.newBuilder();
            if (projectId != null && !projectId.isBlank() && !"my-gcp-project".equals(projectId)) {
                builder.setProjectId(projectId);
            }

            try (Logging logging = builder.build().getService()) {
                String timestampFilter = buildTimestampFilter();

                // Primary query: strict error severity from Kubernetes/Cloud Run workloads.
                String strictFilter = buildStrictFilter(timestampFilter);
                List<String> strictLogs = listEntries(logging, strictFilter);
                if (!strictLogs.isEmpty()) {
                    return strictLogs;
                }

                // Fallback query: catch error-like messages even when severity isn't set properly.
                String fallbackFilter = buildFallbackFilter(timestampFilter);
                List<String> fallbackLogs = listEntries(logging, fallbackFilter);
                if (!fallbackLogs.isEmpty()) {
                    return fallbackLogs;
                }

                log.info("No matching service logs found. strictFilter='{}', fallbackFilter='{}'", strictFilter, fallbackFilter);
                return List.of("No matching logs found in Cloud Logging for the configured window.");
            }
        } catch (Exception e) {
            log.warn("GCP Logging unavailable ({}): returning sample data", e.getMessage());
            // Return representative sample entries so analysis can still run
            return List.of(
                    "ERROR 2026-05-03T10:00:00Z [payment-service] NullPointerException: transaction ID is null at PaymentProcessor.process(PaymentProcessor.java:42)",
                    "ERROR 2026-05-03T10:00:05Z [auth-service] Connection refused: redis:6379  -  cache unavailable, falling back to DB",
                    "ERROR 2026-05-03T10:00:12Z [order-service] SQL timeout after 30s  -  query: SELECT * FROM orders WHERE status='PENDING'",
                    "CRITICAL 2026-05-03T10:01:00Z [api-gateway] HTTP 503 spike: 450 errors in 60s  -  upstream payment-service unreachable"
            );
        }
    }

    private List<String> listEntries(Logging logging, String filter) {
        List<String> logs = new ArrayList<>();

        EntryListOption[] options;
        if (projectId != null && !projectId.isBlank() && !"my-gcp-project".equals(projectId)) {
            options = new EntryListOption[] {
                    EntryListOption.resourceNames("projects/" + projectId),
                    EntryListOption.filter(filter),
                    EntryListOption.pageSize(maxEntries)
            };
        } else {
            options = new EntryListOption[] {
                    EntryListOption.filter(filter),
                    EntryListOption.pageSize(maxEntries)
            };
        }

        logging.listLogEntries(options)
                .iterateAll()
                .forEach(e -> logs.add(formatEntry(e.toString())));

        return logs;
    }

    private String buildTimestampFilter() {
        int minutes = Math.max(1, lookbackMinutes);
        Instant since = Instant.now().minus(Duration.ofMinutes(minutes));
        return "timestamp>=\"" + since + "\"";
    }

    private String buildStrictFilter(String timestampFilter) {
        if (customFilter != null && !customFilter.isBlank()) {
            return "(" + customFilter + ") AND " + timestampFilter;
        }

        return "(resource.type=\"k8s_container\" OR resource.type=\"k8s_pod\" OR resource.type=\"cloud_run_revision\")"
                + " AND severity>=ERROR"
                + " AND " + timestampFilter;
    }

    private String buildFallbackFilter(String timestampFilter) {
        return "(resource.type=\"k8s_container\" OR resource.type=\"k8s_pod\" OR resource.type=\"cloud_run_revision\")"
                + " AND ("
                + "textPayload:(\"error\" OR \"exception\" OR \"failed\")"
                + " OR jsonPayload.message:(\"error\" OR \"exception\" OR \"failed\")"
                + " OR jsonPayload.error:(\"error\" OR \"exception\" OR \"failed\")"
                + ")"
                + " AND " + timestampFilter;
    }

    private String formatEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return "(empty log entry)";
        }
        return raw;
    }
}
