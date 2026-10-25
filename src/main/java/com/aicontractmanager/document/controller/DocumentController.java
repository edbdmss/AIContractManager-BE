package com.aicontractmanager.document.controller;

import com.aicontractmanager.document.domain.Document;
import com.aicontractmanager.document.dto.DocumentResponse;
import com.aicontractmanager.document.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentResponse> list(Principal principal, @PathVariable UUID contractId) {
        return service.list(userId(principal), contractId).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(Principal principal, @PathVariable UUID contractId, @PathVariable UUID id) {
        return DocumentResponse.from(service.get(userId(principal), contractId, id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(Principal principal, @PathVariable UUID contractId,
                                   @RequestPart("file") MultipartFile file) {
        return DocumentResponse.from(service.upload(userId(principal), contractId, file));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(Principal principal, @PathVariable UUID contractId, @PathVariable UUID id) {
        Document document = service.get(userId(principal), contractId, id);
        Resource resource = service.download(userId(principal), contractId, id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.originalFilename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(document.contentType()))
                .contentLength(document.fileSize())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable UUID contractId, @PathVariable UUID id) {
        service.delete(userId(principal), contractId, id);
    }

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
