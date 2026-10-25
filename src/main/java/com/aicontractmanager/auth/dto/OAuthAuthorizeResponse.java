package com.aicontractmanager.auth.dto;

public record OAuthAuthorizeResponse(
        String authorizationUrl,
        String state
) {
}
