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
public class NaverOAuthProviderClient implements OAuthProviderClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String userInfoUri;

    public NaverOAuthProviderClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.oauth2.naver.client-id}") String clientId,
            @Value("${app.oauth2.naver.client-secret}") String clientSecret,
            @Value("${app.oauth2.naver.token-uri}") String tokenUri,
            @Value("${app.oauth2.naver.user-info-uri}") String userInfoUri
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public OAuthProfile getProfile(String code, String redirectUri, String state) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("state", state);

        JsonNode token = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        String accessToken = token.path("access_token").asText(null);
        if (accessToken == null) {
            throw new IllegalStateException("네이버 access token을 받을 수 없습니다.");
        }

        JsonNode profile = restClient.get()
                .uri(userInfoUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class)
                .path("response");

        return new OAuthProfile(
                profile.path("id").asText(null),
                profile.path("email").asText(null),
                firstNonBlank(profile.path("name").asText(null), profile.path("nickname").asText(null))
        );
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
