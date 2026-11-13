package com.sis.repository;

import com.sis.entity.CourseAssessmentComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseAssessmentComponentRepository extends JpaRepository<CourseAssessmentComponent, Long> {
    List<CourseAssessmentComponent> findByCourseIdOrderByIdAsc(Long courseId);
    Optional<CourseAssessmentComponent> findByIdAndCourseId(Long id, Long courseId);
    Optional<CourseAssessmentComponent> findByCourseIdAndComponentCodeIgnoreCase(Long courseId, String componentCode);
}
