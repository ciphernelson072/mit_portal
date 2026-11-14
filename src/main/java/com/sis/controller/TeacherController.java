package com.sis.controller;

import com.sis.dto.AttendanceApprovalRequest;
import com.sis.dto.AttendanceDto;
import com.sis.dto.CourseDto;
import com.sis.dto.CourseUnitDto;
import com.sis.dto.GradeDto;
import com.sis.dto.GradeRequest;
import com.sis.dto.StudentProfileDto;
import com.sis.dto.TeacherDashboardOverviewDto;
import com.sis.dto.TeacherProfileDto;
import com.sis.dto.TeacherGradeDto;
import com.sis.dto.TeacherResultPublicationStatusDto;
import com.sis.entity.Course;
import com.sis.entity.CourseUnit;
import com.sis.entity.Student;
import com.sis.entity.Grade;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.GradeRepository;
import com.sis.service.AttendanceService;
import com.sis.service.AcademicReportService;
import com.sis.service.SchoolService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("@permissionSecurity.canManageAcademicOperations()")
public class TeacherController {

    private final SchoolService schoolService;
    private final AttendanceService attendanceService;
    private final GradeRepository gradeRepository;
    private final AcademicReportService academicReportService;

    public TeacherController(SchoolService schoolService, AttendanceService attendanceService,
                             GradeRepository gradeRepository, AcademicReportService academicReportService) {
        this.schoolService = schoolService;
        this.attendanceService = attendanceService;
        this.gradeRepository = gradeRepository;
        this.academicReportService = academicReportService;
    }

    @PostMapping("/attendance/approve")
    public ResponseEntity<List<AttendanceDto>> approveRosterAttendance(
            @Valid @RequestBody AttendanceApprovalRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(attendanceService.approveTeacherRosterAttendance(username, request).stream()
                .map(schoolService::toAttendanceDto)
                .collect(Collectors.toList()));
    }

    @GetMapping("/courses/{courseId}/grades")
    public ResponseEntity<List<TeacherGradeDto>> listGradesForCourse(@PathVariable Long courseId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Course course = schoolService.getCourseById(courseId).orElse(null);
        if (course == null) return ResponseEntity.notFound().build();
        if (!schoolService.teacherOwnsCourse(username, course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<TeacherGradeDto> grades = gradeRepository.findByCourseIdWithStudent(courseId).stream()
                .map(grade -> new TeacherGradeDto(
                        grade.getId(),
                        grade.getStudent().getId(),
                        grade.getStudent().getFullName(),
                        grade.getGradeValue(),
                        grade.getRemarks(),
                        grade.getStatus() == null ? "PENDING" : grade.getStatus().name(),
                        grade.getRejectionReason(),
                        grade.getSubmittedAt(),
                        grade.getAttemptNumber(),
                        grade.getOriginalAttempt() == null ? null : grade.getOriginalAttempt().getId(),
                        grade.getOriginalAttempt() == null ? "ORIGINAL_ATTEMPT" : "RETAKE_SUBMITTED",
                        grade.getTotalMark(),
                        grade.getGpaScore(),
                        grade.getRetakeRequired()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(grades);
    }

    @PostMapping("/grades")
    public ResponseEntity<GradeDto> postGrade(@Valid @RequestBody GradeRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Student student = schoolService.getStudentById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + request.getStudentId()));
        Course course = schoolService.getCourseById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + request.getCourseId()));
        if (!schoolService.teacherOwnsCourse(username, course)
                || !schoolService.studentEnrolledInCourse(student.getId(), course.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var grade = schoolService.saveGrade(student, course, request.getGradeValue(), request.getRemarks(), username);
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolService.toGradeDto(grade));
    }

    @PutMapping("/grades/{gradeId}")
    public ResponseEntity<GradeDto> updateGrade(@PathVariable Long gradeId, @Valid @RequestBody GradeRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Grade grade = schoolService.getGradeById(gradeId).orElse(null);
        if (grade == null || !schoolService.teacherOwnsCourse(username, grade.getCourse()) || !grade.getStudent().getId().equals(request.getStudentId()) || !grade.getCourse().getId().equals(request.getCourseId())) {
            return ResponseEntity.notFound().build();
        }
        Grade saved = schoolService.updateGrade(grade, request.getGradeValue(), request.getRemarks(), null, username);
        return ResponseEntity.ok(schoolService.toGradeDto(saved));
    }


    @DeleteMapping("/grades/{gradeId}")
    public ResponseEntity<Void> deleteGrade(@PathVariable Long gradeId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Grade grade = schoolService.getGradeById(gradeId).orElse(null);
        if (grade == null || !schoolService.teacherOwnsCourse(username, grade.getCourse())) return ResponseEntity.notFound().build();
        schoolService.deleteGrade(grade);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/students/{studentId}/profile")
    public ResponseEntity<StudentProfileDto> getStudentProfile(@PathVariable Long studentId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Student student = schoolService.getStudentById(studentId).orElse(null);
        if (student == null) {
            return ResponseEntity.notFound().build();
        }
        boolean assignedCourseEnrollment = schoolService.getTeacherByUsername(username)
                .map(teacher -> schoolService.getCoursesForTeacher(teacher.getId()).stream()
                        .anyMatch(course -> schoolService.studentEnrolledInCourse(student.getId(), course.getId())))
                .orElse(false);
        return assignedCourseEnrollment
                ? ResponseEntity.ok(schoolService.getStudentProfile(studentId))
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/courses/{courseId}/students")
    public ResponseEntity<List<StudentProfileDto>> listStudentsForCourse(@PathVariable Long courseId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Course course = schoolService.getCourseById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));
        if (!schoolService.teacherOwnsCourse(username, course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(schoolService.listStudentsByCourse(courseId).stream()
                .map(student -> schoolService.getStudentProfile(student.getId()))
                .collect(Collectors.toList()));
    }

    @GetMapping("/course-units/{unitId}/students")
    public ResponseEntity<List<StudentProfileDto>> listStudentsForCourseUnit(@PathVariable Long unitId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!schoolService.teacherOwnsCourseUnit(username, unitId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(schoolService.listStudentsByCourseUnit(unitId).stream()
                .map(student -> schoolService.getStudentProfile(student.getId()))
                .collect(Collectors.toList()));
    }

    @GetMapping("/courses/{courseId}/attendance")
    public ResponseEntity<List<AttendanceDto>> attendanceForCourse(@PathVariable Long courseId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Course course = schoolService.getCourseById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));
        if (!schoolService.teacherOwnsCourse(username, course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(attendanceService.getCourseAttendance(courseId, date).stream()
                .map(schoolService::toAttendanceDto)
                .collect(Collectors.toList()));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<TeacherDashboardOverviewDto> getDashboardOverview() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        var teacher = schoolService.getTeacherByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found for user: " + username));

        List<CourseDto> courses = schoolService.getCoursesForTeacher(teacher.getId());
        List<Long> courseIds = courses.stream().map(CourseDto::getId).toList();

        long studentCount = schoolService.countDistinctStudentsInCourses(courseIds);
        long pendingGradeCount = schoolService.countPendingGradesInCourses(courseIds);
        long upcomingAssignmentCount = schoolService.countUpcomingAssignmentsInCourses(
                courseIds, java.time.LocalDateTime.now());

        long pendingAttendanceCount = schoolService.countStudentsWithoutAttendanceInCourses(
                courseIds, java.time.LocalDate.now());

        TeacherDashboardOverviewDto overview = new TeacherDashboardOverviewDto(
                (long) courses.size(),
                studentCount,
                pendingGradeCount,
                upcomingAssignmentCount,
                pendingAttendanceCount);
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/results/publication-status")
    public ResponseEntity<List<TeacherResultPublicationStatusDto>> getResultPublicationStatuses() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        var teacher = schoolService.getTeacherByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found for user: " + username));
        if (teacher.getUser() == null || teacher.getUser().getId() == null) {
            throw new IllegalStateException("Teacher account is unavailable for result publication status.");
        }

        List<CourseDto> courses = schoolService.getCoursesForTeacher(teacher.getId());
        List<Long> courseIds = courses.stream()
                .map(CourseDto::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Map<Long, Student> studentsById = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<String>> courseCodesByStudent = new LinkedHashMap<>();
        for (Grade grade : gradeRepository.findByCourseIdsWithStudentAndCourse(courseIds)) {
            Student student = grade.getStudent();
            studentsById.putIfAbsent(student.getId(), student);
            courseCodesByStudent.computeIfAbsent(student.getId(), ignored -> new LinkedHashSet<>())
                    .add(grade.getCourse().getCourseCode());
        }

        Long teacherUserId = teacher.getUser().getId();
        List<TeacherResultPublicationStatusDto> statuses = studentsById.entrySet().stream()
                .map(entry -> academicReportService.getTeacherPublicationStatus(
                        entry.getKey(),
                        entry.getValue().getFullName(),
                        List.copyOf(courseCodesByStudent.get(entry.getKey())),
                        teacherUserId))
                .toList();
        return ResponseEntity.ok(statuses);
    }

    @GetMapping("/me")
    public ResponseEntity<TeacherProfileDto> getTeacherInfo() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getTeacherProfileByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Courses taught by the currently logged-in teacher. The frontend was already
     * calling this exact URL before it existed anywhere in the backend.
     */
    @GetMapping("/my-courses")
    public ResponseEntity<List<CourseDto>> getMyCourses() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getTeacherByUsername(username)
                .map(teacher -> ResponseEntity.ok(schoolService.getCoursesForTeacher(teacher.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/course-units")
    public ResponseEntity<List<CourseUnitDto>> getMyCourseUnits() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getTeacherByUsername(username)
                .map(teacher -> ResponseEntity.ok(schoolService.getCourseUnitsForTeacher(teacher.getId())))
                .orElse(ResponseEntity.notFound().build());
    }
}
