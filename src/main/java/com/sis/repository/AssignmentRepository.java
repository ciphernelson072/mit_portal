package com.sis.repository;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sis.entity.Assignment;
import java.time.LocalDateTime;
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
	List<Assignment> findByCourseIdIn(List<Long> courseIds);
	List<Assignment> findByCourseIdInOrderByDueAtAsc(List<Long> courseIds);
	List<Assignment> findByTeacherIdOrderByDueAtAsc(Long teacherId);
	long countByCourseIdInAndDueAtAfter(List<Long> courseIds, LocalDateTime dueAt);
}
