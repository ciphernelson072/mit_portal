package com.sis.controller;

import com.sis.dto.*;
import com.sis.entity.*;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AuditLogRepository;
import com.sis.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Admin Controller - Handles all administrative operations.
 * Full CRUD operations for users, students, teachers with soft delete support.
 * All endpoints require ADMIN role authority.
 * <p>
 * Every request/response here is a DTO - entities (User/Student/Teacher) never cross
 * this boundary. That matters even for admin-only endpoints: entities carry internal
 * JPA/Hibernate proxy state and, in User's case, a password field that's merely
 * write-only-annotated for Jackson rather than structurally absent from the type.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private GradeImportService gradeImportService;

    // ===== USER MANAGEMENT ENDPOINTS =====

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllActiveUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserById(id));
    }

    @PostMapping("/users")
    @Transactional
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = adminService.createUser(request);
        auditAdminAccountChange("ADMIN_USER_CREATED", created.getId(),
                "Created account with role " + created.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        if (id.equals(currentAdminId())) {
            throw new IllegalArgumentException("Administrators cannot change their own account role here");
        }
        UserDto before = adminService.getUserById(id);
        UserDto updated = adminService.updateUser(id, request);
        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(before.getRole(), updated.getRole())) {
            changedFields.add("role " + before.getRole() + " -> " + updated.getRole());
        }
        if (!Objects.equals(before.getEmail(), updated.getEmail())) changedFields.add("email");
        if (!Objects.equals(before.getPhone(), updated.getPhone())) changedFields.add("phone");
        if (request.getPassword() != null && !request.getPassword().isBlank()) changedFields.add("password");
        if (!changedFields.isEmpty()) {
            auditAdminAccountChange("ADMIN_USER_UPDATED", id,
                    "Updated account fields: " + String.join(", ", changedFields));
        }
        return ResponseEntity.ok(updated);
    }

    private Long currentAdminId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
            .map(user -> user.getId())
            .orElseThrow(() -> new IllegalStateException("Authenticated administrator account not found"));
    }

    private void auditAdminAccountChange(String action, Long targetUserId, String details) {
        auditLogRepository.save(new AuditLog(currentAdminId(), action,
                "Target account #" + targetUserId + ": " + details));
    }

    /** Soft delete user (deactivate account). */
    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<String> removeUser(@PathVariable Long id) {
        if (id.equals(currentAdminId())) {
            throw new IllegalArgumentException("Administrators cannot deactivate their own account");
        }
        UserDto target = adminService.getUserById(id);
        adminService.removeUser(id);
        auditAdminAccountChange("ADMIN_USER_DEACTIVATED", id,
                "Deactivated account with role " + target.getRole());
        return ResponseEntity.ok("User account deactivated successfully");
    }

    @PostMapping("/users/{id}/restore")
    @Transactional
    public ResponseEntity<String> restoreUser(@PathVariable Long id) {
        UserDto target = adminService.getUserById(id);
        adminService.restoreUser(id);
        auditAdminAccountChange("ADMIN_USER_RESTORED", id,
                "Restored account with role " + target.getRole());
        return ResponseEntity.ok("User account restored successfully");
    }

    @GetMapping("/deleted-users")
    public ResponseEntity<List<UserDto>> getDeletedUsers() {
        return ResponseEntity.ok(adminService.getSoftDeletedUsers());
    }

    // ===== STUDENT MANAGEMENT ENDPOINTS =====

    @GetMapping("/students")
    public ResponseEntity<org.springframework.data.domain.Page<AdminStudentDto>> getAllStudents(
            @org.springframework.data.web.PageableDefault(size = 25, sort = "id") org.springframework.data.domain.Pageable pageable,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(adminService.searchStudents(q, active, pageable));
    }

    @GetMapping("/students/{id}")
    public ResponseEntity<AdminStudentDto> getStudentById(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getStudentById(id));
    }

    @GetMapping("/students/{id}/programs")
    public ResponseEntity<List<com.sis.dto.StudentProgramLifecycleDto>> getStudentProgramLifecycles(
            @PathVariable Long id) {
        return ResponseEntity.ok(adminService.getStudentProgramLifecycles(id));
    }

    @GetMapping("/students/{id}/status-history")
    public ResponseEntity<List<com.sis.dto.StudentStatusHistoryDto>> getStudentStatusHistory(
            @PathVariable Long id) {
        return ResponseEntity.ok(adminService.getStudentStatusHistory(id));
    }

    @GetMapping("/students/{id}/transfer-options")
    public ResponseEntity<com.sis.dto.StudentTransferOptionsDto> getStudentTransferOptions(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getStudentTransferOptions(id));
    }

    @PostMapping("/students/{id}/transfer")
    public ResponseEntity<String> transferStudentProgram(
            @PathVariable Long id,
            @Valid @RequestBody com.sis.dto.CreateStudentProgramTransferRequest request) {
        String changedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        adminService.transferStudentProgram(id, request.getSourceProgramId(), request.getSourceAcademicYearId(),
                request.getDestinationProgramId(), request.getDestinationAcademicYearId(),
                request.getReason(), changedBy);
        return ResponseEntity.ok("Student programme transfer completed successfully");
    }

    /** Admit (create) a new student - creates both the student and its user account. */
    @PostMapping("/students/admit")
    public ResponseEntity<AdminStudentDto> admitStudent(@Valid @RequestBody CreateStudentRequest request) {
        AdminStudentDto admitted = adminService.admitStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(admitted);
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<AdminStudentDto> updateStudent(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateStudentRequest request) {
        return ResponseEntity.ok(adminService.updateStudent(id, request));
    }

    /** Soft delete (remove) student - marks the student's account as inactive. */
    @DeleteMapping("/students/{id}")
    public ResponseEntity<String> removeStudent(@PathVariable Long id) {
        adminService.removeStudent(id);
        return ResponseEntity.ok("Student removed successfully");
    }

    @PostMapping("/students/{id}/restore")
    public ResponseEntity<String> restoreStudent(@PathVariable Long id) {
        adminService.restoreStudent(id);
        return ResponseEntity.ok("Student account restored successfully");
    }

    @GetMapping("/students/{id}/cgpa")
    public ResponseEntity<StudentCGPADto> getStudentCGPA(@PathVariable Long id) {
        AdminStudentDto student = adminService.getStudentById(id);
        StudentCGPADto dto = new StudentCGPADto();
        dto.setId(student.getId());
        dto.setFullName(student.getFullName());
        dto.setCgpa(student.getCgpa());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/students/{id}/calculate-cgpa")
    public ResponseEntity<String> recalculateCGPA(@PathVariable Long id) {
        studentService.calculateCGPA(id);
        return ResponseEntity.ok("CGPA recalculated successfully");
    }

    @PostMapping("/students/{id}/status")
    public ResponseEntity<String> changeStudentStatus(@PathVariable Long id, @Valid @RequestBody com.sis.dto.ChangeStudentStatusRequest request) {
        String changedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        adminService.changeStudentLifecycleStatus(id, request.getProgramId(), request.getAcademicYearId(),
                request.getStatus(), request.getReason(), changedBy);
        return ResponseEntity.ok("Student status updated successfully");
    }

    // ===== TEACHER MANAGEMENT ENDPOINTS =====

    @GetMapping("/teachers")
    public ResponseEntity<org.springframework.data.domain.Page<AdminTeacherDto>> getAllTeachers(
            @org.springframework.data.web.PageableDefault(size = 25, sort = "id") org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllTeachers(pageable));
    }

    @GetMapping("/teachers/{id}")
    public ResponseEntity<AdminTeacherDto> getTeacherById(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getTeacherById(id));
    }

    /** Hire (create) a new teacher - creates both the teacher and its user account. */
    @PostMapping("/teachers/hire")
    public ResponseEntity<AdminTeacherDto> hireTeacher(@Valid @RequestBody CreateTeacherRequest request) {
        AdminTeacherDto hired = adminService.hireTeacher(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(hired);
    }

    @PutMapping("/teachers/{id}")
    public ResponseEntity<AdminTeacherDto> updateTeacher(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateTeacherRequest request) {
        return ResponseEntity.ok(adminService.updateTeacher(id, request));
    }

    /** Soft delete (remove) teacher - marks the teacher's account as inactive. */
    @DeleteMapping("/teachers/{id}")
    public ResponseEntity<String> removeTeacher(@PathVariable Long id) {
        adminService.removeTeacher(id);
        return ResponseEntity.ok("Teacher removed successfully");
    }

    @PostMapping("/teachers/{id}/restore")
    public ResponseEntity<String> restoreTeacher(@PathVariable Long id) {
        adminService.restoreTeacher(id);
        return ResponseEntity.ok("Teacher account restored successfully");
    }

    // ===== STATISTICS AND REPORTING ENDPOINTS =====

    @GetMapping("/statistics")
    public ResponseEntity<AdminStatisticsDto> getStatistics() {
        return ResponseEntity.ok(adminService.getStatistics());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardOverviewDto> getDashboardOverview() {
        AdminStatisticsDto stats = adminService.getStatistics();
        List<AdminFeeDto> fees = schoolService.getAllFees();
        long totalCourses = schoolService.listCourseDtos().size();

        BigDecimal totalRevenue = fees.stream()
                .map(AdminFeeDto::getAmountPaid)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AdminDashboardOverviewDto overview = new AdminDashboardOverviewDto(
                stats.getTotalStudents(),
                stats.getTotalTeachers(),
                stats.getTotalUsers(),
                totalCourses,
                (long) fees.size(),
                fees.stream().filter(fee -> fee.getBalance() != null && fee.getBalance().signum() == 0).count(),
                fees.stream().filter(fee -> fee.getBalance() == null || fee.getBalance().signum() > 0).count(),
                totalRevenue);

        return ResponseEntity.ok(overview);
    }

    @GetMapping("/attendance")
    public ResponseEntity<List<AttendanceDto>> getAttendanceRecords() {
        return ResponseEntity.ok(attendanceService.getAllAttendance().stream()
                .map(schoolService::toAttendanceDto)
                .toList());
    }

    @GetMapping("/fees")
    @PreAuthorize("@permissionSecurity.canManageStudentFees()")
    public ResponseEntity<List<AdminFeeDto>> getFees() {
        return ResponseEntity.ok(schoolService.getAllFees());
    }

    @GetMapping("/grades")
    public ResponseEntity<List<AdminGradeDto>> getGrades() {
        return ResponseEntity.ok(schoolService.getAllGradeDtos());
    }

    @PostMapping("/grades/import")
    public ResponseEntity<AdminGradeDto> importGrade(@Valid @RequestBody AdminGradeImportRequest request) {
        Student student = schoolService.getStudentById(request.getStudentId())
            .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        Course course = schoolService.getCourseById(request.getCourseId())
            .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        Grade grade = schoolService.saveGrade(student, course, request.getGradeValue(), request.getRemarks());
        if (request.getGpaScore() != null) {
            grade = schoolService.updateGradeScore(grade, request.getGpaScore());
        }
        auditLogRepository.save(new AuditLog(currentAdminId(), "GRADE_IMPORTED",
                "Grade " + grade.getId() + " imported for student " + student.getId()
                        + " in course " + course.getId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new AdminGradeDto(
                grade.getId(),
                student.getId(),
                student.getFullName(),
                student.getUser().getUsername(),
                course.getId(),
                course.getName(),
                grade.getGradeValue(),
                grade.getGpaScore(),
                grade.getRemarks(),
                grade.getStatus() != null ? grade.getStatus().name() : "PENDING",
                grade.getSubmittedBy(),
                grade.getApprovedBy(),
                grade.getRejectedBy(),
                grade.getRejectionReason()));
    }

    @PostMapping("/grades/import-file")
    public ResponseEntity<List<AdminGradeDto>> importGradesFile(@RequestParam("file") MultipartFile file) {
        String importedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        List<AdminGradeDto> imported = gradeImportService.importGrades(file, importedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(imported);
    }

    @PostMapping("/email/test")
    public ResponseEntity<Void> testEmail(@Valid @RequestBody TestEmailRequest request) {
        emailService.sendTestEmail(request.getRecipient());
        return ResponseEntity.ok().build();
    }

    // ===== COURSE MANAGEMENT ENDPOINTS =====

    @GetMapping("/course-categories")
    public ResponseEntity<List<CourseCategoryDto>> getCourseCategories() {
        return ResponseEntity.ok(schoolService.listCourseCategories());
    }

    @PostMapping("/course-categories")
    public ResponseEntity<CourseCategoryDto> createCourseCategory(@Valid @RequestBody CreateCourseCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolService.createCourseCategory(request));
    }

    @PostMapping("/course-categories/{id}/active")
    public ResponseEntity<CourseCategoryDto> setCourseCategoryActive(@PathVariable Long id, @RequestParam boolean active) {
        return ResponseEntity.ok(schoolService.setCourseCategoryActive(id, active));
    }

    @PutMapping("/course-categories/{id}")
    public ResponseEntity<CourseCategoryDto> updateCourseCategory(@PathVariable Long id,
                                                                    @Valid @RequestBody CreateCourseCategoryRequest request) {
        return ResponseEntity.ok(schoolService.updateCourseCategory(id, request));
    }

    @DeleteMapping("/course-categories/{id}")
    public ResponseEntity<Void> deleteCourseCategory(@PathVariable Long id) {
        schoolService.deleteCourseCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courses")
    public ResponseEntity<org.springframework.data.domain.Page<AdminCourseDto>> getAllCourses(
            @org.springframework.data.web.PageableDefault(size = 25, sort = "id") org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(schoolService.listCourseDtos(pageable));
    }

    @PostMapping("/courses")
    public ResponseEntity<AdminCourseDto> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolService.createCourseDto(request));
    }

    @PutMapping("/courses/{id}")
    public ResponseEntity<AdminCourseDto> updateCourse(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateCourseRequest request) {
        return ResponseEntity.ok(schoolService.updateCourseDto(id, request));
    }

    @GetMapping("/course-units")
    public ResponseEntity<List<CourseUnitDto>> getCourseUnits() {
        return ResponseEntity.ok(schoolService.listCourseUnits());
    }

    @PostMapping("/course-units")
    public ResponseEntity<CourseUnitDto> createCourseUnit(@Valid @RequestBody CreateCourseUnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolService.createCourseUnit(request));
    }

    @PutMapping("/course-units/{id}/teacher")
    public ResponseEntity<CourseUnitDto> assignCourseUnitTeacher(@PathVariable Long id,
                                                                   @RequestParam Long teacherId) {
        return ResponseEntity.ok(schoolService.assignCourseUnitTeacher(id, teacherId));
    }

    @PutMapping("/course-units/{id}/students")
    public ResponseEntity<CourseUnitDto> enrollCourseUnitStudents(@PathVariable Long id,
                                                                   @RequestBody List<Long> studentIds) {
        if (studentIds != null && studentIds.stream().anyMatch(idValue -> idValue == null || idValue <= 0)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(schoolService.enrollStudentsInCourseUnit(id, studentIds));
    }
}
