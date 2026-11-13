package com.sis.dto;

import javax.validation.constraints.NotBlank;

public class CreateSupplierRequest {
    @NotBlank private String supplierCode;
    @NotBlank private String name;
    private String email;
    private String phone;
    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}