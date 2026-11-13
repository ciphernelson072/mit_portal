package com.sis.repository;

import com.sis.entity.GradeAssessmentMark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeAssessmentMarkRepository extends JpaRepository<GradeAssessmentMark, Long> {
    List<GradeAssessmentMark> findByGradeId(Long gradeId);

    boolean existsByComponentId(Long componentId);
    Optional<GradeAssessmentMark> findByGradeIdAndComponentId(Long gradeId, Long componentId);
}
