package com.aicontractmanager.auth.domain;

import com.aicontractmanager.auth.dto.OAuthProvider;

import java.util.UUID;

public record AuthUser(
        UUID id,
        String email,
        String name,
        OAuthProvider provider,
        boolean newUser
) {
}
