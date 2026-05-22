package com.autorootx.controller;

import com.autorootx.model.AgentRequest;
import com.autorootx.model.AgentResult;
import com.autorootx.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/analyze")
    public AgentResult analyze(@Valid @RequestBody AgentRequest request) {
        return agentService.analyze(request);
    }
}