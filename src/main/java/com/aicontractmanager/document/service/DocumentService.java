package com.aicontractmanager.document.service;

import com.aicontractmanager.contract.service.ContractService;
import com.aicontractmanager.document.domain.Document;
import com.aicontractmanager.document.repository.DocumentRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private final ContractService contracts;
    private final DocumentRepository repository;
    private final LocalDocumentStorage storage;

    public DocumentService(ContractService contracts, DocumentRepository repository, LocalDocumentStorage storage) {
        this.contracts = contracts;
        this.repository = repository;
        this.storage = storage;
    }

    public List<Document> list(UUID userId, UUID contractId) {
        contracts.get(userId, contractId);
        return repository.findAll(contractId);
    }

    public Document get(UUID userId, UUID contractId, UUID id) {
        contracts.get(userId, contractId);
        return repository.find(contractId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
    }

    public Document upload(UUID userId, UUID contractId, MultipartFile file) {
        contracts.get(userId, contractId);
        String filename = filename(file.getOriginalFilename());
        if (file.isEmpty() || file.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기는 1바이트 이상 20MB 이하여야 합니다.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드 파일을 읽지 못했습니다.", exception);
        }
        if (bytes.length == 0 || bytes.length > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기는 1바이트 이상 20MB 이하여야 합니다.");
        }
        FileType type = fileType(filename, bytes);
        String checksum = sha256(bytes);
        String key = storage.save(bytes, type.extension());
        UUID id = UUID.randomUUID();
        try {
            repository.create(id, contractId, filename, key, type.contentType(), bytes.length,
                    checksum, repository.findAll(contractId).isEmpty());
        } catch (RuntimeException exception) {
            storage.remove(key);
            throw exception;
        }
        return get(userId, contractId, id);
    }

    public Resource download(UUID userId, UUID contractId, UUID id) {
        return storage.load(get(userId, contractId, id).storageKey());
    }

    public void delete(UUID userId, UUID contractId, UUID id) {
        get(userId, contractId, id);
        repository.delete(contractId, id);
    }

    private String filename(String original) {
        if (original == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일명이 필요합니다.");
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255 || name.contains("\r") || name.contains("\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 파일명입니다.");
        }
        return name;
    }

    private FileType fileType(String filename, byte[] bytes) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> checked(bytes, new byte[]{'%', 'P', 'D', 'F', '-'}, ".pdf", "application/pdf");
            case "png" -> checked(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10},
                    ".png", "image/png");
            case "jpg", "jpeg" -> checked(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                    ".jpg", "image/jpeg");
            case "hwp" -> checked(bytes, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                    (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1}, ".hwp", "application/x-hwp");
            case "docx" -> checked(bytes, new byte[]{'P', 'K', 3, 4}, ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "hwpx" -> checked(bytes, new byte[]{'P', 'K', 3, 4}, ".hwpx", "application/zip");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지원 파일 형식: PDF, HWP, HWPX, DOCX, JPG, PNG");
        };
    }

    private FileType checked(byte[] bytes, byte[] signature, String extension, String contentType) {
        if (bytes.length < signature.length || !Arrays.equals(bytes, 0, signature.length, signature, 0, signature.length)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "확장자와 파일 내용이 일치하지 않습니다.");
        }
        return new FileType(extension, contentType);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private record FileType(String extension, String contentType) {
    }
}
