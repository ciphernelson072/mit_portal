package com.sis.controller;

import com.sis.dto.AssignmentSubmissionReviewDto;
import com.sis.entity.Assignment;
import com.sis.entity.AssignmentSubmission;
import com.sis.entity.Student;
import com.sis.repository.AssignmentRepository;
import com.sis.repository.AssignmentSubmissionRepository;
import com.sis.repository.StudentRepository;
import com.sis.service.SchoolService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portal/teacher/assignments")
public class TeacherAssignmentSubmissionController {

    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final StudentRepository students;
    private final SchoolService schoolService;

    public TeacherAssignmentSubmissionController(AssignmentRepository assignments,
                                                 AssignmentSubmissionRepository submissions,
                                                 StudentRepository students,
                                                 SchoolService schoolService) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.students = students;
        this.schoolService = schoolService;
    }

    @GetMapping("/{assignmentId}/submissions")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<List<AssignmentSubmissionReviewDto>> listSubmissions(
            @PathVariable Long assignmentId) {
        Assignment assignment = assignments.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return ResponseEntity.notFound().build();
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        var course = schoolService.getCourseById(assignment.getCourseId()).orElse(null);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }
        if (!schoolService.teacherOwnsCourse(username, course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<AssignmentSubmission> assignmentSubmissions =
                submissions.findByAssignmentIdOrderBySubmittedAtAsc(assignmentId);
        if (assignmentSubmissions.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<Long> studentIds = assignmentSubmissions.stream()
                .map(AssignmentSubmission::getStudentId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Student> studentsById = students.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        List<AssignmentSubmissionReviewDto> response = assignmentSubmissions.stream()
                .map(submission -> {
                    Student student = studentsById.get(submission.getStudentId());
                    return new AssignmentSubmissionReviewDto(
                            submission.getId(),
                            submission.getStudentId(),
                            student == null ? "Student #" + submission.getStudentId() : student.getFullName(),
                            submission.getContent(),
                            submission.getSubmittedAt());
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
