package com.aicontractmanager.contract.repository;

import com.aicontractmanager.contract.domain.Contract;
import com.aicontractmanager.contract.dto.ContractRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContractRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Contract> rowMapper = this::map;

    public ContractRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Contract> findAll(UUID userId) {
        return jdbc.query("SELECT * FROM contracts WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at DESC, id DESC",
                rowMapper, userId.toString());
    }

    public Optional<Contract> findOwned(UUID id, UUID userId) {
        return jdbc.query("SELECT * FROM contracts WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                rowMapper, id.toString(), userId.toString()).stream().findFirst();
    }

    public void create(UUID id, UUID userId, ContractRequest request) {
        jdbc.update("""
                INSERT INTO contracts (id, user_id, title, category, provider_name, status, source_type,
                    contract_start_date, contract_end_date, auto_renewal_date, cancellation_deadline,
                    monthly_amount, penalty_amount, cancellation_terms, plain_summary)
                VALUES (?, ?, ?, ?, ?, 'PENDING_VERIFICATION', 'MANUAL', ?, ?, ?, ?, ?, ?, ?, ?)
                """, id.toString(), userId.toString(), request.title(), request.category(), request.providerName(),
                request.contractStartDate(), request.contractEndDate(), request.autoRenewalDate(),
                request.cancellationDeadline(), request.monthlyAmount(), request.penaltyAmount(),
                request.cancellationTerms(), request.plainSummary());
    }

    public void update(UUID id, UUID userId, ContractRequest request, boolean resetAmountVerification) {
        jdbc.update("""
                UPDATE contracts SET title = ?, category = ?, provider_name = ?, contract_start_date = ?,
                    contract_end_date = ?, auto_renewal_date = ?, cancellation_deadline = ?, monthly_amount = ?,
                    penalty_amount = ?, cancellation_terms = ?, plain_summary = ?,
                    amount_verified = CASE WHEN ? THEN FALSE ELSE amount_verified END,
                    status = CASE WHEN ? THEN 'PENDING_VERIFICATION' ELSE status END
                WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, request.title(), request.category(), request.providerName(), request.contractStartDate(),
                request.contractEndDate(), request.autoRenewalDate(), request.cancellationDeadline(),
                request.monthlyAmount(), request.penaltyAmount(), request.cancellationTerms(), request.plainSummary(),
                resetAmountVerification, resetAmountVerification, id.toString(), userId.toString());
    }

    public void verifyAmount(UUID id, UUID userId) {
        jdbc.update("""
                UPDATE contracts SET amount_verified = TRUE, status = 'ACTIVE'
                WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, id.toString(), userId.toString());
    }

    public void delete(UUID id, UUID userId) {
        jdbc.update("UPDATE contracts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                id.toString(), userId.toString());
    }

    private Contract map(ResultSet rs, int rowNum) throws SQLException {
        return new Contract(
                UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("user_id")),
                rs.getString("title"), rs.getString("category"), rs.getString("provider_name"),
                rs.getString("status"), rs.getString("source_type"),
                rs.getObject("contract_start_date", LocalDate.class),
                rs.getObject("contract_end_date", LocalDate.class),
                rs.getObject("auto_renewal_date", LocalDate.class),
                rs.getObject("cancellation_deadline", LocalDate.class),
                rs.getBigDecimal("monthly_amount"), rs.getString("currency"),
                rs.getBigDecimal("penalty_amount"), rs.getString("cancellation_terms"),
                rs.getString("plain_summary"), rs.getBoolean("amount_verified"));
    }
}
