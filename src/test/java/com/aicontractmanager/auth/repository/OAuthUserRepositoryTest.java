package com.aicontractmanager.auth.repository;

import com.aicontractmanager.auth.dto.OAuthProvider;
import com.aicontractmanager.auth.service.OAuthProfile;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthUserRepositoryTest {

    @Test
    void doesNotLinkDifferentProviderByEmail() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID existingId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(), any()))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn(existingId.toString());
                    when(rs.getString("email")).thenReturn("same@example.com");
                    when(rs.getString("name")).thenReturn("Existing");
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return mapper.mapRow(rs, 0);
                });

        OAuthUserRepository repository = new OAuthUserRepository(jdbc);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> repository.upsert(OAuthProvider.NAVER,
                        new OAuthProfile("naver-123", "same@example.com", "Other")));

        assertEquals(409, exception.getStatusCode().value());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }
}
