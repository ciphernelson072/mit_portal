package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "staff", uniqueConstraints = @UniqueConstraint(name = "uk_staff_employee_number", columnNames = "employee_number"))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Staff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, unique = true) private User user;
    @Column(name = "employee_number", nullable = false, length = 40) private String employeeNumber;
    @Column(name = "full_name", nullable = false, length = 200) private String fullName;
    @Column(nullable = false, length = 150) private String title;
    @Column(name = "department_id") private Long departmentId;
    @Column(name = "hire_date", nullable = false) private LocalDate hireDate;
    @Column(nullable = false) private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getEmployeeNumber() { return employeeNumber; }
    public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}