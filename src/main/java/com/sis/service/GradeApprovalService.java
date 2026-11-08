package com.sis.service;

import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.NotificationCategory;
import com.sis.repository.GradeRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.entity.AuditLog;
import com.sis.repository.UserRepository;
import com.sis.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.logging.Logger;

@Service
@Transactional
public class GradeApprovalService {

    private static final Logger LOGGER = Logger.getLogger(GradeApprovalService.class.getName());
    private final GradeRepository gradeRepository;
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;
    private final AcademicResultCorrectionService resultCorrections;
    private final com.sis.repository.CourseAssessmentComponentRepository assessmentComponents;
    private final com.sis.repository.GradeAssessmentMarkRepository assessmentMarks;
    private final AssessmentCalculationService assessmentCalculations;
    private final NotificationService notifications;
    private final EmailService emailService;

    public GradeApprovalService(GradeRepository gradeRepository,
                                 StudentRepository studentRepository,
                                 StudentService studentService,
                                 AuditLogRepository auditLogs,
                                 UserRepository users,
                                 NotificationService notifications) {
        this(gradeRepository, studentRepository, studentService, auditLogs, users, notifications, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GradeApprovalService(GradeRepository gradeRepository,
                                 StudentRepository studentRepository,
                                 StudentService studentService,
                                 AuditLogRepository auditLogs,
                                 UserRepository users,
                                 NotificationService notifications,
                                 AcademicResultCorrectionService resultCorrections,
                                 com.sis.repository.CourseAssessmentComponentRepository assessmentComponents,
                                 com.sis.repository.GradeAssessmentMarkRepository assessmentMarks,
                                 AssessmentCalculationService assessmentCalculations,
                                 EmailService emailService) {
        this.gradeRepository = gradeRepository;
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.auditLogs = auditLogs;
        this.users = users;
        this.resultCorrections = resultCorrections;
        this.assessmentComponents = assessmentComponents;
        this.assessmentMarks = assessmentMarks;
        this.assessmentCalculations = assessmentCalculations;
        this.notifications = notifications;
        this.emailService = emailService;
    }

    public GradeApprovalService(GradeRepository gradeRepository,
                                StudentRepository studentRepository,
                                StudentService studentService,
                                AuditLogRepository auditLogs,
                                UserRepository users,
                                NotificationService notifications,
                                AcademicResultCorrectionService resultCorrections) {
        this(gradeRepository, studentRepository, studentService, auditLogs, users, notifications,
                resultCorrections, null, null, null, null);
    }

    public GradeApprovalService(GradeRepository gradeRepository,
                                StudentRepository studentRepository,
                                StudentService studentService,
                                AuditLogRepository auditLogs,
                                UserRepository users,
                                NotificationService notifications,
                                AcademicResultCorrectionService resultCorrections,
                                com.sis.repository.CourseAssessmentComponentRepository assessmentComponents,
                                com.sis.repository.GradeAssessmentMarkRepository assessmentMarks,
                                AssessmentCalculationService assessmentCalculations) {
        this(gradeRepository, studentRepository, studentService, auditLogs, users, notifications,
                resultCorrections, assessmentComponents, assessmentMarks, assessmentCalculations, null);
    }

    public Grade submitForApproval(Grade grade, String submittedBy) {
        if (grade.getId() != null) {
            Long gradeId = grade.getId();
            grade = gradeRepository.findByIdForUpdate(grade.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        }
        if (grade.getStatus() == GradeStatus.APPROVED) {
            throw new IllegalStateException(
                    "Approved grades cannot be resubmitted directly. Use the controlled correction workflow.");
        }
        validateAssessmentMarks(grade);
        validateGradeAgainstInstitutionalPolicy(grade);
        grade.setSubmittedBy(submittedBy);
        grade.setSubmittedAt(LocalDateTime.now());
        grade.setStatus(GradeStatus.PENDING);
        grade.setApprovedBy(null);
        grade.setRejectedBy(null);
        grade.setRejectionReason(null);
        grade.setApprovedAt(null);
        grade.setRejectedAt(null);
        clearAssessmentReview(grade);
        Grade saved = gradeRepository.save(grade);
        if (resultCorrections != null) resultCorrections.markSubmitted(saved.getId(), submittedBy);
        audit(submittedBy, "GRADE_SUBMITTED_FOR_APPROVAL", "Grade #" + saved.getId() + " submitted for approval");
        return reloadGradeForResponse(saved);
    }

    public void acknowledgeAssessmentReview(Long gradeId, String reviewedBy) {
        Grade grade = gradeRepository.findByIdForUpdate(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        if (grade.getStatus() != GradeStatus.PENDING) {
            throw new IllegalStateException("Only pending grades can be reviewed.");
        }
        validateAssessmentMarks(grade);
        validateGradeAgainstInstitutionalPolicy(grade);
        String fingerprint = assessmentReviewFingerprint(grade);
        grade.setAssessmentReviewFingerprint(fingerprint);
        grade.setAssessmentReviewedBy(reviewedBy);
        grade.setAssessmentReviewedAt(LocalDateTime.now());
        gradeRepository.save(grade);
        audit(reviewedBy, "GRADE_ASSESSMENT_REVIEWED",
                "Assessment result state reviewed for grade #" + gradeId + " (fingerprint " + fingerprint + ")");
    }

    public Grade approve(Long gradeId, String approvedBy) {
        Grade grade = gradeRepository.findByIdForUpdate(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        if (grade.getStatus() != GradeStatus.PENDING) {
            throw new IllegalStateException("Only pending grades can be approved. Current status: " + grade.getStatus());
        }
        validateAssessmentMarks(grade);
        validateGradeAgainstInstitutionalPolicy(grade);
        String currentFingerprint = assessmentReviewFingerprint(grade);
        if (grade.getAssessmentReviewFingerprint() == null
                || !grade.getAssessmentReviewFingerprint().equals(currentFingerprint)) {
            throw new IllegalStateException(
                    "Assessment results changed after review. Review the current marks before approving.");
        }
        grade.setStatus(GradeStatus.APPROVED);
        grade.setApprovedBy(approvedBy);
        grade.setApprovedAt(LocalDateTime.now());
        grade.setRejectedBy(null);
        grade.setRejectionReason(null);
        grade.setRejectedAt(null);
        Grade saved = gradeRepository.save(grade);
        if (resultCorrections != null) resultCorrections.markApproved(saved.getId(), approvedBy);
        studentService.calculateCGPA(saved.getStudent().getId());
        audit(approvedBy, "GRADE_APPROVED", "Grade #" + saved.getId() + " approved");
        notifyStudent(saved, true, null);
        return reloadGradeForResponse(saved);
    }

    private String assessmentReviewFingerprint(Grade grade) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for result review integrity.", exception);
        }
        addFingerprintValue(digest, grade.getId());
        addFingerprintValue(digest, grade.getStudent() == null ? null : grade.getStudent().getId());
        addFingerprintValue(digest, grade.getCourse() == null ? null : grade.getCourse().getId());
        addFingerprintValue(digest, grade.getAttemptNumber());
        addFingerprintValue(digest, grade.getOriginalAttempt() == null ? null : grade.getOriginalAttempt().getId());
        addFingerprintValue(digest, grade.getTotalMark());
        addFingerprintValue(digest, grade.getGradeValue());
        addFingerprintValue(digest, grade.getGpaScore());
        addFingerprintValue(digest, grade.getGradingPolicyVersion());
        addFingerprintValue(digest, grade.getRetakeRequired());
        addFingerprintValue(digest, grade.getRemarks());

        java.util.List<com.sis.entity.CourseAssessmentComponent> components =
                assessmentComponents.findByCourseIdOrderByIdAsc(grade.getCourse().getId());
        java.util.Map<Long, com.sis.entity.GradeAssessmentMark> marksByComponent =
                assessmentMarks.findByGradeId(grade.getId()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                mark -> mark.getComponent().getId(), mark -> mark));
        java.util.Set<Long> knownComponentIds = components.stream()
                .map(com.sis.entity.CourseAssessmentComponent::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!knownComponentIds.containsAll(marksByComponent.keySet())) {
            throw new IllegalStateException("Assessment marks contain a component outside the configured structure.");
        }
        for (com.sis.entity.CourseAssessmentComponent component : components) {
            addFingerprintValue(digest, component.getId());
            addFingerprintValue(digest, component.getComponentCode());
            addFingerprintValue(digest, component.getDisplayName());
            addFingerprintValue(digest, component.getComponentType());
            addFingerprintValue(digest, component.getWeightPercentage());
            addFingerprintValue(digest, component.getMaxScore());
            addFingerprintValue(digest, component.getRequired());
            com.sis.entity.GradeAssessmentMark mark = marksByComponent.get(component.getId());
            addFingerprintValue(digest, mark == null ? null : mark.getScore());
            addFingerprintValue(digest, mark == null ? null : mark.getRemarks());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void addFingerprintValue(MessageDigest digest, Object value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void clearAssessmentReview(Grade grade) {
        grade.setAssessmentReviewFingerprint(null);
        grade.setAssessmentReviewedBy(null);
        grade.setAssessmentReviewedAt(null);
    }

    private void validateAssessmentMarks(Grade grade) {
        if (assessmentComponents == null || assessmentMarks == null || assessmentCalculations == null) {
            throw new IllegalStateException("Assessment validation is unavailable; results cannot be approved.");
        }
        java.util.List<com.sis.entity.CourseAssessmentComponent> components =
                assessmentComponents.findByCourseIdOrderByIdAsc(grade.getCourse().getId());
        if (components.isEmpty()) {
            throw new IllegalStateException("An administrator must configure the course assessment structure first.");
        }
        java.util.Map<Long, com.sis.entity.GradeAssessmentMark> marksByComponent =
                assessmentMarks.findByGradeId(grade.getId()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                mark -> mark.getComponent().getId(), mark -> mark));
        java.util.List<AssessmentCalculationService.ComponentMark> calculationInput = new java.util.ArrayList<>();
        for (com.sis.entity.CourseAssessmentComponent component : components) {
            com.sis.entity.GradeAssessmentMark mark = marksByComponent.get(component.getId());
            if (mark == null && Boolean.TRUE.equals(component.getRequired())) {
                throw new IllegalStateException(
                        "Required assessment marks are missing for " + component.getDisplayName());
            }
            if (mark == null) continue;
            calculationInput.add(new AssessmentCalculationService.ComponentMark(
                    component.getWeightPercentage(), component.getMaxScore(), mark.getScore()));
        }
        AssessmentCalculationService.AssessmentTotal total = assessmentCalculations.calculateTotal(calculationInput);
        if (!total.complete()) {
            throw new IllegalStateException("Missing assessment marks must be resolved before grade approval.");
        }
        if (grade.getTotalMark() == null || grade.getTotalMark().compareTo(total.weightedMark()) != 0) {
            throw new IllegalStateException("The submitted total mark does not match the course assessment components.");
        }
        validateGradeAgainstMark(grade, total.weightedMark());
    }

    private void validateGradeAgainstInstitutionalPolicy(Grade grade) {
        if (!UgandanTertiaryGradingPolicy.VERSION.equals(grade.getGradingPolicyVersion())) {
            throw new IllegalStateException("Legacy grading records must be revalidated before approval.");
        }
        BigDecimal expected = GradeCalculationService.gradeToGpa(grade.getGradeValue());
        if (grade.getGpaScore() == null || grade.getGpaScore().compareTo(expected) != 0) {
            throw new IllegalStateException("Grade points do not match the institutional grading policy.");
        }
        boolean retakeRequired = expected.compareTo(UgandanTertiaryGradingPolicy.PASS_GRADE_POINT) < 0;
        if (!Boolean.valueOf(retakeRequired).equals(grade.getRetakeRequired())) {
            throw new IllegalStateException("Retake status does not match the minimum passing grade.");
        }
        if (grade.getTotalMark() != null) validateGradeAgainstMark(grade, grade.getTotalMark());
    }

    private void validateGradeAgainstMark(Grade grade, BigDecimal mark) {
        String expectedGrade = UgandanTertiaryGradingPolicy.gradeForMark(mark).letterGrade();
        if (!expectedGrade.equals(grade.getGradeValue())) {
            throw new IllegalStateException("Grade does not match the submitted percentage mark.");
        }
    }

    public Grade reject(Long gradeId, String rejectedBy, String reason) {
        Grade grade = gradeRepository.findByIdForUpdate(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        if (grade.getStatus() != GradeStatus.PENDING) {
            throw new IllegalStateException("Only pending grades can be rejected. Current status: " + grade.getStatus());
        }
        grade.setStatus(GradeStatus.REJECTED);
        grade.setRejectedBy(rejectedBy);
        grade.setRejectedAt(LocalDateTime.now());
        grade.setRejectionReason(reason);
        grade.setApprovedBy(null);
        grade.setApprovedAt(null);
        Grade saved = gradeRepository.save(grade);
        if (resultCorrections != null) resultCorrections.markRejected(saved.getId(), rejectedBy);
        studentService.calculateCGPA(saved.getStudent().getId());
        audit(rejectedBy, "GRADE_REJECTED", "Grade #" + saved.getId() + " rejected");
        notifySubmittingTeacher(saved, reason);
        notifyStudent(saved, false, reason);
        return reloadGradeForResponse(saved);
    }

    private void notifySubmittingTeacher(Grade grade, String reason) {
        if (notifications == null || grade.getSubmittedBy() == null || grade.getSubmittedBy().isBlank()) {
            return;
        }
        users.findByUsernameNormalized(grade.getSubmittedBy()).ifPresent(user -> {
            if (user.getId() != null) {
                notifications.create(user.getId(), "Grade needs revision",
                        "Grade #" + grade.getId() + " for "
                                + grade.getCourse().getName() + " was rejected: " + reason,
                        NotificationCategory.GRADE);
            }
        });
    }

    private void notifyStudent(Grade grade, boolean approved, String reason) {
        if (grade.getStudent() == null || grade.getStudent().getUser() == null
                || grade.getCourse() == null) {
            return;
        }
        Long userId = grade.getStudent().getUser().getId();
        if (userId != null && notifications != null) {
            String title = approved ? "Grade approved" : "Grade needs revision";
            String message = approved
                    ? "Your grade for " + grade.getCourse().getName() + " has been approved."
                    : "Your grade submission for " + grade.getCourse().getName() + " was rejected: " + reason;
            notifications.create(userId, title, message, NotificationCategory.GRADE);
        }
        if (emailService == null || notifications == null || userId == null
                || !notifications.emailNotificationsEnabled(userId) || grade.getStudent().getEmail() == null) {
            return;
        }
        try {
            if (approved) {
                emailService.sendGradeNotification(
                        grade.getStudent().getEmail(),
                        grade.getStudent().getFullName(),
                        grade.getCourse().getName(),
                        grade.getGradeValue());
                if (grade.getStudent().getCgpa() != null) {
                    emailService.sendCGPAUpdateNotification(
                            grade.getStudent().getEmail(),
                            grade.getStudent().getFullName(),
                            grade.getStudent().getCgpa().toPlainString());
                }
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            LOGGER.warning("Academic notification email was not delivered: " + ex.getMessage());
        }
    }

    private Grade reloadGradeForResponse(Grade grade) {
        return gradeRepository.findByIdWithStudentUserAndCourse(grade.getId())
                .orElseThrow(() -> new IllegalStateException("Saved grade could not be reloaded."));
    }

    private void audit(String username, String action, String details) {
        Long userId = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        auditLogs.save(new AuditLog(userId, action, details));
    }
}
