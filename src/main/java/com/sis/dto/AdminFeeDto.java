package com.sis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AdminFeeDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private String studentUsername;
    private String description;
    private BigDecimal amount;
    private boolean paid;
    private LocalDate dueDate;
    private LocalDate paidDate;

    public AdminFeeDto(Long id, Long studentId, String studentName, String studentUsername,
                       String description, BigDecimal amount, boolean paid,
                       LocalDate dueDate, LocalDate paidDate) {
        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentUsername = studentUsername;
        this.description = description;
        this.amount = amount;
        this.paid = paid;
        this.dueDate = dueDate;
        this.paidDate = paidDate;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getStudentUsername() { return studentUsername; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public boolean isPaid() { return paid; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getPaidDate() { return paidDate; }
}