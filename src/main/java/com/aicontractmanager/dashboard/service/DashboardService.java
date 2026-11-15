package com.aicontractmanager.dashboard.service;

import com.aicontractmanager.dashboard.dto.CategoryExpense;
import com.aicontractmanager.dashboard.dto.DashboardResponse;
import com.aicontractmanager.dashboard.repository.DashboardRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {
    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    public DashboardResponse get(UUID userId, YearMonth month) {
        BigDecimal total = repository.monthlyExpense(userId);
        List<CategoryExpense> categories = repository.categoryExpenses(userId).stream()
                .map(entry -> new CategoryExpense(entry.getKey(), entry.getValue(), percentage(entry.getValue(), total)))
                .toList();
        LocalDate today = LocalDate.now();
        return new DashboardResponse(month, total, categories,
                repository.expiringContracts(userId, today, today.plusDays(3)),
                repository.calendarEvents(userId, month.atDay(1), month.atEndOfMonth()));
    }

    private double percentage(BigDecimal amount, BigDecimal total) {
        if (total.signum() == 0) return 0;
        return amount.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP).doubleValue();
    }
}
