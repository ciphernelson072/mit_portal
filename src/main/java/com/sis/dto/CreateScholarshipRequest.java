package com.sis.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

public class CreateScholarshipRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal amount;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
