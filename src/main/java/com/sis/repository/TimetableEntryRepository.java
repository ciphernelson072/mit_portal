package com.sis.repository;

import com.sis.entity.TimetableEntry;
import com.sis.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.LocalDate;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {
    List<TimetableEntry> findByCourseIdIn(List<Long> courseIds);
    List<TimetableEntry> findByDayOfWeekIgnoreCase(String dayOfWeek);
    List<TimetableEntry> findByCourseIdInAndAcademicTermIdIsNull(List<Long> courseIds);
    @Query("select distinct entry from TimetableEntry entry, CourseRegistration registration "
            + "where registration.student.id = :studentId "
            + "and registration.status = :status "
            + "and entry.courseId = registration.section.course.id "
            + "and entry.academicTermId = registration.section.academicTerm.id "
            + "and registration.section.academicTerm.academicYear.active = true "
            + "and registration.section.academicTerm.endDate >= :asOfDate")
    List<TimetableEntry> findCurrentRegisteredEntries(
            @Param("studentId") Long studentId,
            @Param("status") RegistrationStatus status,
            @Param("asOfDate") LocalDate asOfDate);
}
