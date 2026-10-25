package com.aicontractmanager.document.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Component
public class LocalDocumentStorage {

    private final Path root;

    public LocalDocumentStorage(@Value("${app.document.storage-dir}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public String save(byte[] bytes, String extension) {
        String key = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(key), bytes, StandardOpenOption.CREATE_NEW);
            return key;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "문서를 저장하지 못했습니다.", exception);
        }
    }

    public Resource load(String key) {
        Path path = pathFor(key);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        return new FileSystemResource(path);
    }

    public void remove(String key) {
        try {
            Files.deleteIfExists(pathFor(key));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "문서 저장을 정리하지 못했습니다.", exception);
        }
    }

    private Path pathFor(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 문서 경로입니다.");
        }
        return path;
    }
}
