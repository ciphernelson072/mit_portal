package com.sis.controller;

import java.util.List;
import java.util.LinkedHashSet;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import com.sis.entity.Course;
import com.sis.entity.Exam;
import com.sis.entity.ExamResult;
import com.sis.entity.NotificationCategory;
import com.sis.entity.RegistrationStatus;
import com.sis.entity.Student;
import com.sis.entity.TimetableEntry;
import com.sis.dto.CreateExamResultRequest;
import com.sis.dto.CreateTimetableRequest;
import java.time.LocalDateTime;
import jakarta.validation.Valid;
import com.sis.repository.ExamRepository;
import com.sis.repository.ExamResultRepository;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.TimetableEntryRepository;
import com.sis.service.PdfDocumentService;
import com.sis.service.SchoolService;
import com.sis.service.ExamPermitService;
import com.sis.service.NotificationService;
import com.sis.service.AcademicReportService;

@RestController
@RequestMapping("/api/academic")
public class AcademicController {
    private final SchoolService schoolService;
    private final TimetableEntryRepository timetable;
    private final AcademicTermRepository academicTerms;
    private final ExamResultRepository results;
    private final ExamRepository exams;
    private final PdfDocumentService pdfDocuments;
    private final ExamPermitService examPermits;
    private final NotificationService notifications;
    private final AcademicReportService academicReports;

    public AcademicController(SchoolService schoolService, TimetableEntryRepository timetable,
                              ExamResultRepository results,
                              AcademicTermRepository academicTerms, ExamRepository exams,
                              PdfDocumentService pdfDocuments, ExamPermitService examPermits,
                              NotificationService notifications, AcademicReportService academicReports) {
        this.schoolService = schoolService;
        this.timetable = timetable;
        this.academicTerms = academicTerms;
        this.results = results;
        this.exams = exams;
        this.pdfDocuments = pdfDocuments;
        this.examPermits = examPermits;
        this.notifications = notifications;
        this.academicReports = academicReports;
    }

    @GetMapping("/student/timetable")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<TimetableEntry>> studentTimetable() {
        Student student = currentStudent();
        if (student == null) {
            return ResponseEntity.notFound().build();
        }
        LinkedHashSet<Long> legacyCourseIds = new LinkedHashSet<>();
        student.getCourses().stream().map(Course::getId).forEach(legacyCourseIds::add);
        List<TimetableEntry> entries = new java.util.ArrayList<>();
        if (!legacyCourseIds.isEmpty()) {
            entries.addAll(timetable.findByCourseIdInAndAcademicTermIdIsNull(List.copyOf(legacyCourseIds)));
        }
        entries.addAll(timetable.findCurrentRegisteredEntries(
                student.getId(), RegistrationStatus.REGISTERED, LocalDate.now()));
        return ResponseEntity.ok(entries);
    }

    @PostMapping("/timetable")
    @PreAuthorize("@permissionSecurity.canManageAcademicOperations()")
    @Transactional
    public ResponseEntity<TimetableEntry> createTimetable(@Valid @RequestBody CreateTimetableRequest request) {
        ensureTeacherOwnsCourse(request.getCourseId());
        if (!request.getStartsAt().isBefore(request.getEndsAt())) {
            throw new IllegalArgumentException("Timetable start time must be before end time");
        }
        Long academicTermId = request.getAcademicTermId();
        if (academicTermId != null) {
            academicTermId = academicTerms.findByIdForUpdate(academicTermId)
                    .orElseThrow(() -> new IllegalArgumentException("Academic term not found"))
                    .getId();
        }
        final Long lockedAcademicTermId = academicTermId;
        ensureNoVenueConflict(request, lockedAcademicTermId);
        TimetableEntry entry = new TimetableEntry();
        entry.setCourseId(request.getCourseId());
        entry.setAcademicTermId(lockedAcademicTermId);
        entry.setDayOfWeek(request.getDayOfWeek().trim().toUpperCase(Locale.ROOT));
        entry.setStartsAt(request.getStartsAt());
        entry.setEndsAt(request.getEndsAt());
        entry.setVenue(request.getVenue().trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(timetable.save(entry));
    }

    private void ensureNoVenueConflict(CreateTimetableRequest request, Long academicTermId) {
        String day = request.getDayOfWeek().trim();
        String venue = request.getVenue().trim();
        boolean hasConflict = timetable.findByDayOfWeekIgnoreCase(day).stream()
                .filter(existing -> existing.getAcademicTermId() == null
                        || (academicTermId != null && existing.getAcademicTermId().equals(academicTermId)))
                .filter(existing -> existing.getVenue().trim().equalsIgnoreCase(venue))
                .anyMatch(existing -> existing.getStartsAt().isBefore(request.getEndsAt())
                        && request.getStartsAt().isBefore(existing.getEndsAt()));
        if (hasConflict) {
            throw new IllegalStateException("Venue is already booked during this timetable period");
        }
    }

    @GetMapping("/student/exam-results")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<ExamResult>> studentExamResults() {
        Student student = currentStudent();
        return student == null ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(academicReports.isOfficiallyPublished(student.getId())
                        ? results.findByStudentIdAndPublishedTrue(student.getId()) : List.of());
    }

    @GetMapping("/student/hall-ticket")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<byte[]> hallTicket() {
        Student student = currentStudent();
        if (student == null) return ResponseEntity.notFound().build();
        try {
                List<Exam> permittedExams = exams.findForStudentByEnrolledCourses(
                        student.getId(), RegistrationStatus.REGISTERED)
                    .stream()
                    .filter(exam -> examPermits.hasApprovedPermit(student.getId(), exam.getId()))
                    .toList();
                byte[] pdf = pdfDocuments.hallTicket(permittedExams);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"hall-ticket.pdf\"")
                    .body(pdf);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to generate hall ticket", e);
        }
    }

    @PostMapping("/exam-results")
    @PreAuthorize("@permissionSecurity.canManageAcademicOperations()")
    public ResponseEntity<ExamResult> createExamResult(@Valid @RequestBody CreateExamResultRequest request) {
        Exam exam = exams.findById(request.getExamId())
                .orElseThrow(() -> new IllegalArgumentException("Exam not found"));
        ensureTeacherOwnsCourse(exam.getCourseId());
        if (!schoolService.studentEnrolledInCourse(request.getStudentId(), exam.getCourseId())) {
            throw new IllegalArgumentException("Student is not enrolled in the exam course");
        }
        if (request.getScore().compareTo(request.getMaxScore()) > 0) {
            throw new IllegalArgumentException("Score cannot exceed maximum score");
        }
        ExamResult result = new ExamResult();
        result.setExamId(request.getExamId());
        result.setStudentId(request.getStudentId());
        result.setScore(request.getScore());
        result.setMaxScore(request.getMaxScore());
        result.setRemarks(request.getRemarks());
        result.setPublished(false);
        result.setPublishedAt(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(results.save(result));
    }

    @PostMapping("/exam-results/{id}/publish")
    @PreAuthorize("@permissionSecurity.canPublishExamResults()")
    @Transactional
    public ResponseEntity<ExamResult> publishExamResult(@org.springframework.web.bind.annotation.PathVariable Long id) {
        ExamResult result = results.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Exam result not found"));
        if (Boolean.TRUE.equals(result.getPublished())) {
            return ResponseEntity.ok(result);
        }
        result.setPublished(true);
        result.setPublishedAt(LocalDateTime.now());
        ExamResult saved = results.save(result);
        Long userId = schoolService.getStudentUserId(saved.getStudentId())
                .orElseThrow(() -> new IllegalStateException("Student account not found for exam result"));
        notifications.create(userId, "Exam result available",
                "Your exam result is now available.", NotificationCategory.GRADE);
        return ResponseEntity.ok(saved);
    }

    private Student currentStudent() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentWithCoursesByUsername(username).orElse(null);
    }

    private void ensureTeacherOwnsCourse(Long courseId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Course course = schoolService.getCourseById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        if (authentication.getAuthorities().stream().anyMatch(authority -> "ADMIN".equals(authority.getAuthority()))) {
            return;
        }
        if (!schoolService.teacherOwnsCourse(authentication.getName(), course)) {
            throw new org.springframework.security.access.AccessDeniedException("Teacher does not own this course");
        }
    }
}
