package com.aicontractmanager.auth.dto;

public record OAuthLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        String userId,
        String email,
        String name,
        OAuthProvider provider,
        boolean newUser
) {
}
