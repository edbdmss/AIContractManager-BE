package com.aicontractmanager.auth.service;

import com.aicontractmanager.auth.dto.OAuthProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class OAuthStateStore {

    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private final ConcurrentMap<String, PendingAuthorization> pending = new ConcurrentHashMap<>();

    public String create(OAuthProvider provider, String redirectUri) {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> entry.getValue().createdAt().plus(STATE_TTL).isBefore(now));

        String state = UUID.randomUUID().toString();
        pending.put(state, new PendingAuthorization(provider, redirectUri, now));
        return state;
    }

    public void validateAndConsume(OAuthProvider provider, String redirectUri, String state) {
        PendingAuthorization authorization = pending.remove(state);
        if (authorization == null
                || authorization.createdAt().plus(STATE_TTL).isBefore(Instant.now())
                || authorization.provider() != provider
                || !authorization.redirectUri().equals(redirectUri)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 OAuth state입니다.");
        }
    }

    private record PendingAuthorization(OAuthProvider provider, String redirectUri, Instant createdAt) {
    }
}
