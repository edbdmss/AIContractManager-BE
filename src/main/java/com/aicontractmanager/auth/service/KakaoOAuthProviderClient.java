package com.aicontractmanager.auth.service;

import com.aicontractmanager.auth.dto.OAuthProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoOAuthProviderClient implements OAuthProviderClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String userInfoUri;

    public KakaoOAuthProviderClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.oauth2.kakao.client-id}") String clientId,
            @Value("${app.oauth2.kakao.client-secret:}") String clientSecret,
            @Value("${app.oauth2.kakao.token-uri}") String tokenUri,
            @Value("${app.oauth2.kakao.user-info-uri}") String userInfoUri
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public OAuthProfile getProfile(String code, String redirectUri, String state) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        JsonNode token = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        String accessToken = token.path("access_token").asText(null);
        if (accessToken == null) {
            throw new IllegalStateException("카카오 access token을 받을 수 없습니다.");
        }

        JsonNode profile = restClient.get()
                .uri(userInfoUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class);

        String providerUserId = profile.path("id").asText(null);
        String email = profile.path("kakao_account").path("email").asText(null);
        String name = profile.path("kakao_account").path("profile").path("nickname").asText(null);
        return new OAuthProfile(providerUserId, email, name);
    }
}
