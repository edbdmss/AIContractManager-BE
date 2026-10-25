package com.aicontractmanager.auth.controller;

import com.aicontractmanager.auth.dto.OAuthLoginRequest;
import com.aicontractmanager.auth.dto.OAuthLoginResponse;
import com.aicontractmanager.auth.dto.OAuthProvider;
import com.aicontractmanager.auth.dto.OAuthAuthorizeResponse;
import com.aicontractmanager.auth.service.AuthService;
import com.aicontractmanager.auth.service.OAuthStateStore;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final OAuthStateStore stateStore;
    private final String kakaoClientId;
    private final String naverClientId;

    public AuthController(
            AuthService authService,
            OAuthStateStore stateStore,
            @Value("${app.oauth2.kakao.client-id}") String kakaoClientId,
            @Value("${app.oauth2.naver.client-id}") String naverClientId
    ) {
        this.authService = authService;
        this.stateStore = stateStore;
        this.kakaoClientId = kakaoClientId;
        this.naverClientId = naverClientId;
    }

    @GetMapping("/{provider}/authorize")
    public OAuthAuthorizeResponse authorize(
            @PathVariable String provider,
            @RequestParam String redirectUri
    ) {
        OAuthProvider oauthProvider = OAuthProvider.valueOf(provider.toUpperCase());
        String state = stateStore.create(oauthProvider, redirectUri);
        String clientId = oauthProvider == OAuthProvider.KAKAO ? kakaoClientId : naverClientId;
        String authorizeUri = oauthProvider == OAuthProvider.KAKAO
                ? "https://kauth.kakao.com/oauth/authorize"
                : "https://nid.naver.com/oauth2.0/authorize";

        String authorizationUrl = UriComponentsBuilder.fromUriString(authorizeUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        return new OAuthAuthorizeResponse(authorizationUrl, state);
    }

    @PostMapping("/{provider}/login")
    public OAuthLoginResponse login(
            @PathVariable String provider,
            @Valid @RequestBody OAuthLoginRequest request
    ) {
        OAuthProvider oauthProvider = OAuthProvider.valueOf(provider.toUpperCase());
        stateStore.validateAndConsume(oauthProvider, request.redirectUri(), request.state());
        return authService.login(
                oauthProvider,
                request.code(),
                request.redirectUri(),
                request.state()
        );
    }
}
