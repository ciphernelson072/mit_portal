package com.sis.repository;

import com.sis.entity.Grade;
import com.sis.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findByStudentId(Long studentId);

    public List<Grade> findByStudent(Student student);
}
