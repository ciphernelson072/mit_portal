package com.sis.repository;

import com.sis.entity.TeacherResultStatusNotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherResultStatusNotificationEventRepository
        extends JpaRepository<TeacherResultStatusNotificationEvent, String> {
}
