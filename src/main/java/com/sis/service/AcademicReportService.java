package com.sis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sis.dto.AcademicReportDto;
import com.sis.dto.CourseGradeDto;
import com.sis.dto.TeacherResultPublicationStatusDto;
import com.sis.entity.AcademicReportApproval;
import com.sis.entity.AcademicResultSnapshot;
import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.Student;
import com.sis.entity.StudentProgram;
import com.sis.entity.TeacherResultStatusNotificationEvent;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AcademicResultSnapshotRepository;
import com.sis.repository.AcademicReportApprovalRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.TeacherResultStatusNotificationEventRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import com.sis.entity.AuditLog;

/**
 * Academic Report Service - Generates comprehensive academic reports for students
 * Provides performance analysis, CGPA tracking, and academic statistics
 */
@Service
@Transactional
public class AcademicReportService {

    private static final Logger logger = Logger.getLogger(AcademicReportService.class.getName());
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    @Autowired
    private StudentService studentService;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private AcademicReportApprovalRepository approvalRepository;

    @Autowired
    private AcademicResultSnapshotRepository snapshots;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private AuditLogRepository auditLogs;

    @Autowired
    private UserRepository users;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private TeacherResultStatusNotificationEventRepository statusNotificationEvents;

    @Autowired
    private StudentProgramRepository studentPrograms;

    /**
     * Generate comprehensive academic report for student
     * @param studentId student ID
     * @return academic report DTO
     */
    public AcademicReportDto generateAcademicReport(Long studentId) {
        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        AcademicReportDto report = new AcademicReportDto();
        report.setStudentId(student.getId());
        report.setStudentName(student.getFullName());
        report.setClassName(student.getClassName());
        List<Grade> grades = gradeRepository.findByStudentIdAndStatus(studentId, GradeStatus.APPROVED);
        boolean hasRetakes = grades.stream().anyMatch(grade -> grade.getOriginalAttempt() != null
                || (grade.getAttemptNumber() != null && grade.getAttemptNumber() > 1));
        report.setCgpa(grades.isEmpty() || hasRetakes
                ? null : GradeCalculationService.calculateApprovedCgpa(grades));
        report.setCreditsEarned(grades.isEmpty() || hasRetakes
                ? null : GradeCalculationService.calculateCreditsEarned(grades));
        report.setLatestSemester(hasRetakes ? null : GradeCalculationService.latestApprovedSemester(grades));
        report.setSemesterGpa(grades.isEmpty() || hasRetakes
                ? null : GradeCalculationService.calculateLatestSemesterGpa(grades));
        report.setAcademicStandingStatus(report.getCgpa() == null
                ? "NOT_FINALIZED"
                : GradeCalculationService.academicStanding(
                        report.getCgpa(), grades, gradeRepository.findCoreCourseIdsByStudentId(studentId)));
        if (report.getCgpa() != null && studentPrograms != null) {
            studentPrograms.findByStudentIdOrderByStartDateDesc(studentId).stream()
                    .map(StudentProgram::getProgram)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .ifPresent(program -> report.setAwardClassification(
                            UgandanTertiaryGradingPolicy.awardClassification(
                                    report.getCgpa(), program.getAwardType())));
        }
        report.setTotalCourses(grades.size());

        // Build course grades list
        List<CourseGradeDto> courseGrades = new ArrayList<>();
        for (Grade grade : grades) {
            CourseGradeDto courseGrade = new CourseGradeDto();
            courseGrade.setCourseId(grade.getCourse().getId());
            courseGrade.setCourseCode(grade.getCourse().getCourseCode());
            courseGrade.setCourseName(grade.getCourse().getName());
            courseGrade.setTotalMark(grade.getTotalMark());
            courseGrade.setRetakeRequired(grade.getRetakeRequired());
            courseGrade.setCoreCourse(grade.getCourse().getCore());
            courseGrade.setGradingPolicyVersion(grade.getGradingPolicyVersion());
            courseGrade.setGrade(grade.getGradeValue());
            courseGrade.setGpaScore(grade.getGpaScore());
            courseGrade.setStatus(grade.getStatus() == null ? "PENDING" : grade.getStatus().name());
            courseGrade.setAttemptNumber(grade.getAttemptNumber());
            courseGrade.setOriginalAttemptId(grade.getOriginalAttempt() == null
                    ? null : grade.getOriginalAttempt().getId());
            courseGrade.setAttemptType(grade.getOriginalAttempt() == null
                    ? "ORIGINAL_ATTEMPT" : "RETAKE_SUBMITTED");
            courseGrade.setAssessmentComponents(grade.getAssessmentMarks().stream()
                    .sorted(Comparator.comparing(mark -> mark.getComponent().getId()))
                    .map(mark -> {
                        com.sis.dto.AssessmentMarkDto component = new com.sis.dto.AssessmentMarkDto();
                        component.setComponentCode(mark.getComponent().getComponentCode());
                        component.setDisplayName(mark.getComponent().getDisplayName());
                        component.setComponentType(mark.getComponent().getComponentType());
                        component.setScore(mark.getScore());
                        component.setMaxScore(mark.getComponent().getMaxScore());
                        component.setWeightPercentage(mark.getComponent().getWeightPercentage());
                        return component;
                    }).toList());
            courseGrade.setSemester(grade.getCourse().getSemester());
            courseGrade.setCredits(grade.getCourse().getCredits());
            courseGrade.setTeacherName(grade.getCourse().getTeacher() != null ? 
                    grade.getCourse().getTeacher().getFullName() : "N/A");

            // Calculate weighted GPA
            if (grade.getGpaScore() != null && grade.getCourse().getCredits() != null) {
                BigDecimal weighted = grade.getGpaScore()
                        .multiply(new BigDecimal(grade.getCourse().getCredits()));
                courseGrade.setWeightedGpa(weighted);
            }

            courseGrades.add(courseGrade);
        }
        report.setCourseGrades(courseGrades);

        // Calculate average grade
        if (hasRetakes) {
            report.setAverageGrade("NOT FINALIZED");
        } else if (!grades.isEmpty()) {
            String avgGrade = calculateAverageGrade(grades);
            report.setAverageGrade(avgGrade);
        } else {
            report.setAverageGrade("N/A");
        }

        // Set generation date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        report.setGeneratedDate(LocalDateTime.now().format(formatter));
        String currentVersion = resultsVersion(gradeRepository.findByStudentIdWithCourse(studentId), studentId);
        report.setResultsVersion(currentVersion);
        Optional<AcademicResultSnapshot> latestSnapshot =
                snapshots.findFirstByStudentIdOrderByApprovedAtDescIdDesc(studentId);
        AcademicResultSnapshot snapshot = latestSnapshot.orElse(null);
        boolean currentApproval = snapshot != null && snapshot.getResultsVersion().equals(currentVersion);
        report.setApproved(currentApproval);
        report.setApprovedBy(currentApproval ? snapshot.getApprovedBy() : null);
        report.setApprovedAt(currentApproval ? formatApprovalTimestamp(snapshot.getApprovedAt()) : null);
        report.setPublished(currentApproval && snapshot.getPublishedAt() != null);
        report.setPublishedBy(currentApproval ? snapshot.getPublishedBy() : null);
        report.setPublishedAt(currentApproval && snapshot.getPublishedAt() != null
                ? formatApprovalTimestamp(snapshot.getPublishedAt()) : null);
        report.setPublicationStatus(publicationStatus(snapshot, currentVersion));

        // Generate performance summary
        String summary = generatePerformanceSummary(student, report);
        report.setPerformanceSummary(summary);

        logger.info("Academic report generated for student: " + student.getFullName());
        return report;
    }

    public AcademicReportDto approveAcademicReport(Long studentId, String approverUsername) {
        List<Grade> submittedGrades = gradeRepository.findByStudentIdWithCourse(studentId);
        if (submittedGrades.isEmpty()) {
            throw new IllegalStateException("Results cannot be approved before any course grades are submitted.");
        }
        if (submittedGrades.stream().anyMatch(grade -> grade.getOriginalAttempt() != null
                || (grade.getAttemptNumber() != null && grade.getAttemptNumber() > 1))) {
            throw new IllegalStateException(
                    "Results containing retake attempts cannot be finalized until the institution-approved retake policy is configured.");
        }
        boolean incompleteReview = submittedGrades.stream()
                .anyMatch(grade -> grade.getStatus() != GradeStatus.APPROVED);
        if (incompleteReview) {
            throw new IllegalStateException("Every submitted grade must be approved before the academic report can be approved.");
        }
        String version = resultsVersion(submittedGrades, studentId);
        Optional<AcademicResultSnapshot> existing =
                snapshots.findByStudentIdAndResultsVersion(studentId, version);
        if (existing.isPresent()) {
            return reportFromSnapshot(existing.get());
        }

        AcademicReportDto report = generateAcademicReport(studentId);
        if (!version.equals(report.getResultsVersion())) {
            throw new IllegalStateException("Results changed while the report was being prepared. Review the latest version and try again.");
        }
        LocalDateTime approvedAt = LocalDateTime.now();
        report.setApproved(Boolean.TRUE);
        report.setApprovedBy(approverUsername);
        report.setApprovedAt(formatApprovalTimestamp(approvedAt));
        report.setPublished(Boolean.FALSE);
        report.setPublishedBy(null);
        report.setPublishedAt(null);
        report.setPublicationStatus("AWAITING_PUBLICATION");
        report.setResultsVersion(version);
        String payload = serializeSnapshot(report);
        snapshots.saveAndFlush(new AcademicResultSnapshot(
                studentId, version, payload, approverUsername, approvedAt));
        approvalRepository.save(new AcademicReportApproval(studentId, approverUsername, approvedAt));
        audit(approverUsername, "ACADEMIC_RESULTS_APPROVED",
                "Student " + studentId + " result version " + version + " approved.");
        logger.info("Academic result version " + version + " approved for student " + studentId
                + " by " + approverUsername);
        return report;
    }

    public AcademicReportDto publishAcademicReport(Long studentId, String resultsVersion, String publisherUsername) {
        AcademicResultSnapshot candidate = snapshots.findByStudentIdAndResultsVersion(studentId, resultsVersion)
                .orElseThrow(() -> new IllegalArgumentException("The approved result version was not found."));
        AcademicResultSnapshot snapshot = snapshots.findByIdForUpdate(candidate.getId())
                .orElseThrow(() -> new IllegalStateException("The approved result version could not be locked."));
        if (snapshot.getApprovedAt() == null) {
            throw new IllegalStateException("Only an approved result version can be published.");
        }
        if (!snapshot.getResultsVersion().equals(resultsVersion(studentId))) {
            throw new IllegalStateException("Results changed after approval. Review and approve the new version before publication.");
        }
        AcademicResultSnapshot latest = snapshots.findFirstByStudentIdOrderByApprovedAtDescIdDesc(studentId)
                .orElseThrow(() -> new IllegalStateException("No approved academic result version exists."));
        if (!latest.getId().equals(snapshot.getId())) {
            throw new IllegalStateException("Only the latest approved result version can be published.");
        }
        boolean newlyPublished = snapshot.getPublishedAt() == null;
        snapshot.publish(publisherUsername, LocalDateTime.now());
        AcademicResultSnapshot published = snapshots.save(snapshot);
        if (newlyPublished) {
            Student student = studentService.getStudentById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
            audit(publisherUsername, "ACADEMIC_RESULTS_PUBLISHED",
                    "Student " + studentId + " result version " + resultsVersion + " published.");
            if (student.getUser() == null || student.getUser().getId() == null) {
                throw new IllegalStateException("Student account is unavailable for result publication notification.");
            }
            notifications.create(student.getUser().getId(), "Academic results published",
                    "Your approved academic results are now available in the student portal.",
                    com.sis.entity.NotificationCategory.GRADE);
            notifyAssignedTeachers(published, student);
        }
        logger.info("Academic result version " + resultsVersion + " published for student " + studentId
                + " by " + publisherUsername);
        return reportFromSnapshot(published);
    }

    private void notifyAssignedTeachers(AcademicResultSnapshot snapshot, Student student) {
        AcademicReportDto report = reportFromSnapshot(snapshot);
        List<CourseGradeDto> courseGrades = report.getCourseGrades();
        if (courseGrades == null) {
            logger.warning("Cannot notify teachers for published result snapshot " + snapshot.getId()
                    + ": the snapshot has no course-grade details.");
            return;
        }
        if (courseGrades.stream().anyMatch(courseGrade -> courseGrade.getCourseId() == null)) {
            throw new IllegalStateException("An approved result references a course without an identifier.");
        }
        List<Long> courseIds = courseGrades.stream()
                .map(CourseGradeDto::getCourseId)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return;
        }

        List<Course> courses = courseRepository.findAllById(courseIds);
        if (courses.size() != courseIds.size()) {
            throw new IllegalStateException("An approved result references a course that is no longer available.");
        }

        Map<Long, LinkedHashSet<String>> courseCodesByTeacherUser = new LinkedHashMap<>();
        for (Course course : courses) {
            if (course.getTeacher() == null || course.getTeacher().getUser() == null
                    || course.getTeacher().getUser().getId() == null) {
                throw new IllegalStateException("An approved result course has no valid teacher account.");
            }
            Long teacherUserId = course.getTeacher().getUser().getId();
            courseCodesByTeacherUser.computeIfAbsent(teacherUserId, ignored -> new LinkedHashSet<>())
                    .add(course.getCourseCode());
        }

        courseCodesByTeacherUser.forEach((teacherUserId, courseCodes) ->
                notifications.create(teacherUserId, "Student results published",
                        "Approved results for " + student.getFullName() + " including "
                                + String.join(", ", courseCodes)
                                + " are now available in the student portal.",
                        com.sis.entity.NotificationCategory.GRADE));
    }

    public TeacherResultPublicationStatusDto getTeacherPublicationStatus(
            Long studentId, String studentName, List<String> courseCodes, Long teacherUserId) {
        List<Grade> grades = gradeRepository.findByStudentIdWithCourse(studentId);
        String currentVersion = resultsVersion(grades, studentId);
        AcademicResultSnapshot snapshot = snapshots.findFirstByStudentIdOrderByApprovedAtDescIdDesc(studentId)
                .orElse(null);

        if (snapshot != null && !snapshot.getResultsVersion().equals(currentVersion)) {
            if (snapshot.getPublishedAt() != null) {
                notifyTeacherOfStatusChange(studentId, studentName, courseCodes, teacherUserId,
                        "INVALIDATED", currentVersion, snapshot.getResultsVersion());
                return new TeacherResultPublicationStatusDto(studentId, studentName, courseCodes, "INVALIDATED",
                        "Results changed after publication and need administrative review.");
            }
            return new TeacherResultPublicationStatusDto(studentId, studentName, courseCodes, "PENDING",
                    "Results changed after approval and need administrative re-approval.");
        }

        boolean hasRetakes = grades.stream().anyMatch(grade -> grade.getOriginalAttempt() != null
                || (grade.getAttemptNumber() != null && grade.getAttemptNumber() > 1));
        boolean hasRejectedGrades = grades.stream().anyMatch(grade -> grade.getStatus() == GradeStatus.REJECTED);
        if (hasRetakes || hasRejectedGrades) {
            notifyTeacherOfStatusChange(studentId, studentName, courseCodes, teacherUserId,
                    "BLOCKED", currentVersion, snapshot == null ? null : snapshot.getResultsVersion());
            String message = hasRetakes
                    ? "Retake results cannot be finalized until the institutional retake policy is configured."
                    : "One or more grades were returned and require resolution before results can proceed.";
            return new TeacherResultPublicationStatusDto(
                    studentId, studentName, courseCodes, "BLOCKED", message);
        }

        boolean hasUnapprovedGrades = grades.stream()
                .anyMatch(grade -> grade.getStatus() != GradeStatus.APPROVED);
        if (snapshot == null) {
            String message = hasUnapprovedGrades
                    ? "Waiting for all submitted grades to be approved."
                    : "Grades are approved and waiting for administrative result approval.";
            return new TeacherResultPublicationStatusDto(
                    studentId, studentName, courseCodes, "PENDING", message);
        }

        if (snapshot.getPublishedAt() == null) {
            return new TeacherResultPublicationStatusDto(
                    studentId, studentName, courseCodes, "AWAITING_PUBLICATION",
                    "The current result version is approved and awaiting publication.");
        }
        return new TeacherResultPublicationStatusDto(
                studentId, studentName, courseCodes, "PUBLISHED",
                "The current result version has been published.");
    }

    private void notifyTeacherOfStatusChange(Long studentId, String studentName, List<String> courseCodes,
                                             Long teacherUserId, String status, String currentVersion,
                                             String approvedVersion) {
        if (teacherUserId == null) {
            throw new IllegalStateException("Teacher account is unavailable for result status notification.");
        }
        studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
        String eventKey = statusNotificationEventKey(
                studentId, teacherUserId, status, currentVersion, approvedVersion);
        if (statusNotificationEvents.existsById(eventKey)) {
            return;
        }

        statusNotificationEvents.saveAndFlush(new TeacherResultStatusNotificationEvent(
                eventKey, studentId, teacherUserId, status));
        String title = "BLOCKED".equals(status)
                ? "Student results blocked" : "Published results invalidated";
        String message = "BLOCKED".equals(status)
                ? "Results for " + studentName + " in " + String.join(", ", courseCodes)
                        + " are blocked and require resolution in the portal."
                : "Published results for " + studentName + " in " + String.join(", ", courseCodes)
                        + " changed and require administrative review.";
        notifications.create(teacherUserId, title, message, com.sis.entity.NotificationCategory.GRADE);
    }

    private String statusNotificationEventKey(Long studentId, Long teacherUserId, String status,
                                              String currentVersion, String approvedVersion) {
        String canonical = studentId + "|" + teacherUserId + "|" + status + "|"
                + currentVersion + "|" + (approvedVersion == null ? "" : approvedVersion);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable for result notification deduplication.", ex);
        }
    }

    public boolean isOfficiallyApproved(Long studentId) {
        return currentApprovedSnapshot(studentId).isPresent();
    }

    public boolean isOfficiallyPublished(Long studentId) {
        return currentApprovedSnapshot(studentId)
                .map(snapshot -> snapshot.getPublishedAt() != null)
                .orElse(false);
    }

    public AcademicReportDto getPublishedAcademicReport(Long studentId) {
        AcademicResultSnapshot snapshot = currentApprovedSnapshot(studentId)
                .filter(item -> item.getPublishedAt() != null)
                .orElseThrow(() -> new IllegalStateException("Final academic results have not been published."));
        return reportFromSnapshot(snapshot);
    }

    public AcademicReportDto getStudentVisibleReport(Long studentId) {
        if (isOfficiallyPublished(studentId)) {
            return getPublishedAcademicReport(studentId);
        }
        AcademicReportDto report = generateAcademicReport(studentId);
        report.setCgpa(null);
        report.setAwardClassification(null);
        report.setCreditsEarned(null);
        report.setLatestSemester(null);
        report.setSemesterGpa(null);
        report.setAcademicStandingStatus("NOT_FINALIZED");
        report.setAverageGrade(null);
        report.setCourseGrades(List.of());
        report.setTotalCourses(null);
        report.setPerformanceSummary("Final results are not yet available. They will be shown after administrative approval and publication.");
        report.setPublished(Boolean.FALSE);
        report.setPublishedBy(null);
        report.setPublishedAt(null);
        report.setPublicationStatus(Boolean.TRUE.equals(report.getApproved())
                ? "AWAITING_PUBLICATION" : report.getPublicationStatus());
        return report;
    }

    public AcademicReportDto getApprovalStatus(Long studentId) {
        return generateAcademicReport(studentId);
    }

    private Optional<AcademicResultSnapshot> currentApprovedSnapshot(Long studentId) {
        Optional<AcademicResultSnapshot> snapshot =
                snapshots.findFirstByStudentIdOrderByApprovedAtDescIdDesc(studentId);
        if (snapshot.isEmpty()) return Optional.empty();
        return snapshot.filter(item -> item.getResultsVersion().equals(resultsVersion(studentId)));
    }

    private String publicationStatus(AcademicResultSnapshot snapshot, String currentVersion) {
        if (snapshot == null) return "PENDING";
        if (!snapshot.getResultsVersion().equals(currentVersion)) return "CHANGES_REQUIRED";
        return snapshot.getPublishedAt() == null ? "AWAITING_PUBLICATION" : "PUBLISHED";
    }

    private String resultsVersion(Long studentId) {
        return resultsVersion(gradeRepository.findByStudentIdWithCourse(studentId), studentId);
    }

    private String resultsVersion(List<Grade> grades, Long studentId) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("grading-policy=").append(UgandanTertiaryGradingPolicy.VERSION).append('|')
                .append("award-type=").append(currentAwardType(studentId)).append('\n');
        grades.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(Grade::getId, Comparator.nullsFirst(Long::compareTo)))
                .forEach(grade -> canonical.append(grade.getId()).append('|')
                        .append(grade.getStudent() == null ? null : grade.getStudent().getId()).append('|')
                        .append(grade.getCourse() == null ? null : grade.getCourse().getId()).append('|')
                        .append(grade.getCourse() == null ? null : grade.getCourse().getCourseCode()).append('|')
                        .append(grade.getCourse() == null ? null : grade.getCourse().getSemester()).append('|')
                        .append(grade.getCourse() == null ? null : grade.getCourse().getCredits()).append('|')
                        .append(grade.getGradeValue()).append('|')
                        .append(grade.getGpaScore()).append('|')
                        .append(grade.getGradingPolicyVersion()).append('|')
                        .append(grade.getRetakeRequired()).append('|')
                        .append(grade.getCourse() == null ? null : grade.getCourse().getCore()).append('|')
                        .append(grade.getTotalMark()).append('|')
                        .append(grade.getStatus()).append('|')
                        .append(grade.getRemarks()).append('|')
                        .append(grade.getSubmittedAt()).append('|')
                        .append(grade.getApprovedBy()).append('|')
                        .append(grade.getApprovedAt()).append('|')
                        .append(assessmentMarksVersion(grade)).append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable for academic result versioning.", ex);
        }
    }

    private String currentAwardType(Long studentId) {
        if (studentPrograms == null) return "UNCONFIGURED";
        return studentPrograms.findByStudentIdOrderByStartDateDesc(studentId).stream()
                .map(StudentProgram::getProgram)
                .filter(java.util.Objects::nonNull)
                .map(program -> program.getAwardType() == null ? "UNCONFIGURED" : program.getAwardType().name())
                .findFirst()
                .orElse("UNCONFIGURED");
    }

    private String assessmentMarksVersion(Grade grade) {
        if (grade.getAssessmentMarks() == null || grade.getAssessmentMarks().isEmpty()) return "";
        StringBuilder canonical = new StringBuilder();
        grade.getAssessmentMarks().stream()
                .sorted(Comparator.comparing(mark -> mark.getComponent().getId()))
                .forEach(mark -> canonical.append(mark.getComponent().getId()).append(':')
                        .append(mark.getComponent().getWeightPercentage()).append(':')
                        .append(mark.getComponent().getMaxScore()).append(':')
                        .append(mark.getScore()).append('|'));
        return canonical.toString();
    }

    private String serializeSnapshot(AcademicReportDto report) {
        try {
            return SNAPSHOT_MAPPER.writeValueAsString(report);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to preserve the approved academic result snapshot.", ex);
        }
    }

    private AcademicReportDto reportFromSnapshot(AcademicResultSnapshot snapshot) {
        try {
            AcademicReportDto report = SNAPSHOT_MAPPER.readValue(snapshot.getSnapshotPayload(), AcademicReportDto.class);
            report.setApproved(Boolean.TRUE);
            report.setApprovedBy(snapshot.getApprovedBy());
            report.setApprovedAt(formatApprovalTimestamp(snapshot.getApprovedAt()));
            report.setPublished(snapshot.getPublishedAt() != null);
            report.setPublishedBy(snapshot.getPublishedBy());
            report.setPublishedAt(snapshot.getPublishedAt() == null
                    ? null : formatApprovalTimestamp(snapshot.getPublishedAt()));
            report.setPublicationStatus(snapshot.getPublishedAt() == null
                    ? "AWAITING_PUBLICATION" : "PUBLISHED");
            report.setResultsVersion(snapshot.getResultsVersion());
            if (report.getCreditsEarned() == null || report.getSemesterGpa() == null
                    || report.getLatestSemester() == null || report.getAcademicStandingStatus() == null) {
                List<Grade> approvedGrades = gradeRepository.findByStudentIdAndStatus(
                        snapshot.getStudentId(), GradeStatus.APPROVED);
                boolean hasRetakes = approvedGrades.stream().anyMatch(grade -> grade.getOriginalAttempt() != null
                        || (grade.getAttemptNumber() != null && grade.getAttemptNumber() > 1));
                if (!hasRetakes) {
                    if (report.getCreditsEarned() == null && !approvedGrades.isEmpty()) {
                        report.setCreditsEarned(GradeCalculationService.calculateCreditsEarned(approvedGrades));
                    }
                    if (report.getLatestSemester() == null) {
                        report.setLatestSemester(GradeCalculationService.latestApprovedSemester(approvedGrades));
                    }
                    if (report.getSemesterGpa() == null && !approvedGrades.isEmpty()) {
                        report.setSemesterGpa(GradeCalculationService.calculateLatestSemesterGpa(approvedGrades));
                    }
                    if (report.getAcademicStandingStatus() == null) {
                        report.setAcademicStandingStatus(report.getCgpa() == null
                                ? "NOT_FINALIZED"
                                : GradeCalculationService.academicStanding(report.getCgpa(), approvedGrades,
                                        gradeRepository.findCoreCourseIdsByStudentId(snapshot.getStudentId())));
                    }
                } else if (report.getAcademicStandingStatus() == null) {
                    report.setAcademicStandingStatus("NOT_FINALIZED");
                }
            }
            return report;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("The preserved academic result snapshot could not be read.", ex);
        }
    }

    private void audit(String username, String action, String details) {
        Long userId = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        auditLogs.save(new AuditLog(userId, action, details));
    }

    private String formatApprovalTimestamp(LocalDateTime timestamp) {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Calculate average grade from list of grades
     * @param grades list of grade objects
     * @return average grade letter
     */
    private String calculateAverageGrade(List<Grade> grades) {
        if (grades.isEmpty()) {
            return "N/A";
        }

        BigDecimal totalGpa = BigDecimal.ZERO;
        for (Grade grade : grades) {
            if (grade.getGpaScore() != null) {
                totalGpa = totalGpa.add(grade.getGpaScore());
            }
        }

        BigDecimal avgGpa = totalGpa.divide(new BigDecimal(grades.size()), 2, RoundingMode.HALF_UP);

        // Convert GPA back to letter
        if (avgGpa.compareTo(new BigDecimal("5.0")) >= 0) return "A";
        if (avgGpa.compareTo(new BigDecimal("4.5")) >= 0) return "B+";
        if (avgGpa.compareTo(new BigDecimal("4.0")) >= 0) return "B";
        if (avgGpa.compareTo(new BigDecimal("3.5")) >= 0) return "C+";
        if (avgGpa.compareTo(new BigDecimal("3.0")) >= 0) return "C";
        if (avgGpa.compareTo(new BigDecimal("2.5")) >= 0) return "D+";
        if (avgGpa.compareTo(new BigDecimal("2.0")) >= 0) return "D";
        return "F";
    }

    /**
     * Generate performance summary text
     * @param student the student
     * @param report the academic report
     * @return performance summary
     */
    private String generatePerformanceSummary(Student student, AcademicReportDto report) {
        StringBuilder summary = new StringBuilder();

        BigDecimal cgpa = report.getCgpa();
        summary.append("Student Performance Summary for ").append(student.getFullName()).append("\n");
        summary.append("==========================================\n");
        summary.append("Total Courses: ").append(report.getTotalCourses()).append("\n");
        if (cgpa == null) {
            summary.append("Performance Level: Not available (no approved grades)\n");
            summary.append("Current CGPA: Not available\n");
            return summary.toString();
        }

        summary.append("Performance Level: ").append(getPerformanceLevel(cgpa)).append("\n");
        summary.append("Current CGPA: ").append(cgpa).append("\n");

        if (cgpa.compareTo(new BigDecimal("4.40")) >= 0) {
            summary.append("Status: First Class / Distinction CGPA range.\n");
        } else if (cgpa.compareTo(new BigDecimal("3.60")) >= 0) {
            summary.append("Status: Upper Division / Class II Upper CGPA range.\n");
        } else if (cgpa.compareTo(new BigDecimal("2.80")) >= 0) {
            summary.append("Status: Lower Division / Class II Lower CGPA range.\n");
        } else if (cgpa.compareTo(new BigDecimal("2.00")) >= 0) {
            summary.append("Status: Pass CGPA range.\n");
        } else {
            summary.append("Status: Below the minimum award CGPA.\n");
        }

        return summary.toString();
    }

    /**
     * Get performance level based on CGPA
     * @param cgpa cumulative GPA
     * @return performance level
     */
    private String getPerformanceLevel(BigDecimal cgpa) {
        if (cgpa.compareTo(new BigDecimal("4.40")) >= 0) return "First Class / Distinction range";
        if (cgpa.compareTo(new BigDecimal("3.60")) >= 0) return "Upper Division range";
        if (cgpa.compareTo(new BigDecimal("2.80")) >= 0) return "Lower Division range";
        if (cgpa.compareTo(new BigDecimal("2.00")) >= 0) return "Pass range";
        return "Below minimum award threshold";
    }

    /**
     * Get class-wise academic statistics
     * @param className class name
     * @return statistics for the class
     */
    public String getClassStatistics(String className) {
        List<Student> students = studentRepository.findByClassName(className);
        List<Map.Entry<Student, BigDecimal>> gradedStudents = students.stream()
            .map(student -> {
                BigDecimal cgpa = approvedCgpa(student.getId());
                return cgpa == null ? null : Map.entry(student, cgpa);
            })
            .filter(java.util.Objects::nonNull)
            .toList();
        BigDecimal averageCgpa = average(gradedStudents.stream().map(Map.Entry::getValue).toList());
        Map.Entry<Student, BigDecimal> topper = gradedStudents.stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);
        StringBuilder stats = new StringBuilder();
        stats.append("Class Statistics for ").append(className).append("\n");
        stats.append("Total Students: ").append(students.size()).append("\n");
        stats.append("Students with approved grades: ").append(gradedStudents.size()).append("\n");
        stats.append("Average CGPA: ").append(averageCgpa == null ? "N/A" : averageCgpa).append("\n");
        stats.append("Topper: ").append(topper == null ? "N/A"
                : topper.getKey().getFullName() + " (" + topper.getValue() + ")").append("\n");
        return stats.toString();
    }

    /**
     * Get teacher performance statistics
     * @param teacherId teacher ID
     * @return statistics for teacher
     */
    public String getTeacherStatistics(Long teacherId) {
        var teacher = teacherRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with ID: " + teacherId));
        List<Student> students = teacher.getCourses().stream()
            .flatMap(course -> course.getStudents().stream())
            .distinct()
            .toList();
        BigDecimal averageCgpa = average(students.stream()
            .map(student -> approvedCgpa(student.getId()))
            .filter(java.util.Objects::nonNull)
            .toList());
        StringBuilder stats = new StringBuilder();
        stats.append("Teacher Performance Statistics\n");
        stats.append("Teacher: ").append(teacher.getFullName()).append("\n");
        stats.append("Total Students Taught: ").append(students.size()).append("\n");
        stats.append("Average Student Performance: ").append(averageCgpa == null ? "N/A" : averageCgpa).append("\n");
        stats.append("Courses Taught: ").append(teacher.getCourses().size()).append("\n");
        return stats.toString();
    }

    /**
     * Generate comparison report between students
     * @param studentId1 first student ID
     * @param studentId2 second student ID
     * @return comparison report
     */
    public String generateComparisonReport(Long studentId1, Long studentId2) {
        AcademicReportDto report1 = generateAcademicReport(studentId1);
        AcademicReportDto report2 = generateAcademicReport(studentId2);

        StringBuilder comparison = new StringBuilder();
        comparison.append("Academic Comparison Report\n");
        comparison.append("==========================================\n");
        comparison.append("Student 1: ").append(report1.getStudentName()).append(" (CGPA: ")
                .append(report1.getCgpa() == null ? "Not available" : report1.getCgpa()).append(")\n");
        comparison.append("Student 2: ").append(report2.getStudentName()).append(" (CGPA: ")
                .append(report2.getCgpa() == null ? "Not available" : report2.getCgpa()).append(")\n");

        if (report1.getCgpa() == null || report2.getCgpa() == null) {
            comparison.append("\nCGPA comparison is unavailable until both students have approved grades.\n");
        } else if (report1.getCgpa().compareTo(report2.getCgpa()) > 0) {
            comparison.append("\nStudent 1 has higher CGPA.\n");
        } else if (report1.getCgpa().compareTo(report2.getCgpa()) < 0) {
            comparison.append("\nStudent 2 has higher CGPA.\n");
        } else {
            comparison.append("\nBoth students have equal CGPA.\n");
        }

        return comparison.toString();
    }

    /**
     * Get top performing students in class
     * @param className class name
     * @param limit number of top students
     * @return list of top students
     */
    public String getTopStudents(String className, int limit) {
        int resultLimit = Math.max(1, Math.min(limit, 100));
        List<Student> classStudents = studentRepository.findByClassName(className);
        List<Map.Entry<Student, BigDecimal>> students = classStudents.stream()
                .map(student -> {
                    BigDecimal cgpa = approvedCgpa(student.getId());
                    return cgpa == null ? null : Map.entry(student, cgpa);
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Map.Entry.<Student, BigDecimal>comparingByValue().reversed())
                .limit(resultLimit)
                .toList();
        StringBuilder topStudents = new StringBuilder();
        topStudents.append("Top ").append(resultLimit).append(" Students in ").append(className).append("\n");
        topStudents.append("==========================================\n");
        if (students.isEmpty()) {
            topStudents.append(classStudents.isEmpty() ? "No students found.\n" : "No approved grades available.\n");
        } else {
            for (int index = 0; index < students.size(); index++) {
                Map.Entry<Student, BigDecimal> student = students.get(index);
                topStudents.append(index + 1).append(". ").append(student.getKey().getFullName())
                        .append(" - CGPA: ").append(student.getValue()).append("\n");
            }
        }
        return topStudents.toString();
    }

    private BigDecimal approvedCgpa(Long studentId) {
        List<Grade> approved = gradeRepository.findByStudentIdAndStatus(studentId, GradeStatus.APPROVED);
        return approved.isEmpty() ? null : GradeCalculationService.calculateApprovedCgpa(approved);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
