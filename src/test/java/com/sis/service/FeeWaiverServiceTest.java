package com.sis.service;

import com.sis.dto.CreateFeeWaiverRequest;
import com.sis.entity.Fee;
import com.sis.entity.FeeWaiver;
import com.sis.entity.PaymentAllocation;
import com.sis.entity.PaymentAttemptStatus;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.FeeWaiverRepository;
import com.sis.repository.PaymentAllocationRepository;
import com.sis.repository.PaymentAttemptRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeeWaiverServiceTest {

    @Test
    void rejectsWaiverThatWouldOverlapAllocatedAndPendingPayments() {
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);

        Fee fee = new Fee();
        fee.setId(71L);
        fee.setAmount(new BigDecimal("100.00"));
        when(fees.findByIdForUpdate(71L)).thenReturn(Optional.of(fee));

        FeeWaiver existingWaiver = new FeeWaiver();
        existingWaiver.setAmount(new BigDecimal("10.00"));
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(71L))
                .thenReturn(List.of(existingWaiver));

        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setAmount(new BigDecimal("40.00"));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(71L)).thenReturn(List.of(allocation));
        when(attempts.sumAmountByFeeIdAndStatus(71L, PaymentAttemptStatus.PENDING))
                .thenReturn(new BigDecimal("20.00"));

        FeeWaiverService service = new FeeWaiverService(
                waivers, fees, allocations, attempts, auditLogs, users);
        CreateFeeWaiverRequest request = new CreateFeeWaiverRequest();
        request.setFeeId(71L);
        request.setAmount(new BigDecimal("35.00"));
        request.setReason("Additional support");

        assertThatThrownBy(() -> service.create(request, "finance-reviewer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Waiver amount exceeds the fee balance");
        verify(fees).findByIdForUpdate(71L);
        verify(waivers, never()).save(any(FeeWaiver.class));
    }
}
