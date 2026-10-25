package com.aicontractmanager.auth.service;

import com.aicontractmanager.auth.domain.AuthUser;
import com.aicontractmanager.auth.dto.OAuthLoginResponse;
import com.aicontractmanager.auth.dto.OAuthProvider;
import com.aicontractmanager.auth.repository.OAuthUserRepository;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final Map<OAuthProvider, OAuthProviderClient> clients;
    private final OAuthUserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            List<OAuthProviderClient> clients,
            OAuthUserRepository userRepository,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.clients = new EnumMap<>(OAuthProvider.class);
        clients.forEach(client -> this.clients.put(client.provider(), client));
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public OAuthLoginResponse login(OAuthProvider provider, String code, String redirectUri, String state) {
        OAuthProviderClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("지원하지 않는 OAuth provider입니다.");
        }

        OAuthProfile profile = client.getProfile(code, redirectUri, state);
        AuthUser user = userRepository.upsert(provider, profile);
        return new OAuthLoginResponse(
                jwtTokenProvider.issue(user),
                "Bearer",
                jwtTokenProvider.expiresInSeconds(),
                user.id().toString(),
                user.email(),
                user.name(),
                user.provider(),
                user.newUser()
        );
    }
}
