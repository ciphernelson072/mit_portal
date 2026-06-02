package com.sis.repository;

import com.sis.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByUserId(Long userId);

    public Optional<Teacher> findByUserUsername(String username);
}
