package com.sis.repository;

import com.sis.entity.AttendanceDispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttendanceDisputeRepository extends JpaRepository<AttendanceDispute, Long> {
    List<AttendanceDispute> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<AttendanceDispute> findAllByOrderByCreatedAtDesc();
    boolean existsByAttendanceIdAndStudentIdAndStatusIn(Long attendanceId, Long studentId, List<String> statuses);

    @Query("select d from AttendanceDispute d where d.attendance.course.id = :courseId order by d.createdAt desc")
    List<AttendanceDispute> findByCourseId(@Param("courseId") Long courseId);

    Optional<AttendanceDispute> findByIdAndStudentId(Long id, Long studentId);
}
