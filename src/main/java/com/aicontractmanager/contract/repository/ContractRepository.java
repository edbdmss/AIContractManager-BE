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
        syncSchedules(id, userId);
    }

    public void update(UUID id, UUID userId, ContractRequest request, boolean resetAmountVerification) {
        jdbc.update("""
                UPDATE contracts SET title = ?, category = ?, provider_name = ?, contract_start_date = ?,
                    contract_end_date = ?, auto_renewal_date = ?, cancellation_deadline = ?, monthly_amount = ?,
                    penalty_amount = ?, cancellation_terms = ?, plain_summary = ?,
                    amount_verified = CASE WHEN ? THEN FALSE ELSE amount_verified END,
                    status = CASE WHEN status IN ('CANCELLED','EXPIRED') THEN status WHEN ? THEN 'PENDING_VERIFICATION' ELSE status END
                WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, request.title(), request.category(), request.providerName(), request.contractStartDate(),
                request.contractEndDate(), request.autoRenewalDate(), request.cancellationDeadline(),
                request.monthlyAmount(), request.penaltyAmount(), request.cancellationTerms(), request.plainSummary(),
                resetAmountVerification, resetAmountVerification, id.toString(), userId.toString());
        syncSchedules(id, userId);
    }

    public void verifyAmount(UUID id, UUID userId) {
        jdbc.update("""
                UPDATE contracts SET amount_verified = TRUE, status = CASE WHEN status IN ('CANCELLED','EXPIRED') THEN status ELSE 'ACTIVE' END
                WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, id.toString(), userId.toString());
    }

    public void delete(UUID id, UUID userId) {
        jdbc.update("UPDATE contracts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                id.toString(), userId.toString());
        cancelReminders(id);
    }

    public void cancel(UUID id, UUID userId) {
        jdbc.update("UPDATE contracts SET status='CANCELLED' WHERE id=? AND user_id=? AND deleted_at IS NULL",
                id.toString(), userId.toString());
        cancelReminders(id);
    }

    private void cancelReminders(UUID id) {
        jdbc.update("UPDATE contract_schedules SET status='DISMISSED' WHERE contract_id=? AND status='PENDING'", id.toString());
        jdbc.update("UPDATE notifications SET dispatch_status='CANCELLED', dispatch_claimed_at=NULL WHERE contract_id=? AND dispatch_status IN ('PENDING','SENDING')", id.toString());
    }

    private void syncSchedules(UUID id, UUID userId) {
        Contract contract = findOwned(id, userId).orElseThrow();
        if (java.util.Set.of("CANCELLED", "EXPIRED").contains(contract.status())) return;
        syncSchedule(id, userId, "EXPIRY", contract.contractEndDate(), "계약 만료 예정");
        syncSchedule(id, userId, "RENEWAL", contract.autoRenewalDate(), "계약 자동 갱신 예정");
        syncSchedule(id, userId, "CANCELLATION", contract.cancellationDeadline(), "계약 해지 신청 마감");
    }

    private void syncSchedule(UUID contractId, UUID userId, String type, LocalDate date, String title) {
        var existing = jdbc.queryForList("SELECT id, due_date, status FROM contract_schedules WHERE contract_id=? AND schedule_type=? AND managed=TRUE ORDER BY created_at DESC",
                contractId.toString(), type);
        // Keep an unchanged date, including a reminder the user already completed or dismissed.
        if (!existing.isEmpty() && date != null && date.equals(((java.sql.Date)existing.get(0).get("due_date")).toLocalDate())) return;
        for (var row : existing) {
            String scheduleId = row.get("id").toString();
            jdbc.update("UPDATE contract_schedules SET managed=FALSE, status=CASE WHEN status='PENDING' THEN 'DISMISSED' ELSE status END WHERE id=?", scheduleId);
            jdbc.update("UPDATE notifications SET dispatch_status='CANCELLED', dispatch_claimed_at=NULL WHERE schedule_id=? AND dispatch_status IN ('PENDING','SENDING')", scheduleId);
        }
        if (date == null || date.isBefore(LocalDate.now())) return;
        String scheduleId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO contract_schedules(id,contract_id,schedule_type,due_date,action_title,reminder_days_before,managed) VALUES(?,?,?,?,?,3,TRUE)",
                scheduleId, contractId.toString(), type, date, title);
        jdbc.update("INSERT INTO notifications(id,user_id,contract_id,schedule_id,notification_type,title,body,scheduled_at) VALUES(?,?,?,?,'SCHEDULE_REMINDER',?,?,?)",
                UUID.randomUUID().toString(),userId.toString(),contractId.toString(),scheduleId,title,
                date + "까지 계약 내용을 확인해 주세요.",date.minusDays(3).atTime(9,0));
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
