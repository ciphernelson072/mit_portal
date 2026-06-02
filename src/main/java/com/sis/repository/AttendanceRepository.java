package com.sis.repository;

import com.sis.entity.Attendance;
import com.sis.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByStudentId(Long studentId);
    List<Attendance> findByCourseIdAndDate(Long courseId, LocalDate date);

    public List<Attendance> findByStudentAndDateRange(Student student, LocalDate startDate, LocalDate endDate);

    public List<Attendance> findByStudent(Student student);
}
