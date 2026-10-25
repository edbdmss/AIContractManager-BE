package com.aicontractmanager.auth.service;

import com.aicontractmanager.auth.dto.OAuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthStateStoreTest {

    private final OAuthStateStore store = new OAuthStateStore();

    @Test
    void rejectsWrongProviderWithoutConsumingValidState() {
        String state = store.create(OAuthProvider.KAKAO, "http://localhost:3000/oauth/callback");

        assertThrows(ResponseStatusException.class, () -> store.validateAndConsume(
                OAuthProvider.NAVER, "http://localhost:3000/oauth/callback", state));
        assertDoesNotThrow(() -> store.validateAndConsume(
                OAuthProvider.KAKAO, "http://localhost:3000/oauth/callback", state));
    }

    @Test
    void rejectsReplayedState() {
        String state = store.create(OAuthProvider.NAVER, "http://localhost:3000/oauth/callback");
        store.validateAndConsume(OAuthProvider.NAVER, "http://localhost:3000/oauth/callback", state);

        assertThrows(ResponseStatusException.class, () -> store.validateAndConsume(
                OAuthProvider.NAVER, "http://localhost:3000/oauth/callback", state));
    }
}
