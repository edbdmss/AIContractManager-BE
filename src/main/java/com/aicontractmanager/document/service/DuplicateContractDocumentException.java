package com.aicontractmanager.document.service;

public class DuplicateContractDocumentException extends RuntimeException {
    public DuplicateContractDocumentException() {
        super("이미 등록된 계약 문서예요. 내 계약에서 기존 계약을 확인해 주세요.");
    }
}
