package com.aicontractmanager.dashboard.dto;

import java.math.BigDecimal;

public record CategoryExpense(String category, BigDecimal amount, double percentage) {
}
