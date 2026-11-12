package com.aicontractmanager.suggestion.repository;

import com.aicontractmanager.suggestion.domain.ContractSuggestion;
import com.aicontractmanager.common.ApiTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContractSuggestionRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<ContractSuggestion> mapper = this::map;

    public ContractSuggestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContractSuggestion> findPending(UUID userId) {
        return jdbc.query("""
                SELECT id, merchant_name, category, estimated_amount, estimated_cycle,
                       detection_count, status, created_at
                FROM contract_suggestions WHERE user_id = ? AND status = 'PENDING'
                ORDER BY created_at DESC
                """, mapper, userId.toString());
    }

    public Optional<ContractSuggestion> findOwned(UUID suggestionId, UUID userId) {
        return jdbc.query("""
                SELECT id, merchant_name, category, estimated_amount, estimated_cycle,
                       detection_count, status, created_at
                FROM contract_suggestions WHERE id = ? AND user_id = ?
                """, mapper, suggestionId.toString(), userId.toString()).stream().findFirst();
    }

    public int respond(UUID suggestionId, UUID userId, String status) {
        return jdbc.update("""
                UPDATE contract_suggestions SET status = ?, responded_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ? AND status = 'PENDING'
                """, status, suggestionId.toString(), userId.toString());
    }

    public int linkContract(UUID suggestionId, UUID userId, UUID contractId) {
        return jdbc.update("""
                UPDATE contract_suggestions SET contract_id = ?
                WHERE id = ? AND user_id = ? AND status = 'ACCEPTED' AND contract_id IS NULL
                """, contractId.toString(), suggestionId.toString(), userId.toString());
    }

    private ContractSuggestion map(ResultSet rs, int rowNum) throws SQLException {
        return new ContractSuggestion(UUID.fromString(rs.getString("id")), rs.getString("merchant_name"),
                rs.getString("category"), rs.getBigDecimal("estimated_amount"), rs.getString("estimated_cycle"),
                rs.getInt("detection_count"), rs.getString("status"),
                ApiTime.fromDatabase(rs.getObject("created_at", LocalDateTime.class)));
    }
}
