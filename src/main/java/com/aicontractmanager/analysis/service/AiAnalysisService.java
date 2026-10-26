package com.aicontractmanager.analysis.service;

import com.aicontractmanager.analysis.dto.AiAnalysisResponse;
import com.aicontractmanager.analysis.repository.AiAnalysisRepository;
import com.aicontractmanager.contract.service.ContractService;
import com.aicontractmanager.document.domain.Document;
import com.aicontractmanager.document.service.DocumentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AiAnalysisService {

    private final ContractService contracts;
    private final DocumentService documents;
    private final AiAnalysisRepository repository;
    private final RestClient restClient;
    private final boolean enabled;
    private final String analyzePath;

    public AiAnalysisService(
            ContractService contracts,
            DocumentService documents,
            AiAnalysisRepository repository,
            RestClient.Builder restClientBuilder,
            @Value("${app.ai.enabled:true}") boolean enabled,
            @Value("${app.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.ai.analyze-path:/internal/analysis/contracts}") String analyzePath
    ) {
        this.contracts = contracts;
        this.documents = documents;
        this.repository = repository;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.enabled = enabled;
        this.analyzePath = analyzePath;
    }

    public AiAnalysisResponse analyze(UUID userId, UUID contractId, UUID documentId) {
        contracts.get(userId, contractId);
        Document document = documents.get(userId, contractId, documentId);
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 분석 기능이 비활성화되어 있습니다.");
        }

        UUID analysisId = UUID.randomUUID();
        repository.create(analysisId, documentId, "v1");
        try {
            Resource resource = documents.download(userId, contractId, documentId);
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("documentId", documentId.toString());
            body.part("file", resource)
                    .filename(document.originalFilename())
                    .contentType(MediaType.parseMediaType(document.contentType()));

            JsonNode result = restClient.post()
                    .uri(analyzePath)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(JsonNode.class);
            if (result == null) {
                throw new IllegalStateException("AI 서비스가 분석 결과를 반환하지 않았습니다.");
            }
            repository.complete(analysisId, result, confidence(result));
            return repository.find(analysisId);
        } catch (RestClientException | IllegalStateException exception) {
            repository.fail(analysisId, exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 분석 서비스와 통신하지 못했습니다.", exception);
        }
    }

    public AiAnalysisResponse get(UUID userId, UUID contractId, UUID documentId, UUID analysisId) {
        contracts.get(userId, contractId);
        documents.get(userId, contractId, documentId);
        AiAnalysisResponse response = repository.find(analysisId);
        if (!response.documentId().equals(documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다.");
        }
        return response;
    }

    private Double confidence(JsonNode result) {
        JsonNode value = result.get("confidence");
        if (value == null || !value.isNumber()) return null;
        BigDecimal confidence = value.decimalValue();
        return confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE).doubleValue();
    }
}
