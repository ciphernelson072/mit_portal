package com.sis.repository;

import com.sis.entity.Attendance;
import com.sis.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByStudentId(Long studentId);
    long countByStudentId(Long studentId);
    long countByStudentIdAndPresentTrue(Long studentId);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.student JOIN FETCH a.course "
            + "WHERE a.course.id = :courseId AND a.date = :date")
    List<Attendance> findByCourseIdAndDateWithStudentAndCourse(@Param("courseId") Long courseId,
                                                               @Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.student JOIN FETCH a.course")
    List<Attendance> findAllWithStudentAndCourse();

    @Query("SELECT a FROM Attendance a WHERE a.student.id = :studentId AND a.course.id = :courseId AND a.date = :date")
    List<Attendance> findByStudentIdAndCourseIdAndDate(@Param("studentId") Long studentId,
                                                       @Param("courseId") Long courseId,
                                                       @Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.student JOIN FETCH a.course "
            + "WHERE a.student.id = :studentId AND a.date BETWEEN :startDate AND :endDate "
            + "ORDER BY a.date ASC, a.id ASC")
    List<Attendance> findByStudentIdAndDateRangeWithStudentAndCourse(
            @Param("studentId") Long studentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT a FROM Attendance a JOIN FETCH a.student JOIN FETCH a.course "
            + "WHERE a.student.id = :studentId ORDER BY a.date DESC, a.id DESC")
    List<Attendance> findByStudentIdWithCourse(@Param("studentId") Long studentId);

    List<Attendance> findByStudentIdAndCourseId(Long studentId, Long courseId);

}
