package com.sis.repository;

import com.sis.entity.CoursePrerequisite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, Long> {
    @EntityGraph(attributePaths = {"course", "prerequisiteCourse"})
    List<CoursePrerequisite> findByCourseId(Long courseId);

    @EntityGraph(attributePaths = {"course", "prerequisiteCourse"})
    @Query("select prerequisite from CoursePrerequisite prerequisite where prerequisite.id = :id")
    Optional<CoursePrerequisite> findByIdWithCourses(@Param("id") Long id);

    @Query("select prerequisite.prerequisiteCourse.id from CoursePrerequisite prerequisite "
            + "where prerequisite.course.id = :courseId")
    List<Long> findPrerequisiteCourseIdsByCourseId(@Param("courseId") Long courseId);
}