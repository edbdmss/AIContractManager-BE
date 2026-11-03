package com.aicontractmanager.analysis.controller;

import com.aicontractmanager.analysis.dto.AiAnalysisResponse;
import com.aicontractmanager.analysis.service.AiAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/documents/{documentId}/analysis")
public class AiAnalysisController {

    private final AiAnalysisService service;

    public AiAnalysisController(AiAnalysisService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public AiAnalysisResponse analyze(Principal principal,
                                      @PathVariable UUID contractId,
                                      @PathVariable UUID documentId) {
        return service.analyze(userId(principal), contractId, documentId);
    }

    @GetMapping("/{analysisId}")
    public AiAnalysisResponse get(Principal principal,
                                  @PathVariable UUID contractId,
                                  @PathVariable UUID documentId,
                                  @PathVariable UUID analysisId) {
        return service.get(userId(principal), contractId, documentId, analysisId);
    }

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
