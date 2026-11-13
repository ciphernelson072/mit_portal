package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_registrations", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "student_id"}))
public class EventRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private String status = "REGISTERED";

    @Column(nullable = false)
    private LocalDateTime registeredAt = LocalDateTime.now();

    public EventRegistration() {}

    public EventRegistration(Long eventId, Long studentId) {
        this.eventId = eventId;
        this.studentId = studentId;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getStudentId() { return studentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
}
