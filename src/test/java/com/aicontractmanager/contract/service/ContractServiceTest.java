package com.aicontractmanager.contract.service;

import com.aicontractmanager.contract.domain.Contract;
import com.aicontractmanager.contract.dto.ContractRequest;
import com.aicontractmanager.contract.repository.ContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractServiceTest {

    private ContractRepository repository;
    private ContractService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ContractRepository.class);
        service = new ContractService(repository);
    }

    @Test
    void otherUsersContractIsNotVisible() {
        when(repository.findOwned(contractId, userId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.get(userId, contractId));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        ContractRequest request = request(new BigDecimal("10000"),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(userId, request));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void changingAmountRequiresNewVerification() {
        when(repository.findOwned(contractId, userId)).thenReturn(Optional.of(contract(new BigDecimal("10000"))));

        service.update(userId, contractId, request(new BigDecimal("20000"), null, null));

        verify(repository).update(eq(contractId), eq(userId), any(ContractRequest.class), eq(true));
    }

    @Test
    void verifiesOnlyMatchingAmount() {
        when(repository.findOwned(contractId, userId)).thenReturn(Optional.of(contract(new BigDecimal("10000"))));

        assertThrows(ResponseStatusException.class,
                () -> service.verifyAmount(userId, contractId, new BigDecimal("20000")));
        verify(repository, never()).verifyAmount(any(), any());

        service.verifyAmount(userId, contractId, new BigDecimal("10000.00"));
        verify(repository).verifyAmount(contractId, userId);
    }

    private ContractRequest request(BigDecimal amount, LocalDate start, LocalDate end) {
        return new ContractRequest("헬스장", "FITNESS", "상호", start, end,
                null, null, amount, null, null, null);
    }

    private Contract contract(BigDecimal amount) {
        return new Contract(contractId, userId, "헬스장", "FITNESS", "상호",
                "ACTIVE", "MANUAL", null, null, null, null, amount,
                "KRW", null, null, null, true);
    }
}
