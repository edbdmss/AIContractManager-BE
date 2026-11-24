package com.aicontractmanager.chatbot.repository;

import com.aicontractmanager.chatbot.domain.ChatMessage;
import com.aicontractmanager.chatbot.domain.ChatSession;
import com.aicontractmanager.common.ApiTime;
import com.aicontractmanager.chatbot.dto.ChatMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final ObjectMapper mapper;

    public ChatRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
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

    public void saveMessage(UUID id, UUID sessionId, String role, String content, ChatMetadata metadata) {
        try {
            jdbc.update("INSERT INTO chat_messages (id, session_id, role, content, response_metadata) VALUES (?, ?, ?, ?, ?)",
                    id.toString(), sessionId.toString(), role, content, mapper.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("챗봇 응답 메타데이터 저장에 실패했습니다.", e);
        }
    }

    public JsonNode findRiskReports(UUID contractId) {
        return jdbc.query("""
                SELECT a.extracted_data FROM ai_analyses a
                JOIN documents d ON d.id = a.document_id
                WHERE d.contract_id = ? AND d.deleted_at IS NULL AND a.status = 'COMPLETED'
                ORDER BY a.completed_at DESC, a.id DESC LIMIT 1
                """, (rs, row) -> {
            try {
                String data = rs.getString(1);
                JsonNode reports = data == null ? null : mapper.readTree(data).get("riskReports");
                return reports != null && reports.isArray() ? reports : mapper.createArrayNode();
            } catch (JsonProcessingException e) {
                throw new SQLException("AI 분석 결과를 읽지 못했습니다.", e);
            }
        }, contractId.toString()).stream().findFirst().orElseGet(mapper::createArrayNode);
    }

    public List<ChatMessage> findMessages(UUID sessionId) {
        return jdbc.query("""
                SELECT id, role, content, created_at, response_metadata FROM chat_messages
                WHERE session_id = ? ORDER BY created_at, id
                """, (rs, row) -> mapMessage(rs), sessionId.toString());
    }

    public Optional<ChatMessage> findMessage(UUID messageId, UUID sessionId) {
        return jdbc.query("""
                SELECT id, role, content, created_at, response_metadata FROM chat_messages
                WHERE id = ? AND session_id = ?
                """, (rs, row) -> mapMessage(rs), messageId.toString(), sessionId.toString()).stream().findFirst();
    }

    public List<ChatMessage> findRecentMessages(UUID sessionId, int limit) {
        List<ChatMessage> newestFirst = jdbc.query("""
                SELECT id, role, content, created_at, response_metadata FROM chat_messages
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
                ApiTime.fromDatabase(rs.getObject("created_at", LocalDateTime.class)), ApiTime.fromDatabase(rs.getObject("closed_at", LocalDateTime.class)));
    }

    private ChatMessage mapMessage(ResultSet rs) throws SQLException {
        ChatMetadata metadata = ChatMetadata.empty();
        String json = rs.getString("response_metadata");
        if (json != null) {
            try {
                metadata = mapper.readValue(json, ChatMetadata.class);
            } catch (JsonProcessingException e) {
                throw new SQLException("챗봇 응답 메타데이터를 읽지 못했습니다.", e);
            }
        }
        return new ChatMessage(UUID.fromString(rs.getString("id")), rs.getString("role"),
                rs.getString("content"), ApiTime.fromDatabase(rs.getObject("created_at", LocalDateTime.class)), metadata);
    }
}
