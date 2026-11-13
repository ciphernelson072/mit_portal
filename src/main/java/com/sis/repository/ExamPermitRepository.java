package com.sis.repository;

import com.sis.entity.ExamPermit;
import com.sis.entity.ExamPermitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamPermitRepository extends JpaRepository<ExamPermit, Long> {
    Optional<ExamPermit> findByExamIdAndStudentId(Long examId, Long studentId);
    List<ExamPermit> findByStudentId(Long studentId);
    List<ExamPermit> findByStatus(ExamPermitStatus status);
}
