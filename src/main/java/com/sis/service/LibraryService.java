package com.sis.service;

import com.sis.dto.CreateLibraryItemRequest;
import com.sis.dto.CreateLibraryLoanRequest;
import com.sis.entity.*;
import com.sis.repository.LibraryItemRepository;
import com.sis.repository.LibraryLoanRepository;
import com.sis.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class LibraryService {
    private final LibraryItemRepository items;
    private final LibraryLoanRepository loans;
    private final StudentRepository students;

    public LibraryService(LibraryItemRepository items, LibraryLoanRepository loans, StudentRepository students) {
        this.items = items;
        this.loans = loans;
        this.students = students;
    }

    public LibraryItem createItem(CreateLibraryItemRequest request) {
        LibraryItem item = new LibraryItem();
        item.setIsbn(request.getIsbn());
        item.setTitle(request.getTitle());
        item.setAuthor(request.getAuthor());
        item.setTotalCopies(request.getTotalCopies());
        item.setAvailableCopies(request.getTotalCopies());
        return items.save(item);
    }

    @Transactional(readOnly = true)
    public List<LibraryItem> catalog() { return items.findByActiveTrueOrderByTitleAsc(); }

    public LibraryLoan issue(CreateLibraryLoanRequest request) {
        students.findById(request.getStudentId()).orElseThrow(() -> new IllegalArgumentException("Student not found"));
        LibraryItem item = items.findActiveByIdForUpdate(request.getLibraryItemId())
                .orElseThrow(() -> new IllegalArgumentException("Library item not found"));
        if (item.getAvailableCopies() < 1) throw new IllegalStateException("No available copies");
        item.setAvailableCopies(item.getAvailableCopies() - 1);
        items.save(item);
        LibraryLoan loan = new LibraryLoan();
        loan.setStudentId(request.getStudentId());
        loan.setLibraryItemId(item.getId());
        loan.setDueDate(request.getDueDate());
        return loans.save(loan);
    }

    @Transactional(readOnly = true)
    public List<LibraryLoan> studentLoans(String username) {
        Long studentId = students.findByUserUsername(username).orElseThrow(() -> new IllegalArgumentException("Student profile not found")).getId();
        return loans.findByStudentIdOrderByBorrowedAtDesc(studentId);
    }

    public LibraryLoan returnLoan(Long loanId) {
        LibraryLoan loan = loans.findByIdForUpdate(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Library loan not found"));
        if (loan.getStatus() == LibraryLoanStatus.RETURNED) throw new IllegalStateException("Loan is already returned");
        loan.setStatus(LibraryLoanStatus.RETURNED);
        loan.setReturnedAt(LocalDateTime.now());
        LibraryItem item = items.findByIdForUpdate(loan.getLibraryItemId())
                .orElseThrow(() -> new IllegalArgumentException("Library item not found"));
        item.setAvailableCopies(Math.min(item.getTotalCopies(), item.getAvailableCopies() + 1));
        items.save(item);
        return loans.save(loan);
    }

    public void refreshOverdue() {
        loans.findByStatus(LibraryLoanStatus.BORROWED).stream()
                .filter(loan -> loan.getDueDate().isBefore(LocalDate.now()))
                .forEach(loan -> { loan.setStatus(LibraryLoanStatus.OVERDUE); loans.save(loan); });
    }
}