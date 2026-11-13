package com.sis.entity;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "scholarships")
public class Scholarship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(length = 2000) private String description;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private boolean open = true;
    public Scholarship() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean value) { open = value; }
}
