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
    
    List<Attendance> findByCourseIdAndDate(Long courseId, LocalDate date);
    
    @Query("SELECT a FROM Attendance a WHERE a.student = :student AND a.date BETWEEN :startDate AND :endDate")
    List<Attendance> findByStudentAndDateRange(
            @Param("student") Student student,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    
    List<Attendance> findByStudent(Student student);
}