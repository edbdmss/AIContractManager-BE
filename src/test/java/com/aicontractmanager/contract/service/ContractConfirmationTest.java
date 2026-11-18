package com.aicontractmanager.contract.service;

import com.aicontractmanager.contract.domain.Contract;
import com.aicontractmanager.contract.dto.ContractRequest;
import com.aicontractmanager.contract.repository.ContractRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractConfirmationTest {
    private final UUID user = UUID.randomUUID(), id = UUID.randomUUID();
    private final ContractRepository repository = mock(ContractRepository.class);
    private final ContractService service = new ContractService(repository);
    private Contract existing() {
        return new Contract(id,user,"구독","SUBSCRIPTION",null,"ACTIVE","MANUAL",
                null,null,LocalDate.now().plusMonths(1),null,new BigDecimal("100"),"KRW",
                null,null,null,true,3);
    }
    private ContractRequest request(String amount, Integer cycle, Boolean verified) {
        return new ContractRequest("구독","SUBSCRIPTION",null,null,null,LocalDate.now().plusMonths(1),null,
                new BigDecimal(amount),null,null,null,cycle,verified);
    }
    private ContractRequest update(String amount, Integer cycle, Boolean verified) {
        when(repository.findOwned(id,user)).thenReturn(Optional.of(existing()));
        service.update(user,id,request(amount,cycle,verified));
        var captured = ArgumentCaptor.forClass(ContractRequest.class);
        verify(repository).update(eq(id),eq(user),captured.capture(),anyBoolean());
        return captured.getValue();
    }
    @Test void sameAmountPreservesConfirmationAndCycle() {
        var actual = update("100",null,null);
        assertEquals(3, actual.renewalCycleMonths());
        assertTrue(actual.amountVerified());
    }
    @Test void changedAmountRequiresConfirmationAgain() {
        assertFalse(update("200",null,null).amountVerified());
    }
    @Test void explicitCheckConfirmsNewAmountAndCycle() {
        var actual = update("200",12,true);
        assertTrue(actual.amountVerified());
        assertEquals(12,actual.renewalCycleMonths());
    }
    @Test void uncheckingClearsConfirmation() {
        assertFalse(update("100",null,false).amountVerified());
    }
    @Test void newSubscriptionDoesNotDefaultCycleOrAutoConfirm() {
        when(repository.findOwned(any(),eq(user))).thenReturn(Optional.of(existing()));
        service.create(user,request("100",null,null));
        var captured = ArgumentCaptor.forClass(ContractRequest.class);
        verify(repository).create(any(),eq(user),captured.capture());
        assertNull(captured.getValue().renewalCycleMonths());
        assertFalse(captured.getValue().amountVerified());
    }
    @Test void rejectsOutOfRangeCycles() {
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            for(int months:new int[]{0,-1,61,120}) assertFalse(validator.validate(request("100",months,true)).isEmpty());
            for(int months:new int[]{1,2,3,12,24,60}) assertTrue(validator.validate(request("100",months,true)).isEmpty());
        }
    }
}
