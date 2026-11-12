package com.aicontractmanager.auth.dto;

import java.util.UUID;

public record UserProfileResponse(UUID userId, String email, String name, OAuthProvider provider) {
}
