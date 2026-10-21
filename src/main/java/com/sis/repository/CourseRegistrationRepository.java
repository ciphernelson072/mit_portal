package com.sis.repository;

import com.sis.entity.CourseRegistration;
import com.sis.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface CourseRegistrationRepository extends JpaRepository<CourseRegistration, Long> {
    @EntityGraph(attributePaths = {"section.course", "section.academicTerm"})
    List<CourseRegistration> findByStudentIdAndStatusOrderByRegisteredAtDesc(Long studentId, RegistrationStatus status);
    @EntityGraph(attributePaths = {"section.course"})
    List<CourseRegistration> findByStudentIdAndSectionAcademicTermIdAndStatus(
            Long studentId, Long academicTermId, RegistrationStatus status);
    @EntityGraph(attributePaths = {"section.course"})
    @Query("select registration from CourseRegistration registration "
            + "where registration.student.id = :studentId "
            + "and registration.status = :status "
            + "and registration.section.academicTerm.academicYear.active = true "
            + "and registration.section.academicTerm.endDate >= :asOfDate")
    List<CourseRegistration> findCurrentAcademicYearTimetableRegistrations(
            @Param("studentId") Long studentId,
            @Param("status") RegistrationStatus status,
            @Param("asOfDate") LocalDate asOfDate);
    Optional<CourseRegistration> findByStudentIdAndSectionId(Long studentId, Long sectionId);
    @EntityGraph(attributePaths = {"section.course", "section.academicTerm"})
    @Query("select registration from CourseRegistration registration where registration.id = :id")
    Optional<CourseRegistration> findByIdWithSectionCourseAndTerm(@Param("id") Long id);
    @EntityGraph(attributePaths = {"section.course", "section.academicTerm"})
    @Query("select registration from CourseRegistration registration "
            + "where registration.id = :registrationId and registration.student.id = :studentId")
    Optional<CourseRegistration> findByIdAndStudentIdWithSectionCourseAndTerm(
            @Param("registrationId") Long registrationId, @Param("studentId") Long studentId);
    boolean existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatus(
            Long studentId, Long academicTermId, Long courseId, RegistrationStatus status);
    boolean existsByStudentIdAndSectionCourseIdAndStatus(
            Long studentId, Long courseId, RegistrationStatus status);
    boolean existsBySectionCourseTeacherIdAndStudentIdAndStatus(
            Long teacherId, Long studentId, RegistrationStatus status);
    long countBySectionIdAndStatus(Long sectionId, RegistrationStatus status);
    @Query("select registration.section.id as sectionId, count(registration.id) as registrationCount "
            + "from CourseRegistration registration "
            + "where registration.section.id in :sectionIds and registration.status = :status "
            + "group by registration.section.id")
    List<SectionRegistrationCount> countRegisteredBySectionIds(
            @Param("sectionIds") List<Long> sectionIds, @Param("status") RegistrationStatus status);
}