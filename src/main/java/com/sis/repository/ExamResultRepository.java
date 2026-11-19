package com.sis.repository;

import com.sis.entity.ExamResult;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ExamResultRepository extends JpaRepository<ExamResult, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ExamResult r WHERE r.id = :id")
    Optional<ExamResult> findByIdForUpdate(@Param("id") Long id);

    List<ExamResult> findByStudentId(Long studentId);
    List<ExamResult> findByStudentIdAndPublishedTrue(Long studentId);
}
