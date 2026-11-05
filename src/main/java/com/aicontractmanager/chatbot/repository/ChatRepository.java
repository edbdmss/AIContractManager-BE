package com.aicontractmanager.chatbot.repository;

import com.aicontractmanager.chatbot.domain.ChatMessage;
import com.aicontractmanager.chatbot.domain.ChatSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ChatRepository {
    private final JdbcTemplate jdbc;

    public ChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void createSession(UUID id, UUID userId, UUID contractId) {
        jdbc.update("INSERT INTO chat_sessions (id, user_id, contract_id) VALUES (?, ?, ?)",
                id.toString(), userId.toString(), contractId == null ? null : contractId.toString());
    }

    public Optional<ChatSession> findSession(UUID sessionId, UUID userId) {
        return jdbc.query("""
                SELECT id, contract_id, created_at, closed_at FROM chat_sessions
                WHERE id = ? AND user_id = ? AND closed_at IS NULL
                """, (rs, row) -> mapSession(rs), sessionId.toString(), userId.toString()).stream().findFirst();
    }

    public List<ChatSession> findSessions(UUID userId) {
        return jdbc.query("""
                SELECT id, contract_id, created_at, closed_at FROM chat_sessions
                WHERE user_id = ? ORDER BY created_at DESC
                """, (rs, row) -> mapSession(rs), userId.toString());
    }

    public void saveMessage(UUID id, UUID sessionId, String role, String content) {
        jdbc.update("INSERT INTO chat_messages (id, session_id, role, content) VALUES (?, ?, ?, ?)",
                id.toString(), sessionId.toString(), role, content);
    }

    public List<ChatMessage> findMessages(UUID sessionId) {
        return jdbc.query("""
                SELECT id, role, content, created_at FROM chat_messages
                WHERE session_id = ? ORDER BY created_at, id
                """, (rs, row) -> mapMessage(rs), sessionId.toString());
    }

    public Optional<ChatMessage> findMessage(UUID messageId, UUID sessionId) {
        return jdbc.query("""
                SELECT id, role, content, created_at FROM chat_messages
                WHERE id = ? AND session_id = ?
                """, (rs, row) -> mapMessage(rs), messageId.toString(), sessionId.toString()).stream().findFirst();
    }

    public List<ChatMessage> findRecentMessages(UUID sessionId, int limit) {
        List<ChatMessage> newestFirst = jdbc.query("""
                SELECT id, role, content, created_at FROM chat_messages
                WHERE session_id = ? ORDER BY created_at DESC, id DESC LIMIT ?
                """, (rs, row) -> mapMessage(rs), sessionId.toString(), limit);
        List<ChatMessage> chronological = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(chronological);
        return chronological;
    }

    private ChatSession mapSession(ResultSet rs) throws SQLException {
        String contractId = rs.getString("contract_id");
        return new ChatSession(UUID.fromString(rs.getString("id")),
                contractId == null ? null : UUID.fromString(contractId),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("closed_at", LocalDateTime.class));
    }

    private ChatMessage mapMessage(ResultSet rs) throws SQLException {
        return new ChatMessage(UUID.fromString(rs.getString("id")), rs.getString("role"),
                rs.getString("content"), rs.getObject("created_at", LocalDateTime.class));
    }
}
