package com.aicontractmanager.document.service;

import com.aicontractmanager.contract.service.ContractService;
import com.aicontractmanager.document.domain.Document;
import com.aicontractmanager.document.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private ContractService contracts;
    private DocumentRepository repository;
    private LocalDocumentStorage storage;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        contracts = mock(ContractService.class);
        repository = mock(DocumentRepository.class);
        storage = mock(LocalDocumentStorage.class);
        service = new DocumentService(contracts, repository, storage);
    }

    @Test
    void blocksOtherUsersBeforeListingDocuments() {
        when(contracts.get(userId, contractId)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThrows(ResponseStatusException.class, () -> service.list(userId, contractId));
        verify(repository, never()).findAll(any());
    }

    @Test
    void rejectsExtensionThatDoesNotMatchFileSignature() {
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", "not a pdf".getBytes());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.upload(userId, contractId, file));

        assertEquals(400, exception.getStatusCode().value());
        verify(storage, never()).save(any(), anyString());
    }

    @Test
    void storesFileAndMetadataUnderContract() {
        byte[] bytes = "%PDF-1.7".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "folder/contract.pdf", "application/pdf", bytes);
        when(storage.save(bytes, ".pdf")).thenReturn("random.pdf");
        when(repository.findAll(contractId)).thenReturn(List.of());
        when(repository.find(eq(contractId), any(UUID.class))).thenAnswer(invocation -> Optional.of(new Document(
                invocation.getArgument(1), contractId, "CONTRACT", "contract.pdf", "random.pdf",
                "application/pdf", bytes.length, "checksum", true, LocalDateTime.now())));

        Document document = service.upload(userId, contractId, file);

        assertEquals("contract.pdf", document.originalFilename());
        verify(repository).create(any(UUID.class), eq(contractId), eq("contract.pdf"), eq("random.pdf"),
                eq("application/pdf"), eq((long) bytes.length), anyString(), eq(true));
    }
}
