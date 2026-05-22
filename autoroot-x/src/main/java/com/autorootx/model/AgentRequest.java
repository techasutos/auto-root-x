package com.autorootx.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public class AgentRequest {
    @NotBlank(message = "problem is required")
    public String problem;

    public String context;

    public Map<String, Object> hints;
}