package com.sis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_result_status_notification_events")
public class TeacherResultStatusNotificationEvent {

    @Id
    @Column(name = "event_key", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String eventKey;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "teacher_user_id", nullable = false)
    private Long teacherUserId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected TeacherResultStatusNotificationEvent() {
    }

    public TeacherResultStatusNotificationEvent(String eventKey, Long studentId, Long teacherUserId, String status) {
        this.eventKey = eventKey;
        this.studentId = studentId;
        this.teacherUserId = teacherUserId;
        this.status = status;
    }

    public String getEventKey() {
        return eventKey;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getTeacherUserId() {
        return teacherUserId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
