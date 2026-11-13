package com.sis.repository;

import com.sis.entity.StudentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface StudentStatusHistoryRepository extends JpaRepository<StudentStatusHistory, Long> {
    List<StudentStatusHistory> findByStudentIdOrderByChangedAtDesc(Long studentId);

    @EntityGraph(attributePaths = {"studentProgram.program", "studentProgram.academicYear"})
    List<StudentStatusHistory> findTop100ByStudentIdOrderByChangedAtDescIdDesc(Long studentId);
}