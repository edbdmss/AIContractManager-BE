package com.aicontractmanager.auth.service;

public record OAuthProfile(
        String providerUserId,
        String email,
        String name
) {
}
