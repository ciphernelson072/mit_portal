package com.sis.service;

import com.sis.dto.CreateLibraryLoanRequest;
import com.sis.entity.LibraryItem;
import com.sis.entity.LibraryLoan;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.LibraryItemRepository;
import com.sis.repository.LibraryLoanRepository;
import com.sis.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
@Import(LibraryService.class)
class LibraryServiceConcurrencyTest {

    @Autowired
    private LibraryService library;

    @Autowired
    private StudentRepository students;

    @Autowired
    private LibraryItemRepository items;

    @Autowired
    private LibraryLoanRepository loans;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentLoanIssuesCannotExceedAvailableCopies() throws Exception {
        Student student = createStudent("library-issue-race");
        LibraryItem item = createItem("LIB-ISSUE-RACE", 1);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() ->
                    issueWhenReleased(ready, start, student.getId(), item.getId()));
            Future<Boolean> second = executor.submit(() ->
                    issueWhenReleased(ready, start, student.getId(), item.getId()));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successfulLoans = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);

            assertThat(successfulLoans).isEqualTo(1);
            assertThat(items.findById(item.getId()).orElseThrow().getAvailableCopies()).isZero();
            assertThat(loans.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentReturnsRestoreOnlyOneCopy() throws Exception {
        Student student = createStudent("library-return-race");
        LibraryItem item = createItem("LIB-RETURN-RACE", 0);
        LibraryLoan loan = loans.saveAndFlush(newLoan(student.getId(), item.getId()));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> returnWhenReleased(ready, start, loan.getId()));
            Future<Boolean> second = executor.submit(() -> returnWhenReleased(ready, start, loan.getId()));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successfulReturns = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);

            assertThat(successfulReturns).isEqualTo(1);
            assertThat(items.findById(item.getId()).orElseThrow().getAvailableCopies()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Student createStudent(String username) {
        return students.saveAndFlush(new Student(new User(username, "encoded", Role.STUDENT), username, "Year 1"));
    }

    private LibraryItem createItem(String isbn, int availableCopies) {
        LibraryItem item = new LibraryItem();
        item.setIsbn(isbn);
        item.setTitle(isbn);
        item.setAuthor("Test");
        item.setTotalCopies(1);
        item.setAvailableCopies(availableCopies);
        return items.saveAndFlush(item);
    }

    private static LibraryLoan newLoan(Long studentId, Long itemId) {
        LibraryLoan loan = new LibraryLoan();
        loan.setStudentId(studentId);
        loan.setLibraryItemId(itemId);
        loan.setDueDate(LocalDate.now().plusDays(14));
        return loan;
    }

    private boolean issueWhenReleased(CountDownLatch ready, CountDownLatch start,
                                      Long studentId, Long itemId) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Library issue concurrency test did not start");
        }
        try {
            library.issue(request(studentId, itemId));
            return true;
        } catch (IllegalStateException expectedNoCopies) {
            return false;
        }
    }

    private boolean returnWhenReleased(CountDownLatch ready, CountDownLatch start, Long loanId) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Library return concurrency test did not start");
        }
        try {
            library.returnLoan(loanId);
            return true;
        } catch (IllegalStateException expectedAlreadyReturned) {
            return false;
        }
    }

    private static CreateLibraryLoanRequest request(Long studentId, Long itemId) {
        CreateLibraryLoanRequest request = new CreateLibraryLoanRequest();
        request.setStudentId(studentId);
        request.setLibraryItemId(itemId);
        request.setDueDate(LocalDate.now().plusDays(14));
        return request;
    }
}
