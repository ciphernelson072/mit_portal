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
    private BigDecimal amountPaid;
    private BigDecimal balance;
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
        this.amountPaid = paid ? amount : BigDecimal.ZERO;
        this.balance = paid ? BigDecimal.ZERO : amount;
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
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getBalance() { return balance; }
    public boolean isPaid() { return paid; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getPaidDate() { return paidDate; }

    public void setSettlementAmounts(BigDecimal amountPaid, BigDecimal balance) {
        this.amountPaid = amountPaid;
        this.balance = balance;
    }
}