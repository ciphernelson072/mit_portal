package com.sis.repository;

import com.sis.entity.GradeCorrectionRequest;
import com.sis.entity.GradeCorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;

public interface GradeCorrectionRequestRepository extends JpaRepository<GradeCorrectionRequest, Long> {
    boolean existsByGradeIdAndStatusIn(Long gradeId, Collection<GradeCorrectionStatus> statuses);
    boolean existsByGradeId(Long gradeId);
    boolean existsByGradeCourseIdAndStatusIn(Long courseId, Collection<GradeCorrectionStatus> statuses);

    Optional<GradeCorrectionRequest> findTopByGradeIdAndStatusInOrderByIdDesc(
            Long gradeId, Collection<GradeCorrectionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM GradeCorrectionRequest request WHERE request.id = :id")
    Optional<GradeCorrectionRequest> findByIdForUpdate(@Param("id") Long id);
}
