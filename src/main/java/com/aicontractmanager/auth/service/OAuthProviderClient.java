package com.aicontractmanager.auth.service;

import com.aicontractmanager.auth.dto.OAuthProvider;

public interface OAuthProviderClient {

    OAuthProvider provider();

    OAuthProfile getProfile(String code, String redirectUri, String state);
}
