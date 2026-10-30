package com.sis.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.validation.Valid;

import com.sis.dto.CreateAssignmentRequest;
import com.sis.dto.AssignmentSubmissionRequest;
import com.sis.dto.CreateExamRequest;
import com.sis.dto.SaveSystemSettingRequest;
import com.sis.entity.Assignment;
import com.sis.entity.AssignmentSubmission;
import com.sis.entity.NotificationCategory;
import com.sis.entity.AuditLog;
import com.sis.entity.CampusEvent;
import com.sis.entity.Club;
import com.sis.entity.ClubMembership;
import com.sis.entity.Course;
import com.sis.entity.EventRegistration;
import com.sis.entity.Exam;
import com.sis.entity.PaymentTransaction;
import com.sis.entity.Scholarship;
import com.sis.entity.ScholarshipApplication;
import com.sis.entity.Student;
import com.sis.entity.SystemSetting;
import com.sis.entity.TransportRoute;
import com.sis.entity.User;
import com.sis.entity.UserPreference;
import com.sis.repository.AssignmentRepository;
import com.sis.repository.AssignmentSubmissionRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.CampusEventRepository;
import com.sis.repository.ClubMembershipRepository;
import com.sis.repository.ClubRepository;
import com.sis.repository.EventRegistrationRepository;
import com.sis.repository.ExamRepository;
import com.sis.repository.PaymentTransactionRepository;
import com.sis.repository.ScholarshipApplicationRepository;
import com.sis.repository.ScholarshipRepository;
import com.sis.repository.SystemSettingRepository;
import com.sis.repository.TransportRouteRepository;
import com.sis.repository.UserPreferenceRepository;
import com.sis.service.SchoolService;
import com.sis.service.NotificationService;
import com.sis.service.ScholarshipService;
import com.sis.service.EventRegistrationService;
import com.sis.util.UserPreferenceNormalizer;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/portal")
public class PortalController {
    private final SchoolService schoolService;
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final ExamRepository exams;
    private final ScholarshipRepository scholarships;
    private final ScholarshipApplicationRepository applications;
    private final ScholarshipService scholarshipService;
    private final UserPreferenceRepository preferences;
    private final SystemSettingRepository settings;
    private final AuditLogRepository auditLogs;
    private final PaymentTransactionRepository transactions;
    private final CampusEventRepository events;
    private final TransportRouteRepository routes;
    private final EventRegistrationRepository eventRegistrations;
    private final ClubRepository clubs;
    private final ClubMembershipRepository clubMemberships;
    private final NotificationService notifications;
    private final EventRegistrationService eventRegistrationService;

    public PortalController(SchoolService schoolService, AssignmentRepository assignments,
                            AssignmentSubmissionRepository submissions, ExamRepository exams,
                            ScholarshipRepository scholarships, ScholarshipApplicationRepository applications,
                            ScholarshipService scholarshipService,
                            UserPreferenceRepository preferences,
                            SystemSettingRepository settings, AuditLogRepository auditLogs,
                            PaymentTransactionRepository transactions, CampusEventRepository events,
                            TransportRouteRepository routes, EventRegistrationRepository eventRegistrations,
                            ClubRepository clubs, ClubMembershipRepository clubMemberships,
                            NotificationService notifications, EventRegistrationService eventRegistrationService) {
        this.schoolService = schoolService;
        this.assignments = assignments;
        this.submissions = submissions;
        this.exams = exams;
        this.scholarships = scholarships;
        this.applications = applications;
        this.scholarshipService = scholarshipService;
        this.preferences = preferences;
        this.settings = settings;
        this.auditLogs = auditLogs;
        this.transactions = transactions;
        this.events = events;
        this.routes = routes;
        this.eventRegistrations = eventRegistrations;
        this.clubs = clubs;
        this.clubMemberships = clubMemberships;
        this.notifications = notifications;
        this.eventRegistrationService = eventRegistrationService;
    }

    @GetMapping("/student/assignments")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<Assignment>> studentAssignments() {
        Student student = currentStudent();
        if (student == null) return ResponseEntity.notFound().build();
        List<Long> courseIds = schoolService.getCoursesForStudent(student.getId()).stream()
                .map(com.sis.dto.CourseDto::getId).collect(Collectors.toList());
        return ResponseEntity.ok(courseIds.isEmpty() ? List.of() : assignments.findByCourseIdIn(courseIds));
    }

    @GetMapping("/teacher/assignments")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<List<Assignment>> teacherAssignments() {
        return currentUser()
                .filter(user -> "TEACHER".equals(user.getRole().name()))
                .flatMap(user -> schoolService.getTeacherByUsername(user.getUsername()))
                .map(teacher -> {
                    List<Long> courseIds = schoolService.getCoursesForTeacher(teacher.getId()).stream()
                            .map(com.sis.dto.CourseDto::getId)
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(courseIds.isEmpty()
                            ? List.<Assignment>of()
                            : assignments.findByCourseIdInOrderByDueAtAsc(courseIds));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/student/assignments/{assignmentId}/submissions")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<AssignmentSubmission> submit(@PathVariable Long assignmentId,
                                                        @Valid @RequestBody AssignmentSubmissionRequest body) {
        Student student = currentStudent();
        Assignment assignment = assignments.findById(assignmentId).orElse(null);
        if (student == null || assignment == null) return ResponseEntity.notFound().build();
        if (!schoolService.studentEnrolledInCourse(student.getId(), assignment.getCourseId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<AssignmentSubmission> existingSubmission =
                submissions.findByAssignmentIdAndStudentId(assignmentId, student.getId());
        AssignmentSubmission submission = existingSubmission.orElseGet(AssignmentSubmission::new);
        submission.setAssignmentId(assignmentId);
        submission.setStudentId(student.getId());
        submission.setContent(body.getContent());
        submission.setSubmittedAt(LocalDateTime.now());
        HttpStatus status = existingSubmission.isPresent() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(submissions.save(submission));
    }

    @GetMapping("/student/submissions")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<AssignmentSubmission>> studentSubmissions() {
        Student student = currentStudent();
        return student == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(submissions.findByStudentId(student.getId()));
    }

    @GetMapping("/student/exams")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<Exam>> studentExams() {
        Student student = currentStudent();
        if (student == null) return ResponseEntity.notFound().build();
        List<Long> courseIds = schoolService.getCoursesForStudent(student.getId()).stream()
                .map(com.sis.dto.CourseDto::getId).collect(Collectors.toList());
        return ResponseEntity.ok(courseIds.isEmpty() ? List.of() : exams.findByCourseIdIn(courseIds));
    }

    @GetMapping("/student/payments")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<PaymentTransaction>> studentPayments() {
        Student student = currentStudent();
        return student == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(transactions.findByStudentIdOrderByCreatedAtDesc(student.getId()));
    }

    @GetMapping("/events")
    @PreAuthorize("isAuthenticated()")
    public List<CampusEvent> events() { return events.findAll(); }

    @PostMapping("/student/events/{eventId}/register")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<EventRegistration> registerForEvent(@PathVariable Long eventId) {
        Student student = currentStudent();
        if (student == null) return ResponseEntity.notFound().build();
        EventRegistrationService.RegistrationOutcome outcome =
                eventRegistrationService.register(eventId, student.getId());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(outcome.registration());
    }

    @DeleteMapping("/student/events/{eventId}/registration")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<EventRegistration> cancelEventRegistration(@PathVariable Long eventId) {
        Student student = currentStudent();
        if (student == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(eventRegistrationService.cancel(eventId, student.getId()));
    }

    @GetMapping("/student/event-registrations")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<EventRegistration>> studentEventRegistrations() {
        Student student = currentStudent();
        return student == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(eventRegistrations.findByStudentId(student.getId()));
    }

    @GetMapping("/routes")
    @PreAuthorize("isAuthenticated()")
    public List<TransportRoute> routes() { return routes.findAll(); }

    @GetMapping("/clubs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> clubs() {
        Student student = currentStudent();
        List<Club> allClubs = clubs.findAll();
        if (allClubs.isEmpty()) return ResponseEntity.ok(List.of());

        List<Long> clubIds = allClubs.stream().map(Club::getId).collect(Collectors.toList());
        Map<Long, Long> memberCounts = new LinkedHashMap<>();
        clubMemberships.countActiveByClubIds(clubIds).forEach(row ->
                memberCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        Set<Long> joinedClubIds = student == null
                ? Set.of()
                : clubMemberships.findByClubIdInAndStudentId(clubIds, student.getId()).stream()
                        .filter(membership -> "ACTIVE".equals(membership.getStatus()))
                        .map(ClubMembership::getClubId)
                        .collect(Collectors.toSet());

        List<Map<String, Object>> result = allClubs.stream().map(club -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", club.getId());
            value.put("slug", club.getSlug());
            value.put("name", club.getName());
            value.put("description", club.getDescription());
            value.put("meetingInfo", club.getMeetingInfo());
            value.put("memberCount", memberCounts.getOrDefault(club.getId(), 0L));
            value.put("joined", joinedClubIds.contains(club.getId()));
            return value;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/student/clubs/{clubId}/join")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<ClubMembership> joinClub(@PathVariable Long clubId) {
        Student student = currentStudent();
        if (student == null || !clubs.existsById(clubId)) return ResponseEntity.notFound().build();
        return clubMemberships.findByClubIdAndStudentId(clubId, student.getId()).map(membership -> {
            membership.setStatus("ACTIVE");
            return ResponseEntity.ok(clubMemberships.save(membership));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.CREATED)
                .body(clubMemberships.save(new ClubMembership(clubId, student.getId()))));
    }

    @DeleteMapping("/student/clubs/{clubId}/leave")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<Void> leaveClub(@PathVariable Long clubId) {
        Student student = currentStudent();
        if (student == null) return ResponseEntity.notFound().build();
        return clubMemberships.findByClubIdAndStudentId(clubId, student.getId()).map(membership -> {
            membership.setStatus("LEFT");
            clubMemberships.save(membership);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.<Void>notFound().build());
    }

    @GetMapping("/scholarships")
    @PreAuthorize("isAuthenticated()")
    public List<Scholarship> scholarships() { return scholarships.findAll(); }

    @PostMapping("/student/scholarships/{id}/apply")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<ScholarshipApplication> applyScholarship(@PathVariable Long id) {
        Student student = currentStudent();
        if (student == null) return ResponseEntity.notFound().build();
        Optional<ScholarshipApplication> existing = applications.findByStudentIdAndScholarshipId(student.getId(), id);
        if (existing.isPresent()) return ResponseEntity.ok(existing.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(scholarshipService.apply(id, student.getId()));
    }

    @GetMapping("/student/scholarship-applications")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<ScholarshipApplication>> studentScholarshipApplications() {
        Student student = currentStudent();
        return student == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(applications.findByStudentId(student.getId()));
    }

    @GetMapping("/student/preferences")
    @PreAuthorize("hasAuthority('STUDENT')")
    @Deprecated
    public ResponseEntity<UserPreference> getPreferences() {
        return getSharedPreferences();
    }

    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPreference> getSharedPreferences() {
        return currentUser().map(user -> preferences.findByUserId(user.getId()).orElseGet(() -> {
            UserPreference value = new UserPreference(); value.setUserId(user.getId()); return value;
        })).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/student/preferences")
    @PreAuthorize("hasAuthority('STUDENT')")
    @Deprecated
    public ResponseEntity<UserPreference> savePreferences(@RequestBody Map<String, Object> body) {
        return saveSharedPreferences(body);
    }

    @PutMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPreference> saveSharedPreferences(@RequestBody Map<String, Object> body) {
        if (body == null) {
            return ResponseEntity.<UserPreference>badRequest().build();
        }
        User user = currentUser().orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        UserPreference value = preferences.findByUserId(user.getId()).orElseGet(UserPreference::new);
        value.setUserId(user.getId());

        Object languageValue = body.get("language");
        if (languageValue instanceof String language && !language.isBlank()) {
            if (language.length() > 20) {
                return ResponseEntity.badRequest().build();
            }
            value.setLanguage(UserPreferenceNormalizer.normalizeLanguage(language, value.getLanguage()));
        }

        Object themeValue = body.get("theme");
        if (themeValue instanceof String theme && !theme.isBlank()) {
            value.setTheme(UserPreferenceNormalizer.normalizeTheme(theme, value.getTheme()));
        }

        Object settingsJsonValue = body.get("settingsJson");
        Object settingsValue = body.get("settings");
        if (settingsJsonValue != null || settingsValue != null) {
            String normalizedSettings = UserPreferenceNormalizer.normalizeSettingsJson(
                    value.getSettingsJson(),
                    settingsJsonValue,
                    settingsValue
            );
            if (normalizedSettings.length() > 4000) {
                return ResponseEntity.badRequest().build();
            }
            value.setSettingsJson(normalizedSettings);
        }

        return ResponseEntity.ok(preferences.save(value));
    }

    @GetMapping("/admin/settings")
    @PreAuthorize("@permissionSecurity.canManagePortalSettings()")
    public List<SystemSetting> adminSettings() { return settings.findAll(); }

    @PutMapping("/admin/settings/{key}")
    @PreAuthorize("@permissionSecurity.canManagePortalSettings()")
    public ResponseEntity<SystemSetting> saveSetting(@PathVariable String key,
                                                      @Valid @RequestBody SaveSystemSettingRequest body) {
        if (key == null || key.isBlank() || key.length() > 100) {
            return ResponseEntity.badRequest().build();
        }
        SystemSetting setting = settings.findById(key).orElseGet(() -> new SystemSetting(key, ""));
        setting.setSettingValue(body.getValue());
        return ResponseEntity.ok(settings.save(setting));
    }

    @GetMapping("/admin/audit")
    @PreAuthorize("@permissionSecurity.canManagePortalSettings()")
    public List<AuditLog> audit() { return auditLogs.findTop100ByOrderByCreatedAtDesc(); }

    @PostMapping("/admin/assignments")
    @PreAuthorize("@permissionSecurity.canManageAssignments()")
    @Transactional
    public ResponseEntity<Assignment> createAssignment(@Valid @RequestBody CreateAssignmentRequest request) {
        Course course = schoolService.getCourseById(request.getCourseId()).orElse(null);
        if (course == null) return ResponseEntity.notFound().build();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Assignment assignment = new Assignment();
        assignment.setCourseId(request.getCourseId());
        assignment.setTitle(request.getTitle().trim());
        assignment.setDescription(request.getDescription());
        assignment.setDueAt(request.getDueAt());
        if ("TEACHER".equals(currentUser().map(user -> user.getRole().name()).orElse(null))) {
            if (!schoolService.teacherOwnsCourse(username, course)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (course.getTeacher() == null || course.getTeacher().getId() == null) {
            throw new IllegalStateException("Course does not have an assigned teacher.");
        }
        assignment.setTeacherId(course.getTeacher().getId());
        Assignment saved = assignments.save(assignment);
        schoolService.getUserIdsForStudentsInCourse(course.getId()).forEach(userId ->
            notifications.create(userId, "New assignment",
                "A new assignment, " + saved.getTitle() + ", is available for " + course.getName() + ".",
                NotificationCategory.ASSIGNMENT));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/teacher/assignments")
    @PreAuthorize("@permissionSecurity.canManageAssignments()")
    @Transactional
    public ResponseEntity<Assignment> createTeacherAssignment(@Valid @RequestBody CreateAssignmentRequest request) {
        return createAssignment(request);
    }

    @PostMapping("/admin/exams")
    @PreAuthorize("@permissionSecurity.canManageExams()")
    public Exam createExam(@Valid @RequestBody CreateExamRequest request) {
        if (schoolService.getCourseById(request.getCourseId()).isEmpty()) {
            throw new IllegalArgumentException("Course not found");
        }
        Exam exam = new Exam();
        exam.setCourseId(request.getCourseId());
        exam.setTitle(request.getTitle().trim());
        exam.setStartsAt(request.getStartsAt());
        exam.setVenue(request.getVenue().trim());
        return exams.save(exam);
    }

    @GetMapping("/admin/exams")
    @PreAuthorize("@permissionSecurity.canManageAcademicOperations()")
    public List<Map<String, Object>> adminExams() {
        return exams.findAll().stream().map(exam -> {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", exam.getId());
            response.put("courseId", exam.getCourseId());
            response.put("title", exam.getTitle());
            response.put("startsAt", exam.getStartsAt());
            response.put("venue", exam.getVenue());
            return response;
        }).toList();
    }

    private Student currentStudent() {
        return currentUser().flatMap(user -> schoolService.getStudentByUsername(user.getUsername())).orElse(null);
    }

    private Optional<User> currentUser() {
        return schoolService.getUserByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
