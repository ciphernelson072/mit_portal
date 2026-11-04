package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "club_memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "student_id"}))
public class ClubMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    public ClubMembership() {}
    public ClubMembership(Long clubId, Long studentId) { this.clubId = clubId; this.studentId = studentId; }
    public Long getId() { return id; }
    public Long getClubId() { return clubId; }
    public Long getStudentId() { return studentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
