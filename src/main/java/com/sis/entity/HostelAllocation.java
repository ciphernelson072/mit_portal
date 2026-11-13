package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hostel_allocations")
public class HostelAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "hostel_room_id", nullable = false) private Long hostelRoomId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(name = "application_id", nullable = false) private Long applicationId;
    @Column(nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "allocated_at", nullable = false) private LocalDateTime allocatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHostelRoomId() { return hostelRoomId; }
    public void setHostelRoomId(Long hostelRoomId) { this.hostelRoomId = hostelRoomId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(LocalDateTime allocatedAt) { this.allocatedAt = allocatedAt; }
}