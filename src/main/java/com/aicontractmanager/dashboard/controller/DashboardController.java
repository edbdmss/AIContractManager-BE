package com.aicontractmanager.dashboard.controller;

import com.aicontractmanager.dashboard.dto.DashboardResponse;
import com.aicontractmanager.dashboard.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/aicontract/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse get(Principal principal,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.get(UUID.fromString(principal.getName()), month == null ? YearMonth.now() : month);
    }
}
