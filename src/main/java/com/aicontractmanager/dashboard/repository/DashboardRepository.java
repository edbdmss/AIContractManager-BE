package com.aicontractmanager.dashboard.repository;

import com.aicontractmanager.dashboard.dto.CalendarEvent;
import com.aicontractmanager.dashboard.dto.ExpiringContract;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DashboardRepository {
    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal monthlyExpense(UUID userId) {
        BigDecimal amount = jdbc.queryForObject("""
                SELECT COALESCE(SUM(monthly_amount), 0)
                FROM contracts
                WHERE user_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """, BigDecimal.class, userId.toString());
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public List<Map.Entry<String, BigDecimal>> categoryExpenses(UUID userId) {
        return jdbc.query("""
                SELECT category, SUM(monthly_amount) AS amount
                FROM contracts
                WHERE user_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                  AND monthly_amount IS NOT NULL
                GROUP BY category
                ORDER BY amount DESC, category
                """, (rs, row) -> Map.entry(rs.getString("category"), rs.getBigDecimal("amount")),
                userId.toString());
    }

    public List<ExpiringContract> expiringContracts(UUID userId, LocalDate from, LocalDate through) {
        return jdbc.query("""
                SELECT id, title, provider_name, contract_end_date
                FROM contracts
                WHERE user_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                  AND contract_end_date BETWEEN ? AND ?
                ORDER BY contract_end_date, title
                """, (rs, row) -> mapExpiring(rs, from), userId.toString(), from, through);
    }

    public List<CalendarEvent> calendarEvents(UUID userId, LocalDate from, LocalDate through) {
        return jdbc.query("""
                SELECT event_date, event_type, contract_id, contract_title, title, description
                FROM (
                    SELECT s.due_date AS event_date, s.schedule_type AS event_type, c.id AS contract_id,
                           c.title AS contract_title, s.action_title AS title, s.action_description AS description
                    FROM contract_schedules s JOIN contracts c ON c.id = s.contract_id
                    WHERE c.user_id = ? AND c.deleted_at IS NULL AND s.status = 'PENDING'
                      AND s.due_date BETWEEN ? AND ?
                    UNION ALL
                    SELECT c.contract_end_date, 'EXPIRY', c.id, c.title, '계약 만료', NULL
                    FROM contracts c
                    WHERE c.user_id = ? AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
                      AND c.contract_end_date BETWEEN ? AND ?
                      AND NOT EXISTS (SELECT 1 FROM contract_schedules managed_schedule
                          WHERE managed_schedule.contract_id=c.id AND managed_schedule.managed=TRUE
                            AND managed_schedule.schedule_type='EXPIRY'
                            AND managed_schedule.due_date=c.contract_end_date)
                    UNION ALL
                    SELECT c.auto_renewal_date,
                           CASE WHEN c.category = 'SUBSCRIPTION' THEN 'PAYMENT' ELSE 'RENEWAL' END,
                           c.id, c.title,
                           CASE WHEN c.category = 'SUBSCRIPTION' THEN '다음 결제 예정' ELSE '자동 갱신 예정' END,
                           NULL
                    FROM contracts c
                    WHERE c.user_id = ? AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
                      AND c.auto_renewal_date BETWEEN ? AND ?
                      AND NOT EXISTS (SELECT 1 FROM contract_schedules managed_schedule
                          WHERE managed_schedule.contract_id=c.id AND managed_schedule.managed=TRUE
                            AND managed_schedule.schedule_type = CASE WHEN c.category = 'SUBSCRIPTION' THEN 'PAYMENT' ELSE 'RENEWAL' END
                            AND managed_schedule.due_date=c.auto_renewal_date)
                    UNION ALL
                    SELECT c.cancellation_deadline, 'CANCELLATION', c.id, c.title, '해지 신청 마감', NULL
                    FROM contracts c
                    WHERE c.user_id = ? AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
                      AND c.cancellation_deadline BETWEEN ? AND ?
                      AND NOT EXISTS (SELECT 1 FROM contract_schedules managed_schedule
                          WHERE managed_schedule.contract_id=c.id AND managed_schedule.managed=TRUE
                            AND managed_schedule.schedule_type='CANCELLATION'
                            AND managed_schedule.due_date=c.cancellation_deadline)
                ) events
                ORDER BY event_date, title
                """, (rs, row) -> new CalendarEvent(rs.getObject("event_date", LocalDate.class),
                rs.getString("event_type"), UUID.fromString(rs.getString("contract_id")),
                rs.getString("contract_title"), rs.getString("title"), rs.getString("description")),
                userId.toString(), from, through,
                userId.toString(), from, through,
                userId.toString(), from, through,
                userId.toString(), from, through);
    }

    private ExpiringContract mapExpiring(ResultSet rs, LocalDate from) throws SQLException {
        LocalDate endDate = rs.getObject("contract_end_date", LocalDate.class);
        return new ExpiringContract(UUID.fromString(rs.getString("id")), rs.getString("title"),
                rs.getString("provider_name"), endDate, java.time.temporal.ChronoUnit.DAYS.between(from, endDate));
    }
}
