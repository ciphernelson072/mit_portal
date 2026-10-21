package com.sis.repository;

import com.sis.entity.CurriculumCourse;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CurriculumCourseRepository extends JpaRepository<CurriculumCourse, Long> {
    @EntityGraph(attributePaths = {"program", "course"})
    List<CurriculumCourse> findByProgramIdOrderBySemesterNumberAscCourseCourseCodeAsc(Long programId);

    @EntityGraph(attributePaths = {"program", "course"})
    @Query("select curriculumCourse from CurriculumCourse curriculumCourse where curriculumCourse.id = :id")
    Optional<CurriculumCourse> findByIdWithProgramAndCourse(@Param("id") Long id);
}