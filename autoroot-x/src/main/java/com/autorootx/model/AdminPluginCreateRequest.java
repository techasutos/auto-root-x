package com.autorootx.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class AdminPluginCreateRequest {

    @NotBlank(message = "id is required")
    @Pattern(regexp = "^[A-Z0-9_\\-]+$", message = "id must use uppercase letters, numbers, underscore or hyphen")
    public String id;

    @NotBlank(message = "name is required")
    public String name;

    @NotBlank(message = "category is required")
    public String category;

    @NotEmpty(message = "inputs must have at least one field")
    public List<String> inputs;

    public String summaryTemplate;
}
