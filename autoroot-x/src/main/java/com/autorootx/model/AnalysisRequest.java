package com.autorootx.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class AnalysisRequest {
    @NotBlank(message = "analyzerId is required")
    public String analyzerId;

    @NotNull(message = "payload is required")
    public Map<String, Object> payload;
}