package com.sis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FeeDto {
    private Long id;
    private String description;
    private BigDecimal amount;
    private boolean paid;
    private LocalDate dueDate;
    private LocalDate paidDate;

    public FeeDto() {
    }

    public FeeDto(Long id, String description, BigDecimal amount, boolean paid, LocalDate dueDate, LocalDate paidDate) {
        this.id = id;
        this.description = description;
        this.amount = amount;
        this.paid = paid;
        this.dueDate = dueDate;
        this.paidDate = paidDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(LocalDate paidDate) {
        this.paidDate = paidDate;
    }
}