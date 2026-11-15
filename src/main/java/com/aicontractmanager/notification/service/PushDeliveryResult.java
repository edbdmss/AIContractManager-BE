package com.aicontractmanager.notification.service;

import java.util.List;

public record PushDeliveryResult(int successCount, String error, List<String> invalidTokens) {
    public boolean hasSuccess() {
        return successCount > 0;
    }
}
