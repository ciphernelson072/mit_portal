package com.sis.repository;

import com.sis.entity.CourseSection;
import com.sis.entity.StudentLifecycleStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {
    @EntityGraph(attributePaths = {"course", "academicTerm"})
    List<CourseSection> findByAcademicTermIdAndActiveTrueOrderByCourseCourseCodeAsc(Long academicTermId);

    @EntityGraph(attributePaths = {"course", "academicTerm"})
    @Query("select distinct section from CourseSection section "
            + "join StudentProgram enrollment "
            + "on enrollment.academicYear.id = section.academicTerm.academicYear.id "
            + "join CurriculumCourse curriculum "
            + "on curriculum.program.id = enrollment.program.id "
            + "and curriculum.course.id = section.course.id "
            + "where section.academicTerm.id = :termId "
            + "and section.active = true "
            + "and section.academicTerm.registrationOpen = true "
            + "and enrollment.student.id = :studentId "
            + "and enrollment.status = :status "
            + "and enrollment.program.active = true "
            + "and enrollment.program.department.active = true "
            + "and enrollment.program.department.faculty.active = true "
            + "and enrollment.program.department.faculty.campus.active = true")
    List<CourseSection> findEligibleByAcademicTermAndStudentId(
            @Param("termId") Long termId,
            @Param("studentId") Long studentId,
            @Param("status") StudentLifecycleStatus status);

    @EntityGraph(attributePaths = {"course", "academicTerm"})
    @Query("select section from CourseSection section where section.id = :id")
    Optional<CourseSection> findByIdWithCourseAndTerm(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select section from CourseSection section where section.id = :id and section.active = true")
    Optional<CourseSection> findActiveByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select section from CourseSection section where section.id = :id")
    Optional<CourseSection> findByIdForUpdate(@Param("id") Long id);
}