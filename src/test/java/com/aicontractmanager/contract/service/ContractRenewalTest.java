package com.aicontractmanager.contract.service;

import com.aicontractmanager.contract.repository.ContractRepository;
import com.aicontractmanager.contract.repository.ContractRepository.DueSubscriptionPayment;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractRenewalTest {
    private final ContractRepository repository = mock(ContractRepository.class);
    private final ContractService service = new ContractService(repository);

    private void rollover(LocalDate dueDate, int day, int months, LocalDate today, LocalDate expected) {
        var due = new DueSubscriptionPayment(UUID.randomUUID(), UUID.randomUUID(), dueDate, day, months, null);
        when(repository.findDueSubscriptionPayments(today)).thenReturn(List.of(due));
        when(repository.advanceSubscriptionPayment(due, expected)).thenReturn(1);
        assertEquals(1, service.rollForwardDueSubscriptionPayments(today));
        verify(repository).advanceSubscriptionPayment(due, expected);
        verify(repository).refreshSchedulesAfterPaymentRollover(due.id(), due.userId());
    }

    @Test void fiveYearCycleUsesSixtyMonths() {
        rollover(LocalDate.of(2026, 11, 18), 18, 60, LocalDate.of(2026, 11, 19), LocalDate.of(2031, 11, 18));
    }
    @Test void twoMonthCycleUsesUserSelection() {
        rollover(LocalDate.of(2026, 11, 18), 18, 2, LocalDate.of(2026, 11, 19), LocalDate.of(2027, 1, 18));
    }
    @Test void quarterlyClampsToMonthEnd() {
        rollover(LocalDate.of(2026, 1, 31), 31, 3, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30));
    }
    @Test void monthlyRestoresOriginalDayAfterFebruary() {
        rollover(LocalDate.of(2026, 2, 28), 31, 1, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
    }
    @Test void annualRestoresLeapDay() {
        rollover(LocalDate.of(2027, 2, 28), 29, 12, LocalDate.of(2027, 3, 1), LocalDate.of(2028, 2, 29));
    }
    @Test void catchupKeepsAnOccurrenceDueToday() {
        rollover(LocalDate.of(2026, 1, 15), 15, 3, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15));
    }
    @Test void doesNotExtendPastContractEnd() {
        var today = LocalDate.of(2026, 2, 1);
        var due = new DueSubscriptionPayment(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 1, 31), 31, 3, LocalDate.of(2026, 3, 1));
        when(repository.findDueSubscriptionPayments(today)).thenReturn(List.of(due));
        assertEquals(0, service.rollForwardDueSubscriptionPayments(today));
        verify(repository, never()).advanceSubscriptionPayment(any(), any());
    }
    @Test void attentionUsesInclusiveSevenDayWindowInKorea() {
        var id = UUID.randomUUID();
        var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        service.attention(id);
        verify(repository).findAttention(id, today, today.plusDays(7));
    }
}
