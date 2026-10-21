package com.sis.entity;

import javax.persistence.*;

@Entity
@Table(name = "suppliers", uniqueConstraints = @UniqueConstraint(name = "uk_supplier_code", columnNames = "supplier_code"))
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "supplier_code", nullable = false, length = 40) private String supplierCode;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 255) private String email;
    @Column(length = 40) private String phone;
    @Column(nullable = false) private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}