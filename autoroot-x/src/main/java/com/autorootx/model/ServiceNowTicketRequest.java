package com.autorootx.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ServiceNowTicketRequest {

    @NotBlank(message = "title is required")
    public String title;

    @NotBlank(message = "description is required")
    public String description;

    /** CRITICAL, HIGH, MEDIUM, LOW */
    @NotBlank(message = "severity is required")
    public String severity;

    /** e.g. LOGS, IMAGE, OSS, GENERIC */
    public String analyzerType;

    /** Optional: raw analysis summary to include in ticket details */
    public String analysisSummary;

    /** Optional: component / service affected */
    public String affectedComponent;
}
