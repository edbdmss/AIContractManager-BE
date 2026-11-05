package com.aicontractmanager.schedule.controller;

import com.aicontractmanager.schedule.domain.ContractSchedule;
import com.aicontractmanager.schedule.dto.ContractScheduleRequest;
import com.aicontractmanager.schedule.service.ContractScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ContractScheduleController {
    private final ContractScheduleService service;

    public ContractScheduleController(ContractScheduleService service) {
        this.service = service;
    }

    @GetMapping("/schedules/upcoming")
    public List<ContractSchedule> upcoming(Principal principal) {
        return service.listUpcoming(userId(principal));
    }

    @GetMapping("/contracts/{contractId}/schedules")
    public List<ContractSchedule> list(Principal principal, @PathVariable UUID contractId) {
        return service.list(userId(principal), contractId);
    }

    @PostMapping("/contracts/{contractId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractSchedule create(Principal principal, @PathVariable UUID contractId,
                                   @Valid @RequestBody ContractScheduleRequest request) {
        return service.create(userId(principal), contractId, request);
    }

    @PostMapping("/schedules/{scheduleId}/complete")
    public ContractSchedule complete(Principal principal, @PathVariable UUID scheduleId) {
        return service.complete(userId(principal), scheduleId);
    }

    @PostMapping("/schedules/{scheduleId}/dismiss")
    public ContractSchedule dismiss(Principal principal, @PathVariable UUID scheduleId) {
        return service.dismiss(userId(principal), scheduleId);
    }

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
