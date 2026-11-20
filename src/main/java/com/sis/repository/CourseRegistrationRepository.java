package com.sis.repository;

import com.sis.entity.CourseRegistration;
import com.sis.entity.RegistrationStatus;
import com.sis.entity.GradeStatus;
import com.sis.entity.Attendance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface CourseRegistrationRepository extends JpaRepository<CourseRegistration, Long> {
    @EntityGraph(attributePaths = {"section.course", "section.course.teacher", "section.course.teacher.user",
            "section.academicTerm"})
    List<CourseRegistration> findByStudentIdAndStatusOrderByRegisteredAtDesc(Long studentId, RegistrationStatus status);
    @EntityGraph(attributePaths = {"section.course", "section.course.teacher", "section.course.teacher.user"})
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
    @EntityGraph(attributePaths = {"section.course", "section.course.teacher", "section.course.teacher.user",
            "section.academicTerm"})
    @Query("select registration from CourseRegistration registration where registration.id = :id")
    Optional<CourseRegistration> findByIdWithSectionCourseAndTerm(@Param("id") Long id);
    @EntityGraph(attributePaths = {"section.course", "section.course.teacher", "section.course.teacher.user",
            "section.academicTerm"})
    @Query("select registration from CourseRegistration registration "
            + "where registration.id = :registrationId and registration.student.id = :studentId")
    Optional<CourseRegistration> findByIdAndStudentIdWithSectionCourseAndTerm(
            @Param("registrationId") Long registrationId, @Param("studentId") Long studentId);
    boolean existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatus(
            Long studentId, Long academicTermId, Long courseId, RegistrationStatus status);
    boolean existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatusIn(
            Long studentId, Long academicTermId, Long courseId, List<RegistrationStatus> statuses);
    boolean existsByStudentIdAndSectionCourseIdAndStatus(
            Long studentId, Long courseId, RegistrationStatus status);
    boolean existsBySectionCourseTeacherIdAndStudentIdAndStatus(
            Long teacherId, Long studentId, RegistrationStatus status);
    long countBySectionIdAndStatus(Long sectionId, RegistrationStatus status);
    @Query("select distinct registration.section.course.id from CourseRegistration registration "
            + "where registration.section.academicTerm.id = :termId "
            + "and registration.status = :registrationStatus "
            + "and not exists (select grade.id from Grade grade "
            + "where grade.student.id = registration.student.id "
            + "and grade.course.id = registration.section.course.id "
            + "and grade.status = :approvedStatus)")
    List<Long> findCourseIdsWithMissingApprovedGradesByTerm(
            @Param("termId") Long termId,
            @Param("registrationStatus") RegistrationStatus registrationStatus,
            @Param("approvedStatus") GradeStatus approvedStatus);
    @Query("select registration.section.id as sectionId, count(registration.id) as registrationCount "
            + "from CourseRegistration registration "
            + "where registration.section.id in :sectionIds and registration.status = :status "
            + "group by registration.section.id")
    List<SectionRegistrationCount> countRegisteredBySectionIds(
            @Param("sectionIds") List<Long> sectionIds, @Param("status") RegistrationStatus status);
    @Query("select term.id as termId, registration.student.user.id as userId, "
            + "registration.student.id as studentId, course.id as courseId, "
            + "course.courseCode as courseCode, course.name as courseName, "
            + "sum(case when attendance.present = true then 1L else 0L end) as presentSessions, "
            + "count(attendance.id) as totalSessions "
            + "from CourseRegistration registration "
            + "join registration.section section "
            + "join section.academicTerm term "
            + "join section.course course "
            + "join Attendance attendance on attendance.student = registration.student "
            + "and attendance.course = course "
            + "and attendance.date between term.startDate and term.endDate "
            + "where registration.status = :registrationStatus "
            + "and section.active = true "
            + "and term.startDate <= :today and term.endDate >= :today "
            + "group by term.id, registration.student.user.id, registration.student.id, "
            + "course.id, course.courseCode, course.name")
    List<AttendanceRiskCandidate> findCurrentTermAttendanceRiskCandidates(
            @Param("registrationStatus") RegistrationStatus registrationStatus,
            @Param("today") LocalDate today);
}