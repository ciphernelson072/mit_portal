package com.sis.repository;

import com.sis.entity.StudentProgram;
import com.sis.entity.StudentLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface StudentProgramRepository extends JpaRepository<StudentProgram, Long> {
    List<StudentProgram> findByStudentIdOrderByStartDateDesc(Long studentId);
    boolean existsByStudentIdAndProgramIdAndAcademicYearId(Long studentId, Long programId, Long academicYearId);
    @Query("select case when count(enrollment) > 0 then true else false end from StudentProgram enrollment "
            + "where enrollment.program.id = :programId and enrollment.academicYear.id = :academicYearId "
            + "and lower(trim(enrollment.student.email)) = lower(trim(:email))")
    boolean existsByProgrammeYearAndStudentEmail(@Param("programId") Long programId,
                                                 @Param("academicYearId") Long academicYearId,
                                                 @Param("email") String email);
    Optional<StudentProgram> findByStudentIdAndProgramIdAndAcademicYearId(Long studentId, Long programId, Long academicYearId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select enrollment from StudentProgram enrollment "
            + "where enrollment.student.id = :studentId and enrollment.program.id = :programId "
            + "and enrollment.academicYear.id = :academicYearId")
    Optional<StudentProgram> findForUpdateByStudentIdAndProgramIdAndAcademicYearId(
            @Param("studentId") Long studentId,
            @Param("programId") Long programId,
            @Param("academicYearId") Long academicYearId);
    Optional<StudentProgram> findByStudentIdAndAcademicYearId(Long studentId, Long academicYearId);
    boolean existsByStudentIdAndAcademicYearIdAndStatus(Long studentId, Long academicYearId, StudentLifecycleStatus status);

    @Query("select case when count(enrollment) > 0 then true else false end "
            + "from StudentProgram enrollment, CurriculumCourse curriculum "
            + "where enrollment.program.id = curriculum.program.id "
            + "and enrollment.student.id = :studentId "
            + "and enrollment.academicYear.id = :academicYearId "
            + "and enrollment.status = :status "
            + "and enrollment.program.active = true "
            + "and enrollment.program.department.active = true "
            + "and enrollment.program.department.faculty.active = true "
            + "and enrollment.program.department.faculty.campus.active = true "
            + "and curriculum.course.id = :courseId")
    boolean existsActiveProgramCourse(@Param("studentId") Long studentId,
                                      @Param("academicYearId") Long academicYearId,
                                      @Param("status") StudentLifecycleStatus status,
                                      @Param("courseId") Long courseId);
}