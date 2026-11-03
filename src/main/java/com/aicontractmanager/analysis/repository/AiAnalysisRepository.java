package com.aicontractmanager.analysis.repository;

import com.aicontractmanager.analysis.dto.AiAnalysisResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Repository
public class AiAnalysisRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AiAnalysisRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void create(UUID id, UUID documentId, String version) {
        jdbc.update("""
                INSERT INTO ai_analyses (id, document_id, analysis_version, status)
                VALUES (?, ?, ?, 'PROCESSING')
                """, id.toString(), documentId.toString(), version);
    }

    public void complete(UUID id, JsonNode result, Double confidence) {
        jdbc.update("""
                UPDATE ai_analyses
                SET status = 'COMPLETED', extracted_data = ?, confidence = ?, completed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, result.toString(), confidence, id.toString());
    }

    public void fail(UUID id, String message) {
        jdbc.update("""
                UPDATE ai_analyses
                SET status = 'FAILED', error_message = ?, completed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, message, id.toString());
    }

    public AiAnalysisResponse find(UUID id) {
        return jdbc.queryForObject("""
                SELECT id, document_id, status, extracted_data, completed_at
                FROM ai_analyses WHERE id = ?
                """, this::map, id.toString());
    }

    private AiAnalysisResponse map(ResultSet resultSet, int rowNum) throws SQLException {
        JsonNode result = null;
        String json = resultSet.getString("extracted_data");
        if (json != null) {
            try {
                result = objectMapper.readTree(json);
            } catch (Exception ignored) {
                result = objectMapper.createObjectNode();
            }
        }
        return new AiAnalysisResponse(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("document_id")),
                resultSet.getString("status"),
                result,
                resultSet.getTimestamp("completed_at") == null
                        ? null
                        : resultSet.getTimestamp("completed_at").toLocalDateTime()
        );
    }
}
