package com.aicontractmanager.document.repository;

import com.aicontractmanager.document.domain.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Document> rowMapper = this::map;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Document> findAll(UUID contractId) {
        return jdbc.query("SELECT * FROM documents WHERE contract_id = ? AND deleted_at IS NULL ORDER BY uploaded_at DESC, id DESC",
                rowMapper, contractId.toString());
    }

    public Optional<Document> find(UUID contractId, UUID id) {
        return jdbc.query("SELECT * FROM documents WHERE contract_id = ? AND id = ? AND deleted_at IS NULL",
                rowMapper, contractId.toString(), id.toString()).stream().findFirst();
    }

    public void create(UUID id, UUID contractId, String filename, String storageKey,
                       String contentType, long size, String checksum, boolean primary) {
        jdbc.update("""
                INSERT INTO documents (id, contract_id, document_type, original_filename, storage_key,
                    content_type, file_size, checksum, is_primary)
                VALUES (?, ?, 'CONTRACT', ?, ?, ?, ?, ?, ?)
                """, id.toString(), contractId.toString(), filename, storageKey,
                contentType, size, checksum, primary);
    }

    public void delete(UUID contractId, UUID id) {
        jdbc.update("UPDATE documents SET deleted_at = CURRENT_TIMESTAMP WHERE contract_id = ? AND id = ? AND deleted_at IS NULL",
                contractId.toString(), id.toString());
    }

    private Document map(ResultSet rs, int rowNum) throws SQLException {
        return new Document(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("contract_id")),
                rs.getString("document_type"), rs.getString("original_filename"), rs.getString("storage_key"),
                rs.getString("content_type"), rs.getLong("file_size"), rs.getString("checksum"),
                rs.getBoolean("is_primary"), rs.getTimestamp("uploaded_at").toLocalDateTime());
    }
}
