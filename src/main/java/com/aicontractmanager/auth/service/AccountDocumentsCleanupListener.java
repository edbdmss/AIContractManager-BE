package com.aicontractmanager.auth.service;

import com.aicontractmanager.document.service.LocalDocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AccountDocumentsCleanupListener {
    private static final Logger log = LoggerFactory.getLogger(AccountDocumentsCleanupListener.class);
    private final LocalDocumentStorage storage;

    public AccountDocumentsCleanupListener(LocalDocumentStorage storage) {
        this.storage = storage;
    }

    @TransactionalEventListener
    public void removeDocuments(AccountDeletionService.AccountDocumentsCleanupRequested event) {
        for (String key : event.storageKeys()) {
            try {
                storage.remove(key);
            } catch (RuntimeException exception) {
                log.error("탈퇴 계정 문서 파일 삭제 실패. storageKey={}", key, exception);
            }
        }
    }
}
