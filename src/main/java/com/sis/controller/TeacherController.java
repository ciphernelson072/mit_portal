package com.sis.controller;

import com.sis.dto.AttendanceRequest;
import com.sis.dto.GradeRequest;
import com.sis.dto.StudentProfileDto;
import com.sis.dto.TeacherProfileDto;
import com.sis.entity.Course;
import com.sis.entity.Student;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasAuthority('TEACHER')")
public class TeacherController {

    private final SchoolService schoolService;

    public TeacherController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @PostMapping("/grades")
    public ResponseEntity<?> postGrade(@RequestBody GradeRequest request) {
        Student student = schoolService.getStudentById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        Course course = schoolService.getCourseById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        return ResponseEntity.ok(schoolService.saveGrade(student, course, request.getGradeValue(), request.getRemarks()));
    }

    @PostMapping("/attendance")
    public ResponseEntity<?> postAttendance(@RequestBody AttendanceRequest request) {
        Student student = schoolService.getStudentById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        Course course = schoolService.getCourseById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        return ResponseEntity.ok(schoolService.saveAttendance(student, course, request.getDate(), request.isPresent()));
    }

    @GetMapping("/students/{studentId}/profile")
    public ResponseEntity<?> getStudentProfile(@PathVariable Long studentId) {
        return schoolService.getStudentById(studentId)
                .map(student -> ResponseEntity.ok(schoolService.getStudentProfile(studentId)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/courses/{courseId}/students")
    public ResponseEntity<List<StudentProfileDto>> listStudentsForCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(schoolService.listStudentsByCourse(courseId).stream()
                .map(student -> schoolService.getStudentProfile(student.getId()))
                .collect(Collectors.toList()));
    }

    @GetMapping("/me")
    public ResponseEntity<TeacherProfileDto> getTeacherInfo() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getTeacherProfileByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
