package com.sis.repository;

import com.sis.entity.AdmissionApplication;
import com.sis.entity.AdmissionApplicationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplication, Long> {
    @EntityGraph(attributePaths = {"program", "academicYear"})
    Optional<AdmissionApplication> findByApplicationNumber(String applicationNumber);

    @EntityGraph(attributePaths = {"program", "academicYear"})
    @Query("select application from AdmissionApplication application "
            + "where application.applicationNumber = :applicationNumber "
            + "and application.normalizedEmail = lower(trim(:email))")
    Optional<AdmissionApplication> findByApplicationNumberAndEmail(
            @Param("applicationNumber") String applicationNumber, @Param("email") String email);

    @EntityGraph(attributePaths = {"program", "academicYear"})
    List<AdmissionApplication> findByStatusOrderBySubmittedAtAsc(AdmissionApplicationStatus status);

    @EntityGraph(attributePaths = {"program", "academicYear"})
    @Query("select application from AdmissionApplication application")
    List<AdmissionApplication> findAllWithProgramAndAcademicYear();

    @EntityGraph(attributePaths = {"program", "academicYear"})
    @Query("select application from AdmissionApplication application where application.id = :id")
    Optional<AdmissionApplication> findByIdWithProgramAndAcademicYear(@Param("id") Long id);

    @Query("select distinct application.id from AdmissionApplication application, StudentProgram enrollment "
            + "where enrollment.program = application.program "
            + "and enrollment.academicYear = application.academicYear "
            + "and lower(trim(enrollment.student.email)) = lower(trim(application.email))")
    List<Long> findEnrolledApplicationIds();

    boolean existsByNormalizedEmailAndAcademicYearId(String normalizedEmail, Long academicYearId);
}