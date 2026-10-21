package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "transport_passes")
public class TransportPass {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(nullable = false) private String passType = "STUDENT_SEMESTER";
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "valid_until") private LocalDate validUntil;
    public TransportPass() {}
    public TransportPass(Long studentId, LocalDate validUntil) { this.studentId = studentId; this.validUntil = validUntil; }
    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getPassType() { return passType; }
    public String getStatus() { return status; }
    public LocalDate getValidUntil() { return validUntil; }
}
