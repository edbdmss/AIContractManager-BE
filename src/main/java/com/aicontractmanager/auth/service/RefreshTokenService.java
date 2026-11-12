package com.aicontractmanager.auth.service;

import com.aicontractmanager.auth.dto.TokenRefreshResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final JwtTokenProvider jwtTokenProvider;
    private final Duration expiration;

    public RefreshTokenService(JdbcTemplate jdbc, JwtTokenProvider jwtTokenProvider,
                               @Value("${app.jwt.refresh-token-expiration-ms:2592000000}") long expirationMs) {
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("Refresh Token 만료 시간은 0보다 커야 합니다.");
        }
        this.jdbc = jdbc;
        this.jwtTokenProvider = jwtTokenProvider;
        this.expiration = Duration.ofMillis(expirationMs);
    }

    @Transactional
    public TokenRefreshResponse issue(UUID userId) {
        if (!isActiveUser(userId)) {
            throw unauthorized();
        }
        String refreshToken = newToken();
        save(userId, refreshToken);
        return response(userId, refreshToken);
    }

    @Transactional
    public TokenRefreshResponse rotate(String currentToken) {
        String hash = hash(currentToken);
        var rows = jdbc.query("""
                SELECT id, user_id, expires_at, revoked_at
                FROM user_refresh_tokens WHERE token_hash = ? FOR UPDATE
                """, (rs, row) -> new TokenRow(UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                rs.getObject("expires_at", LocalDateTime.class),
                rs.getObject("revoked_at", LocalDateTime.class)), hash);
        if (rows.isEmpty()) {
            throw unauthorized();
        }
        TokenRow current = rows.get(0);
        if (current.revokedAt() != null || !current.expiresAt().isAfter(LocalDateTime.now())
                || !isActiveUser(current.userId())) {
            throw unauthorized();
        }

        int revoked = jdbc.update("""
                UPDATE user_refresh_tokens SET revoked_at = CURRENT_TIMESTAMP
                WHERE id = ? AND revoked_at IS NULL
                """, current.id().toString());
        if (revoked != 1) {
            throw unauthorized();
        }
        String replacement = newToken();
        save(current.userId(), replacement);
        return response(current.userId(), replacement);
    }

    @Transactional
    public void revoke(String refreshToken) {
        jdbc.update("""
                UPDATE user_refresh_tokens SET revoked_at = CURRENT_TIMESTAMP
                WHERE token_hash = ? AND revoked_at IS NULL
                """, hash(refreshToken));
    }

    private TokenRefreshResponse response(UUID userId, String refreshToken) {
        return new TokenRefreshResponse(jwtTokenProvider.issueForUserId(userId), refreshToken,
                "Bearer", jwtTokenProvider.expiresInSeconds(), expiration.toSeconds());
    }

    private void save(UUID userId, String token) {
        jdbc.update("""
                INSERT INTO user_refresh_tokens (id, user_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID().toString(), userId.toString(), hash(token),
                LocalDateTime.now().plus(expiration));
    }

    private boolean isActiveUser(UUID userId) {
        Boolean active = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE id = ? AND deleted_at IS NULL)",
                Boolean.class, userId.toString());
        return Boolean.TRUE.equals(active);
    }

    private String newToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않거나 만료되었습니다.");
    }

    private record TokenRow(UUID id, UUID userId, LocalDateTime expiresAt, LocalDateTime revokedAt) {
    }
}
