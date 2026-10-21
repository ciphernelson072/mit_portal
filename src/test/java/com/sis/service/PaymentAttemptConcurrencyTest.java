package com.sis.service;

import com.sis.dto.CreatePaymentAttemptRequest;
import com.sis.dto.PaymentDestinationView;
import com.sis.entity.Fee;
import com.sis.entity.PaymentAttemptStatus;
import com.sis.entity.PaymentChannel;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.PaymentAttemptRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
@Import(PaymentAttemptService.class)
class PaymentAttemptConcurrencyTest {

    @Autowired
    private PaymentAttemptService paymentAttempts;

    @Autowired
    private StudentRepository students;

    @Autowired
    private FeeRepository fees;

    @Autowired
    private PaymentAttemptRepository attempts;

    @MockBean
    private AuditLogRepository auditLogs;

    @MockBean
    private UserRepository users;

    @MockBean
    private PaymentDestinationService destinations;

    @MockBean
    private NotificationService notifications;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentPaymentAttemptsCannotReserveMoreThanFeeAmount() throws Exception {
        Student student = students.saveAndFlush(new Student(
                new User("payment-race-student", "encoded", Role.STUDENT), "Payment Race Student", "Year 1"));
        Fee fee = fees.saveAndFlush(new Fee(student, "Tuition", new BigDecimal("100.00"), null));
        when(destinations.list()).thenReturn(List.of(new PaymentDestinationView(
                PaymentChannel.BANK_TRANSFER, "Bank transfer", true, "Account", "Transfer instructions")));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() ->
                    createWhenReleased(ready, start, fee.getId(), "PAY-RACE-A"));
            Future<Boolean> second = executor.submit(() ->
                    createWhenReleased(ready, start, fee.getId(), "PAY-RACE-B"));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successfulReservations = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);

            assertThat(successfulReservations).isEqualTo(1);
            assertThat(attempts.sumAmountByFeeIdAndStatus(fee.getId(), PaymentAttemptStatus.PENDING))
                    .isEqualByComparingTo("60.00");
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean createWhenReleased(CountDownLatch ready, CountDownLatch start,
                                      Long feeId, String providerReference) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Payment reservation concurrency test did not start");
        }
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest();
        request.setFeeId(feeId);
        request.setAmount(new BigDecimal("60.00"));
        request.setPaymentChannel(PaymentChannel.BANK_TRANSFER);
        request.setProviderReference(providerReference);
        try {
            paymentAttempts.create(request, students.findByUserUsername("payment-race-student").orElseThrow().getId());
            return true;
        } catch (IllegalStateException expectedBalanceConflict) {
            return false;
        }
    }
}
