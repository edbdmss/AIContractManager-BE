package com.aicontractmanager.auth.dto;

public record OAuthLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String userId,
        String email,
        String name,
        OAuthProvider provider,
        boolean newUser
) {
}
