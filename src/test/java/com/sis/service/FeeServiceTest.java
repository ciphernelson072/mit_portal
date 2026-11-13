package com.sis.service;

import com.sis.dto.FeeDto;
import com.sis.entity.Fee;
import com.sis.entity.FeeWaiver;
import com.sis.entity.PaymentAllocation;
import com.sis.entity.PaymentRefund;
import com.sis.entity.PaymentTransaction;
import com.sis.entity.RefundStatus;
import com.sis.entity.Student;
import com.sis.repository.FeeRepository;
import com.sis.repository.FeeWaiverRepository;
import com.sis.repository.PaymentAllocationRepository;
import com.sis.repository.PaymentRefundRepository;
import com.sis.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeeServiceTest {

    @Test
    void studentFeeAmountsExcludeRefundedAndWaivedAmountsFromCashPaid() {
        FeeRepository fees = mock(FeeRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        FeeService service = new FeeService(fees, transactions, allocations, refunds, waivers);

        Student student = new Student();
        student.setId(12L);
        Fee fee = new Fee(student, "Tuition", new BigDecimal("100.00"), null);
        fee.setId(34L);
        when(fees.findByStudentId(12L)).thenReturn(List.of(fee));

        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setAmount(new BigDecimal("30.00"));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(34L)).thenReturn(List.of(allocation));
        when(transactions.findByFeeIdAndPaymentAttemptIdIsNullAndStatus(34L, "COMPLETED"))
                .thenReturn(List.of(new PaymentTransaction(34L, 12L, new BigDecimal("20.00"))));
        PaymentRefund refund = new PaymentRefund();
        refund.setAmount(new BigDecimal("5.00"));
        refund.setStatus(RefundStatus.COMPLETED);
        when(refunds.findByFeeIdAndStatus(34L, RefundStatus.COMPLETED)).thenReturn(List.of(refund));
        FeeWaiver waiver = new FeeWaiver();
        waiver.setAmount(new BigDecimal("10.00"));
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(34L)).thenReturn(List.of(waiver));

        FeeDto result = service.getFeesForStudent(12L).get(0);

        assertEquals(new BigDecimal("45.00"), result.getAmountPaid());
        assertEquals(new BigDecimal("45.00"), result.getBalance());
    }
}
