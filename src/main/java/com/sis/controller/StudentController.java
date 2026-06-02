package com.sis.controller;

import com.sis.dto.CourseDto;
import com.sis.dto.StudentProfileDto;
import com.sis.entity.Grade;
import com.sis.entity.Attendance;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasAuthority('STUDENT')")
public class StudentController {

    private final SchoolService schoolService;

    public StudentController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping("/profile")
    public ResponseEntity<StudentProfileDto> profile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        StudentProfileDto profile = schoolService.getStudentProfileByUsername(username);
        return profile != null ? ResponseEntity.ok(profile) : ResponseEntity.notFound().build();
    }

    @GetMapping("/grades")
    public ResponseEntity<List<Grade>> grades() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(schoolService.getGradesForStudent(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> courses() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(schoolService.getCoursesForStudent(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/attendance")
    public ResponseEntity<List<Attendance>> attendance() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(schoolService.getAttendanceForStudent(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    // --- NEW COMBINED ENDPOINT FOR APP.JS DASHBOARD ---
    @GetMapping("/my-status")
    public ResponseEntity<Map<String, Object>> getMyStatus() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        
        return schoolService.getStudentByUsername(username).map(student -> {
            Map<String, Object> statusMap = new HashMap<>();
            
            List<Grade> studentGrades = schoolService.getGradesForStudent(student.getId());
            List<Attendance> studentAttendance = schoolService.getAttendanceForStudent(student.getId());
            
            // Map Grades into the expected JSON format
            List<Map<String, Object>> gradesJson = studentGrades.stream().map(g -> {
                Map<String, Object> map = new HashMap<>();
                map.put("courseName", g.getCourse() != null ? g.getCourse().getName() : "Unknown Course");
                map.put("score", g.getScore()); 
                return map;
            }).collect(Collectors.toList());

            // Map Attendance into the expected JSON format
            List<Map<String, Object>> attendanceJson = studentAttendance.stream().map(a -> {
                Map<String, Object> map = new HashMap<>();
                map.put("date", a.getDate() != null ? a.getDate().toString() : "Unknown Date");
                map.put("status", a.getStatus()); 
                return map;
            }).collect(Collectors.toList());

            // Assemble the final object payload
            statusMap.put("balance", "0.00"); // Ready for your fees logic later
            statusMap.put("deadline", "None"); 
            statusMap.put("grades", gradesJson);
            statusMap.put("attendance", attendanceJson);
            
            return ResponseEntity.ok(statusMap);
        }).orElse(ResponseEntity.notFound().build());
    }
}