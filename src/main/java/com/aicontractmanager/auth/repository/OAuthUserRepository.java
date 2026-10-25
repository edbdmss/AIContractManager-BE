package com.aicontractmanager.auth.repository;

import com.aicontractmanager.auth.domain.AuthUser;
import com.aicontractmanager.auth.dto.OAuthProvider;
import com.aicontractmanager.auth.service.OAuthProfile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Repository
public class OAuthUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public OAuthUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthUser upsert(OAuthProvider provider, OAuthProfile profile) {
        if (profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new IllegalStateException("OAuth provider user id가 없습니다.");
        }

        String email = normalizeEmail(profile.email(), provider, profile.providerUserId());
        String name = profile.name() == null || profile.name().isBlank()
                ? provider.name().toLowerCase()
                : profile.name();

        Optional<UserRow> byProvider = findByProvider(provider, profile.providerUserId());
        if (byProvider.isPresent()) {
            UserRow user = byProvider.get();
            jdbcTemplate.update(
                    "UPDATE users SET email = ?, name = ?, deleted_at = NULL WHERE id = ?",
                    email, name, user.id().toString()
            );
            return new AuthUser(user.id(), email, name, provider, false);
        }

        Optional<UserRow> byEmail = findByEmail(email);
        if (byEmail.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 로그인 방식에 사용 중인 이메일입니다.");
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, name, provider, provider_user_id) VALUES (?, ?, ?, ?, ?)",
                id.toString(), email, name, provider.name(), profile.providerUserId()
        );
        return new AuthUser(id, email, name, provider, true);
    }

    private Optional<UserRow> findByProvider(OAuthProvider provider, String providerUserId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, email, name FROM users WHERE provider = ? AND provider_user_id = ? LIMIT 1",
                    (rs, rowNum) -> new UserRow(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("email"),
                            rs.getString("name")
                    ),
                    provider.name(), providerUserId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private Optional<UserRow> findByEmail(String email) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, email, name FROM users WHERE email = ? AND deleted_at IS NULL LIMIT 1",
                    (rs, rowNum) -> new UserRow(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("email"),
                            rs.getString("name")
                    ),
                    email
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private String normalizeEmail(String email, OAuthProvider provider, String providerUserId) {
        if (email != null && !email.isBlank()) {
            return email;
        }
        return providerUserId + "@" + provider.name().toLowerCase() + ".local";
    }

    private record UserRow(UUID id, String email, String name) {
    }
}
