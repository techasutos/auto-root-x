package com.autorootx.controller;

import com.autorootx.model.AdminPluginCreateRequest;
import com.autorootx.model.AdminPluginStatus;
import com.autorootx.plugin.AnalyzerRegistry;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AnalyzerRegistry registry;

    public AdminController(AnalyzerRegistry r) {
        this.registry = r;
    }

    @GetMapping("/plugins")
    public List<AdminPluginStatus> listPlugins() {
        return registry.listAllWithStatus();
    }

    @PutMapping("/plugins/{id}/enabled")
    public void setEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        if (enabled) {
            registry.enable(id);
            return;
        }
        registry.disable(id);
    }

    @PostMapping("/plugins")
    public void addPlugin(@Valid @RequestBody AdminPluginCreateRequest request) {
        registry.addDynamic(request);
    }
}