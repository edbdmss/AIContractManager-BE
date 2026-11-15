package com.aicontractmanager.dashboard.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record DashboardResponse(YearMonth month, BigDecimal monthlyExpense,
                                List<CategoryExpense> categoryExpenses,
                                List<ExpiringContract> expiringContracts,
                                List<CalendarEvent> calendarEvents) {
}
