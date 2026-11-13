package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "library_loans")
public class LibraryLoan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "library_item_id", nullable = false) private Long libraryItemId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(name = "borrowed_at", nullable = false) private LocalDateTime borrowedAt = LocalDateTime.now();
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "returned_at") private LocalDateTime returnedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LibraryLoanStatus status = LibraryLoanStatus.BORROWED;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLibraryItemId() { return libraryItemId; }
    public void setLibraryItemId(Long libraryItemId) { this.libraryItemId = libraryItemId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public LocalDateTime getBorrowedAt() { return borrowedAt; }
    public void setBorrowedAt(LocalDateTime borrowedAt) { this.borrowedAt = borrowedAt; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(LocalDateTime returnedAt) { this.returnedAt = returnedAt; }
    public LibraryLoanStatus getStatus() { return status; }
    public void setStatus(LibraryLoanStatus status) { this.status = status; }
}