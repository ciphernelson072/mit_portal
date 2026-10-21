package com.sis.repository;
import com.sis.entity.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> { List<AssignmentSubmission> findByStudentId(Long studentId); Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId); }
