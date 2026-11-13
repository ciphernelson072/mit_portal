package com.sis.service;

import com.sis.dto.AdmissionApplicationRequest;
import com.sis.dto.AdmissionDecisionRequest;
import com.sis.dto.AdmissionEnrollmentCredentialsResponse;
import com.sis.dto.AdmissionStatusRequest;
import com.sis.dto.AdminStudentDto;
import com.sis.dto.CreateAdmittedStudentRequest;
import com.sis.dto.CreateStudentRequest;
import com.sis.dto.EnrollAdmissionRequest;
import com.sis.entity.AcademicYear;
import com.sis.entity.AdmissionApplication;
import com.sis.entity.AdmissionApplicationStatus;
import com.sis.entity.Program;
import com.sis.entity.Student;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.entity.StudentStatusHistory;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.AdmissionApplicationRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.StudentStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class AdmissionService {
    private final AdmissionApplicationRepository applications;
    private final ProgramRepository programs;
    private final AcademicYearRepository academicYears;
    private final StudentRepository students;
    private final StudentProgramRepository studentPrograms;
    private final StudentStatusHistoryRepository statusHistory;
    private final AdminService adminService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdmissionService(AdmissionApplicationRepository applications, ProgramRepository programs,
                            AcademicYearRepository academicYears, StudentRepository students,
                            StudentProgramRepository studentPrograms, StudentStatusHistoryRepository statusHistory,
                            AdminService adminService) {
        this.applications = applications;
        this.programs = programs;
        this.academicYears = academicYears;
        this.students = students;
        this.studentPrograms = studentPrograms;
        this.statusHistory = statusHistory;
        this.adminService = adminService;
    }

    public AdmissionApplication submit(AdmissionApplicationRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        AcademicYear year = academicYears.findById(request.getAcademicYearId())
                .orElseThrow(() -> new IllegalArgumentException("Academic year not found"));
        if (applications.existsByNormalizedEmailAndAcademicYearId(normalizedEmail, year.getId())) {
            throw new IllegalStateException("An application already exists for this email and academic year");
        }
        Program program = programs.findById(request.getProgramId())
                .filter(item -> programs.existsActiveInDirectory(item.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Active program not found"));

        AdmissionApplication application = new AdmissionApplication();
        application.setApplicationNumber("APP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        application.setApplicantName(request.getApplicantName());
        application.setEmail(normalizedEmail);
        application.setNormalizedEmail(normalizedEmail);
        application.setPhone(request.getPhone());
        application.setProgram(program);
        application.setAcademicYear(year);
        return applications.save(application);
    }

    @Transactional(readOnly = true)
    public AdmissionApplication findStatus(AdmissionStatusRequest request) {
        return applications.findByApplicationNumberAndEmail(request.getApplicationNumber(), request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Application number or email address is incorrect"));
    }

    @Transactional(readOnly = true)
    public List<AdmissionApplication> list(AdmissionApplicationStatus status) {
        return status == null ? applications.findAllWithProgramAndAcademicYear()
                : applications.findByStatusOrderBySubmittedAtAsc(status);
    }

    @Transactional(readOnly = true)
    public boolean isEnrolled(AdmissionApplication application) {
        return studentPrograms.existsByProgrammeYearAndStudentEmail(
                application.getProgram().getId(), application.getAcademicYear().getId(), application.getEmail());
    }

    @Transactional(readOnly = true)
    public Set<Long> enrolledApplicationIds() {
        return Set.copyOf(applications.findEnrolledApplicationIds());
    }

    public AdmissionApplication decide(Long id, AdmissionDecisionRequest request) {
        AdmissionApplication application = applications.findByIdWithProgramAndAcademicYear(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (application.getStatus() == AdmissionApplicationStatus.ACCEPTED
                || application.getStatus() == AdmissionApplicationStatus.REJECTED
                || application.getStatus() == AdmissionApplicationStatus.WITHDRAWN) {
            throw new IllegalStateException("A finalized application cannot be decided again");
        }
        if (request.getStatus() != AdmissionApplicationStatus.ACCEPTED
                && request.getStatus() != AdmissionApplicationStatus.REJECTED) {
            throw new IllegalArgumentException("Decision must be ACCEPTED or REJECTED");
        }
        application.setStatus(request.getStatus());
        application.setReviewerNotes(request.getReviewerNotes());
        application.setDecidedAt(LocalDateTime.now());
        return applications.save(application);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> enrollmentCandidates(Long applicationId) {
        AdmissionApplication application = applications.findByIdWithProgramAndAcademicYear(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (application.getStatus() != AdmissionApplicationStatus.ACCEPTED) {
            throw new IllegalStateException("Only accepted applications can be enrolled");
        }
        requireActiveProgrammeDirectory(application.getProgram());
        return students.findActiveByEmailIgnoreCaseWithUser(application.getEmail()).stream()
                .map(student -> {
                    Map<String, Object> candidate = new LinkedHashMap<>();
                    candidate.put("id", student.getId());
                    candidate.put("fullName", student.getFullName());
                    candidate.put("username", student.getUser().getUsername());
                    candidate.put("email", student.getEmail());
                    candidate.put("registrationNumber", student.getRegistrationNumber());
                    candidate.put("enrolled", studentPrograms.existsByStudentIdAndProgramIdAndAcademicYearId(
                            student.getId(), application.getProgram().getId(), application.getAcademicYear().getId()));
                    return candidate;
                })
                .toList();
    }

    public StudentProgram enroll(Long applicationId, EnrollAdmissionRequest request, String changedBy) {
        AdmissionApplication application = applications.findByIdWithProgramAndAcademicYear(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (application.getStatus() != AdmissionApplicationStatus.ACCEPTED) {
            throw new IllegalStateException("Only an accepted application can be enrolled");
        }
        requireActiveProgrammeDirectory(application.getProgram());
        Student student = students.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        return enroll(application, student, changedBy);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdmissionEnrollmentCredentialsResponse createStudentAndEnroll(
            Long applicationId, CreateAdmittedStudentRequest request, String changedBy) {
        AdmissionApplication application = applications.findByIdWithProgramAndAcademicYear(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (application.getStatus() != AdmissionApplicationStatus.ACCEPTED) {
            throw new IllegalStateException("Only an accepted application can be enrolled");
        }
        requireActiveProgrammeDirectory(application.getProgram());
        if (request.getEmail() == null
                || !request.getEmail().trim().equalsIgnoreCase(application.getEmail().trim())) {
            throw new IllegalArgumentException("Student email must match the accepted applicant's email");
        }

        String temporaryPassword = generateTemporaryPassword();
        CreateStudentRequest studentRequest = new CreateStudentRequest();
        studentRequest.setUsername(request.getUsername());
        studentRequest.setPassword(temporaryPassword);
        studentRequest.setFullName(request.getFullName());
        studentRequest.setEmail(application.getEmail());
        studentRequest.setPhone(application.getPhone());
        studentRequest.setClassName(application.getProgram().getName());
        AdminStudentDto created = adminService.admitStudent(studentRequest);
        Student student = students.findById(created.getId())
                .orElseThrow(() -> new IllegalStateException("Created student account could not be loaded"));
        StudentProgram enrollment = enroll(application, student, changedBy);
        return new AdmissionEnrollmentCredentialsResponse(
                enrollment.getId(),
                student.getId(),
                enrollment.getProgram().getId(),
                enrollment.getAcademicYear().getId(),
                enrollment.getStatus().name(),
                created.getUsername(),
                temporaryPassword);
    }

    private String generateTemporaryPassword() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private StudentProgram enroll(AdmissionApplication application, Student student, String changedBy) {
        if (!Boolean.TRUE.equals(student.getUser().getIsActive())) {
            throw new IllegalStateException("An inactive student account cannot be enrolled");
        }
        if (student.getEmail() == null || !student.getEmail().trim().toLowerCase(Locale.ROOT)
                .equals(application.getEmail().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Student email must match the accepted applicant's email");
        }
        var existingEnrollment = studentPrograms.findByStudentIdAndProgramIdAndAcademicYearId(
                student.getId(), application.getProgram().getId(), application.getAcademicYear().getId());
        if (existingEnrollment.isPresent()) return existingEnrollment.get();

        StudentProgram enrollment = new StudentProgram();
        enrollment.setStudent(student);
        enrollment.setProgram(application.getProgram());
        enrollment.setAcademicYear(application.getAcademicYear());
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);
        StudentProgram saved = studentPrograms.save(enrollment);

        StudentStatusHistory history = new StudentStatusHistory();
        history.setStudent(student);
        history.setStudentProgram(saved);
        history.setStatus(StudentLifecycleStatus.ACTIVE);
        history.setChangedBy(changedBy);
        history.setReason("Enrolled from admission application " + application.getApplicationNumber());
        statusHistory.save(history);
        return saved;
    }

    private void requireActiveProgrammeDirectory(Program program) {
        if (!programs.existsActiveInDirectory(program.getId())) {
            throw new IllegalStateException(
                    "The programme's campus, faculty, department, or programme is inactive");
        }
    }
}