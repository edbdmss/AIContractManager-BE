package com.aicontractmanager.schedule.repository;

import com.aicontractmanager.schedule.domain.ContractSchedule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import com.aicontractmanager.common.ApiTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContractScheduleRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<ContractSchedule> mapper = this::map;

    public ContractScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContractSchedule> findByContract(UUID contractId, UUID userId) {
        return jdbc.query("""
                SELECT s.* FROM contract_schedules s
                JOIN contracts c ON c.id = s.contract_id
                WHERE s.contract_id = ? AND c.user_id = ? AND c.deleted_at IS NULL
                ORDER BY s.due_date, s.created_at
                """, mapper, contractId.toString(), userId.toString());
    }

    public List<ContractSchedule> findUpcoming(UUID userId) {
        return jdbc.query("""
                SELECT s.* FROM contract_schedules s
                JOIN contracts c ON c.id = s.contract_id
                WHERE c.user_id = ? AND c.deleted_at IS NULL
                  AND s.status = 'PENDING' AND s.due_date >= CURRENT_DATE
                ORDER BY s.due_date, s.created_at
                """, mapper, userId.toString());
    }

    public Optional<ContractSchedule> findOwned(UUID scheduleId, UUID userId) {
        return jdbc.query("""
                SELECT s.* FROM contract_schedules s
                JOIN contracts c ON c.id = s.contract_id
                WHERE s.id = ? AND c.user_id = ? AND c.deleted_at IS NULL
                """, mapper, scheduleId.toString(), userId.toString()).stream().findFirst();
    }

    public void create(UUID id, UUID contractId, ContractScheduleRequestData request) {
        jdbc.update("""
                INSERT INTO contract_schedules
                    (id, contract_id, schedule_type, due_date, action_title, action_description, reminder_days_before)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id.toString(), contractId.toString(), request.type(), request.dueDate(), request.title(),
                request.description(), request.reminderDaysBefore());
    }

    public int markStatus(UUID scheduleId, UUID userId, String status) {
        return jdbc.update("""
                UPDATE contract_schedules s JOIN contracts c ON c.id = s.contract_id
                SET s.status = ?, s.completed_at = CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END
                WHERE s.id = ? AND c.user_id = ? AND c.deleted_at IS NULL AND s.status = 'PENDING'
                """, status, status, scheduleId.toString(), userId.toString());
    }

    private ContractSchedule map(ResultSet rs, int rowNum) throws SQLException {
        return new ContractSchedule(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("contract_id")),
                rs.getString("schedule_type"), rs.getObject("due_date", java.time.LocalDate.class),
                rs.getString("action_title"), rs.getString("action_description"), rs.getInt("reminder_days_before"),
                rs.getString("status"), ApiTime.fromDatabase(rs.getObject("completed_at", LocalDateTime.class)),
                ApiTime.fromDatabase(rs.getObject("created_at", LocalDateTime.class)));
    }

    public record ContractScheduleRequestData(String type, java.time.LocalDate dueDate, String title,
                                              String description, int reminderDaysBefore) {
    }
}
