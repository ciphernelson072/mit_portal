package com.sis.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class CreateLibraryLoanRequest {
    @NotNull private Long studentId;
    @NotNull private Long libraryItemId;
        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
    @NotNull @FutureOrPresent private LocalDate dueDate;

    public Long getLibraryItemId() { return libraryItemId; }
    public void setLibraryItemId(Long libraryItemId) { this.libraryItemId = libraryItemId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}