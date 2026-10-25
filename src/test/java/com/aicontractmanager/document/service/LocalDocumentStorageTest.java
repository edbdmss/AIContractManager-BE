package com.aicontractmanager.document.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalDocumentStorageTest {

    @TempDir
    Path directory;

    @Test
    void writesAndReadsGeneratedKey() throws Exception {
        LocalDocumentStorage storage = new LocalDocumentStorage(directory.toString());
        byte[] bytes = "%PDF-1.7".getBytes();

        String key = storage.save(bytes, ".pdf");

        assertArrayEquals(bytes, storage.load(key).getInputStream().readAllBytes());
        storage.remove(key);
        assertThrows(ResponseStatusException.class, () -> storage.load(key));
    }

    @Test
    void rejectsTraversalKey() {
        LocalDocumentStorage storage = new LocalDocumentStorage(directory.toString());

        assertThrows(ResponseStatusException.class, () -> storage.load("../private.txt"));
    }
}
