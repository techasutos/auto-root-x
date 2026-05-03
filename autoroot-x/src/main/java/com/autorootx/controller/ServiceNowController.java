package com.autorootx.controller;

import com.autorootx.model.ServiceNowTicketRequest;
import com.autorootx.model.ServiceNowTicketResponse;
import com.autorootx.service.ServiceNowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/servicenow")
public class ServiceNowController {

    private final ServiceNowService service;

    public ServiceNowController(ServiceNowService s) {
        this.service = s;
    }

    /**
     * POST /api/servicenow/incidents
     * Create an incident ticket from an analysis result.
     */
    @PostMapping("/incidents")
    public ServiceNowTicketResponse createIncident(@Valid @RequestBody ServiceNowTicketRequest req) {
        return service.createIncident(req);
    }
}
