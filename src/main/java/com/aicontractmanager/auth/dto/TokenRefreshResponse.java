package com.aicontractmanager.auth.dto;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn
) {
}
