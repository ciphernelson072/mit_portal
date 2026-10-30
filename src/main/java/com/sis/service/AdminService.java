package com.sis.service;

import com.sis.dto.*;
import com.sis.dto.StudentTransferOptionsDto.AcademicYearOption;
import com.sis.dto.StudentTransferOptionsDto.ProgramOption;
import com.sis.entity.AcademicYear;
import com.sis.entity.Course;
import com.sis.entity.Department;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.entity.StudentStatusHistory;
import com.sis.entity.Program;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.exception.ResourceNotFoundException;
import com.sis.service.AcademicRecordsService;
import com.sis.repository.CourseRepository;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentStatusHistoryRepository;
import com.sis.repository.DepartmentRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Service class for Admin operations.
 * Handles full CRUD operations for users, students, and teachers with soft delete support.
 * <p>
 * Every method here works with DTOs, never with JPA entities, at the controller boundary -
 * AdminController must never see a {@code User}/{@code Student}/{@code Teacher} entity.
 */
@Service
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final DepartmentRepository departmentRepository;
    private final GradeRepository gradeRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final StudentProgramRepository studentProgramRepository;
    private final StudentStatusHistoryRepository studentStatusHistoryRepository;
    private final ProgramRepository programRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicRecordsService academicRecordsService;

    public AdminService(UserRepository userRepository, StudentRepository studentRepository,
                         TeacherRepository teacherRepository, CourseRepository courseRepository,
                         DepartmentRepository departmentRepository, GradeRepository gradeRepository,
                         PasswordEncoder passwordEncoder,
                         RefreshTokenService refreshTokenService,
                         StudentProgramRepository studentProgramRepository,
                         StudentStatusHistoryRepository studentStatusHistoryRepository,
                         ProgramRepository programRepository,
                         AcademicYearRepository academicYearRepository,
                         AcademicRecordsService academicRecordsService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
        this.departmentRepository = departmentRepository;
        this.gradeRepository = gradeRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.studentProgramRepository = studentProgramRepository;
        this.studentStatusHistoryRepository = studentStatusHistoryRepository;
        this.programRepository = programRepository;
        this.academicYearRepository = academicYearRepository;
        this.academicRecordsService = academicRecordsService;
    }

    // ===== MAPPING HELPERS =====

    private UserDto toUserDto(User user) {
        return new UserDto(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPhone(),
            user.getRole() != null ? user.getRole().name() : null,
            Boolean.TRUE.equals(user.getIsActive())
        );
    }

    private AdminStudentDto toStudentDto(Student student) {
        AdminStudentDto dto = new AdminStudentDto(
                student.getId(),
                student.getFullName(),
                student.getClassName(),
                student.getUser().getUsername(),
                student.getEmail(),
                student.getPhone(),
                student.getAddress(),
                approvedCgpa(student.getId()),
                student.getProfileImagePath(),
                Boolean.TRUE.equals(student.getUser().getIsActive())
        );
            dto.setRegistrationNumber(student.getRegistrationNumber());
            return dto;
    }

    private BigDecimal approvedCgpa(Long studentId) {
        List<Grade> approved = gradeRepository.findByStudentIdAndStatus(studentId, GradeStatus.APPROVED);
        return approved.isEmpty() ? null : GradeCalculationService.calculateApprovedCgpa(approved);
    }

    private AdminTeacherDto toTeacherDto(Teacher teacher) {
        AdminTeacherDto dto = new AdminTeacherDto(
                teacher.getId(),
                teacher.getFullName(),
                teacher.getUser().getUsername(),
                teacher.getEmail(),
                teacher.getPhone(),
                teacher.getQualification(),
                teacher.getYearsOfExperience(),
                teacher.getDepartment(),
                teacher.getProfileImagePath(),
                Boolean.TRUE.equals(teacher.getUser().getIsActive())
        );
        if (teacher.getHomeDepartment() != null) {
            dto.setDepartmentId(teacher.getHomeDepartment().getId());
            dto.setDepartmentName(teacher.getHomeDepartment().getName());
        }
        return dto;
    }

    private String normalizeUsername(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    // ===== USER MANAGEMENT =====

    public List<UserDto> getAllActiveUsers() {
        return userRepository.findByIsActiveTrueOrderByIdAsc().stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::toUserDto).collect(Collectors.toList());
    }

    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return toUserDto(user);
    }

    /**
     * Create a new user account with an admin-chosen role. Only reachable via
     * AdminController, which requires {@code hasAuthority('ADMIN')} - this is
     * intentionally the only place a caller can pick an arbitrary role.
     */
    public UserDto createUser(CreateUserRequest request) {
        String normalizedUsername = normalizeUsername(request.getUsername());
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (request.getRole() == Role.STUDENT || request.getRole() == Role.TEACHER) {
            throw new IllegalArgumentException("Student and teacher accounts must be created through their managed workflows");
        }

        if (userRepository.findByUsernameIgnoreCase(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + normalizedUsername);
        }
        if (normalizedEmail != null && userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email is already associated with another user");
        }

        User user = new User(normalizedUsername, passwordEncoder.encode(request.getPassword()), request.getRole());
        user.setEmail(normalizedEmail);
        user.setPhone(request.getPhone());
        user.setIsActive(true);
        user.setPasswordChangeRequired(request.getRole() == Role.STUDENT);
        return toUserDto(userRepository.save(user));
    }

    public UserDto updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (user.getRole() == Role.ADMIN && request.getRole() != null && request.getRole() != Role.ADMIN
            && userRepository.countByRoleAndIsActive(Role.ADMIN, true) <= 1) {
            throw new IllegalArgumentException("The last active administrator cannot be demoted");
        }

        String normalizedEmail = normalizeEmail(request.getEmail());
        if (normalizedEmail != null && !normalizedEmail.equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail())) {
            if (userRepository.findByEmailIgnoreCase(normalizedEmail)
                    .filter(existing -> !existing.getId().equals(user.getId())).isPresent()) {
                throw new IllegalArgumentException("Email is already associated with another user");
            }
        }
        user.setEmail(normalizedEmail);
        user.setPhone(request.getPhone());

        boolean credentialsChanged = false;
        if (request.getRole() != null) {
            credentialsChanged = user.getRole() != request.getRole();
            if (credentialsChanged && request.getRole() == Role.STUDENT
                    && studentRepository.findByUserId(user.getId()).isEmpty()) {
                throw new IllegalArgumentException("Cannot assign STUDENT role: the account has no student profile");
            }
            if (credentialsChanged && request.getRole() == Role.TEACHER
                    && teacherRepository.findByUserId(user.getId()).isEmpty()) {
                throw new IllegalArgumentException("Cannot assign TEACHER role: the account has no teacher profile");
            }
            user.setRole(request.getRole());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setPasswordChangeRequired(user.getRole() == Role.STUDENT);
            credentialsChanged = true;
        }
        if (credentialsChanged && user.getRole() == Role.STUDENT) {
            user.setPasswordChangeRequired(true);
        } else if (user.getRole() != Role.STUDENT) {
            user.setPasswordChangeRequired(false);
        }

        if (credentialsChanged) {
            user.setCredentialVersion(user.getCredentialVersion() + 1);
            refreshTokenService.revokeAllForUser(user.getId());
        }

        return toUserDto(userRepository.save(user));
    }

    /** Soft delete user - marks account as inactive. */
    public void removeUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (user.getRole() == Role.ADMIN && Boolean.TRUE.equals(user.getIsActive())
            && userRepository.countByRoleAndIsActive(Role.ADMIN, true) <= 1) {
            throw new IllegalArgumentException("The last active administrator cannot be deactivated");
        }
        if (Boolean.TRUE.equals(user.getIsActive())) {
            user.setCredentialVersion(user.getCredentialVersion() + 1);
        }
        user.setIsActive(false);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    /** Restore soft-deleted user account. */
    public void restoreUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setIsActive(true);
        userRepository.save(user);
    }

    public List<UserDto> getSoftDeletedUsers() {
        return userRepository.findByIsActiveFalseOrderByIdAsc().stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    // ===== STUDENT MANAGEMENT =====

    public List<AdminStudentDto> getAllStudents() {
        return studentRepository.findAllWithUser().stream().map(this::toStudentDto).collect(Collectors.toList());
    }

    /** Paginated student listing - JOIN FETCH avoids the N+1 toStudentDto() would
     *  otherwise cause per row (student.getUser().getUsername() for every student). */
    public org.springframework.data.domain.Page<AdminStudentDto> getAllStudents(org.springframework.data.domain.Pageable pageable) {
        return studentRepository.findAllWithUser(pageable).map(this::toStudentDto);
    }

    public org.springframework.data.domain.Page<AdminStudentDto> searchStudents(
            String query, Boolean active, org.springframework.data.domain.Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return studentRepository.searchWithUser(normalizedQuery, active, pageable).map(this::toStudentDto);
    }

    public AdminStudentDto getStudentById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        return toStudentDto(student);
    }

    public List<StudentProgramLifecycleDto> getStudentProgramLifecycles(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found with id: " + studentId);
        }
        return studentProgramRepository.findByStudentIdOrderByStartDateDesc(studentId).stream()
                .map(enrollment -> new StudentProgramLifecycleDto(
                        enrollment.getId(),
                        enrollment.getProgram().getId(),
                        enrollment.getProgram().getName(),
                        enrollment.getAcademicYear().getId(),
                        enrollment.getAcademicYear().getName(),
                        enrollment.getStatus().name(),
                        enrollment.getStartDate(),
                        enrollment.getEndDate()))
                .collect(Collectors.toList());
    }

    public List<StudentStatusHistoryDto> getStudentStatusHistory(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found with id: " + studentId);
        }
        return studentStatusHistoryRepository.findTop100ByStudentIdOrderByChangedAtDescIdDesc(studentId)
                .stream()
                .map(history -> {
                    StudentProgram enrollment = history.getStudentProgram();
                    return new StudentStatusHistoryDto(
                            history.getId(),
                            history.getStatus().name(),
                            history.getReason(),
                            history.getChangedBy(),
                            history.getChangedAt(),
                            enrollment != null ? enrollment.getId() : null,
                            enrollment != null && enrollment.getProgram() != null
                                    ? enrollment.getProgram().getName() : null,
                            enrollment != null && enrollment.getAcademicYear() != null
                                    ? enrollment.getAcademicYear().getName() : null);
                })
                .collect(Collectors.toList());
    }

    public StudentTransferOptionsDto getStudentTransferOptions(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found with id: " + studentId);
        }
        List<ProgramOption> programs = programRepository.findActiveProgramsInActiveDirectoryOrderByName()
                .stream()
                .map(program -> new ProgramOption(program.getId(), program.getCode(), program.getName(),
                        program.getDepartment().getName()))
                .collect(Collectors.toList());
        List<AcademicYearOption> years = academicYearRepository.findFirstByActiveTrueOrderByStartDateDesc()
                .map(year -> List.of(new AcademicYearOption(year.getId(), year.getName(),
                        year.getStartDate(), year.getEndDate())))
                .orElseGet(List::of);
        return new StudentTransferOptionsDto(programs, years);
    }

    public void transferStudentProgram(Long studentId, Long sourceProgramId, Long sourceAcademicYearId,
                                       Long destinationProgramId, Long destinationAcademicYearId,
                                       String reason, String changedBy) {
        if (sourceProgramId == null || sourceAcademicYearId == null || destinationProgramId == null
                || destinationAcademicYearId == null || sourceProgramId.equals(destinationProgramId)) {
            throw new IllegalArgumentException("Select a different destination programme and a valid academic year.");
        }
        if (reason == null || reason.trim().isEmpty() || reason.length() > 1800) {
            throw new IllegalArgumentException("A transfer reason of at most 1800 characters is required.");
        }
        if (changedBy == null || changedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("The authenticated administrator is required for the audit record.");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));
        if (student.getUser() == null || !Boolean.TRUE.equals(student.getUser().getIsActive())) {
            throw new IllegalStateException("An inactive student account cannot be transferred.");
        }

        StudentProgram source = studentProgramRepository
                .findForUpdateByStudentIdAndProgramIdAndAcademicYearId(
                        studentId, sourceProgramId, sourceAcademicYearId)
                .orElseThrow(() -> new IllegalArgumentException("Source programme enrollment was not found."));
        if (source.getStatus() != StudentLifecycleStatus.ACTIVE) {
            throw new IllegalStateException("Only an active programme enrollment can be transferred.");
        }
        if (!programRepository.existsActiveInDirectory(destinationProgramId)) {
            throw new IllegalArgumentException("The destination programme is not in the active directory.");
        }
        AcademicYear destinationYear = academicYearRepository.findById(destinationAcademicYearId)
                .orElseThrow(() -> new IllegalArgumentException("Destination academic year was not found."));
        if (!Boolean.TRUE.equals(destinationYear.getActive())) {
            throw new IllegalArgumentException("The destination academic year must be active.");
        }
        if (studentProgramRepository.existsByStudentIdAndProgramIdAndAcademicYearId(
                studentId, destinationProgramId, destinationAcademicYearId)) {
            throw new IllegalStateException(
                    "The student already has an enrollment in the destination programme and year.");
        }
        Program destinationProgram = programRepository.findByIdWithDirectory(destinationProgramId)
                .orElseThrow(() -> new IllegalArgumentException("Destination programme was not found."));

        LocalDate transferDate = LocalDate.now();
        source.setStatus(StudentLifecycleStatus.TRANSFERRED);
        source.setEndDate(transferDate);
        studentProgramRepository.save(source);

        StudentProgram destination = new StudentProgram();
        destination.setStudent(student);
        destination.setProgram(destinationProgram);
        destination.setAcademicYear(destinationYear);
        destination.setStatus(StudentLifecycleStatus.ACTIVE);
        destination.setStartDate(transferDate);
        studentProgramRepository.save(destination);

        StudentStatusHistory sourceHistory = new StudentStatusHistory();
        sourceHistory.setStudent(student);
        sourceHistory.setStudentProgram(source);
        sourceHistory.setStatus(StudentLifecycleStatus.TRANSFERRED);
        sourceHistory.setReason("Transferred from programme " + sourceProgramId + " in academic year "
                + sourceAcademicYearId + " to programme " + destinationProgramId + " in academic year "
                + destinationAcademicYearId + ". Reason: " + reason.trim());
        sourceHistory.setChangedBy(changedBy.trim());
        studentStatusHistoryRepository.save(sourceHistory);

        StudentStatusHistory destinationHistory = new StudentStatusHistory();
        destinationHistory.setStudent(student);
        destinationHistory.setStudentProgram(destination);
        destinationHistory.setStatus(StudentLifecycleStatus.ACTIVE);
        destinationHistory.setReason("Transfer enrollment into programme " + destinationProgramId
                + " in academic year " + destinationAcademicYearId + ". Reason: " + reason.trim());
        destinationHistory.setChangedBy(changedBy.trim());
        studentStatusHistoryRepository.save(destinationHistory);
    }

    /** Admit (create) a new student - creates both the student record and its user account. */
    public AdminStudentDto admitStudent(CreateStudentRequest request) {
        String normalizedUsername = normalizeUsername(request.getUsername());
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.findByUsernameIgnoreCase(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + normalizedUsername);
        }
        if (normalizedEmail != null && userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email is already associated with another user");
        }

        User user = new User(normalizedUsername, passwordEncoder.encode(request.getPassword()), com.sis.entity.Role.STUDENT);
        user.setEmail(normalizedEmail);
        user.setPhone(request.getPhone());
        user.setPasswordChangeRequired(true);
        user = userRepository.save(user);

        Student student = new Student(user, request.getFullName(), request.getClassName());
        student.setEmail(normalizedEmail);
        student.setPhone(request.getPhone());
        student.setEnrollmentDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        student.setRegistrationNumber("P-" + java.util.UUID.randomUUID());

        if (request.getCourseIds() != null) {
            for (Long courseId : request.getCourseIds()) {
                if (courseId != null) {
                    Course course = courseRepository.findById(courseId)
                            .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));
                    student.getCourses().add(course);
                }
            }
        }

        Student savedStudent = studentRepository.save(student);
        savedStudent.setRegistrationNumber(generateRegistrationNumber(savedStudent));
        return toStudentDto(studentRepository.save(savedStudent));
    }

    private String generateRegistrationNumber(Student student) {
        String year = student.getEnrollmentDate() != null && student.getEnrollmentDate().length() >= 4
                ? student.getEnrollmentDate().substring(0, 4)
                : String.valueOf(java.time.Year.now().getValue());
        return String.format("MIT/%s/%05d", year, student.getId());
    }

    public AdminStudentDto updateStudent(Long id, UpdateStudentRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));

        student.setFullName(request.getFullName());
        student.setClassName(request.getClassName());
        student.setEmail(request.getEmail());
        student.setPhone(request.getPhone());
        student.setAddress(request.getAddress());
        student.setDateOfBirth(request.getDateOfBirth());
        student.setGender(request.getGender());
        student.setAlternativePhone(request.getAlternativePhone());
        student.setCity(request.getCity());
        student.setCountry(request.getCountry());

        return toStudentDto(studentRepository.save(student));
    }

    /** Change a student's lifecycle status for a given academic year (defer, suspend, withdraw, graduate, etc.)
     *  Creates a StudentStatusHistory entry and updates the StudentProgram record. This operation is
     *  intended for administrative use and performs validations to ensure the enrollment exists.
     */
    public void changeStudentLifecycleStatus(Long studentId, Long programId, Long academicYearId,
                                             StudentLifecycleStatus newStatus, String reason, String changedBy) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Student lifecycle status is required");
        }
        if (reason == null || reason.trim().isEmpty() || reason.length() > 2000) {
            throw new IllegalArgumentException("A lifecycle decision reason of at most 2000 characters is required.");
        }
        if (changedBy == null || changedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("The authenticated administrator is required for the audit record.");
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));

        StudentProgram enrollment = studentProgramRepository
                .findForUpdateByStudentIdAndProgramIdAndAcademicYearId(studentId, programId, academicYearId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No enrollment found for student, programme, and academic year"));
        StudentLifecycleStatus currentStatus = enrollment.getStatus();
        if (!isAllowedLifecycleTransition(currentStatus, newStatus)) {
            throw new IllegalArgumentException("Student lifecycle transition from " + currentStatus
                    + " to " + newStatus + " is not allowed");
        }
        if (newStatus == StudentLifecycleStatus.GRADUATED) {
            boolean eligible = Boolean.TRUE.equals(academicRecordsService
                    .degreeAudit(studentId, programId, academicYearId).get("eligibleForGraduation"));
            if (!eligible) {
                throw new IllegalStateException(
                        "Graduation requires completed programme requirements, an active programme directory, and financial clearance.");
            }
        }
        enrollment.setStatus(newStatus);
        if (newStatus == StudentLifecycleStatus.WITHDRAWN || newStatus == StudentLifecycleStatus.GRADUATED) {
            enrollment.setEndDate(LocalDate.now());
        } else if (newStatus == StudentLifecycleStatus.ACTIVE) {
            enrollment.setEndDate(null);
        }
        studentProgramRepository.save(enrollment);

        StudentStatusHistory history = new StudentStatusHistory();
        history.setStudent(student);
        history.setStudentProgram(enrollment);
        history.setStatus(newStatus);
        history.setReason(reason.trim());
        history.setChangedBy(changedBy.trim());
        studentStatusHistoryRepository.save(history);
    }

    private boolean isAllowedLifecycleTransition(StudentLifecycleStatus current, StudentLifecycleStatus next) {
        if (current == null || current == next) {
            return false;
        }
        switch (current) {
            case ACTIVE:
                return next == StudentLifecycleStatus.DEFERRED
                        || next == StudentLifecycleStatus.SUSPENDED
                        || next == StudentLifecycleStatus.WITHDRAWN
                        || next == StudentLifecycleStatus.GRADUATED;
            case DEFERRED:
                return next == StudentLifecycleStatus.ACTIVE
                        || next == StudentLifecycleStatus.SUSPENDED
                        || next == StudentLifecycleStatus.WITHDRAWN;
            case SUSPENDED:
                return next == StudentLifecycleStatus.ACTIVE
                        || next == StudentLifecycleStatus.WITHDRAWN;
            case GRADUATED:
                return next == StudentLifecycleStatus.ALUMNI;
            case WITHDRAWN:
            case ALUMNI:
            default:
                return false;
        }
    }

    /** Soft delete student - marks the underlying user account as inactive. */
    public void removeStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        if (Boolean.TRUE.equals(student.getUser().getIsActive())) {
            student.getUser().setCredentialVersion(student.getUser().getCredentialVersion() + 1);
        }
        student.getUser().setIsActive(false);
        studentRepository.save(student);
        refreshTokenService.revokeAllForUser(student.getUser().getId());
    }

    public void restoreStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        student.getUser().setIsActive(true);
        studentRepository.save(student);
    }

    // ===== TEACHER MANAGEMENT =====

    public List<AdminTeacherDto> getAllTeachers() {
        return teacherRepository.findAllWithUser().stream().map(this::toTeacherDto).collect(Collectors.toList());
    }

    /** See getAllStudents(Pageable) - same rationale. */
    public org.springframework.data.domain.Page<AdminTeacherDto> getAllTeachers(org.springframework.data.domain.Pageable pageable) {
        return teacherRepository.findAllWithUser(pageable).map(this::toTeacherDto);
    }

    public AdminTeacherDto getTeacherById(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));
        return toTeacherDto(teacher);
    }

    /** Hire (create) a new teacher - creates both the teacher record and its user account. */
    public AdminTeacherDto hireTeacher(CreateTeacherRequest request) {
        String normalizedUsername = normalizeUsername(request.getUsername());
        String normalizedEmail = normalizeEmail(request.getEmail());
        Department homeDepartment = requireActiveDepartment(request.getDepartmentId());

        if (userRepository.findByUsernameIgnoreCase(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + normalizedUsername);
        }
        if (normalizedEmail != null && userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email is already associated with another user");
        }

        User user = new User(normalizedUsername, passwordEncoder.encode(request.getPassword()), com.sis.entity.Role.TEACHER);
        user.setEmail(normalizedEmail);
        user = userRepository.save(user);

        Teacher teacher = new Teacher(user, request.getFullName());
        teacher.setEmail(normalizedEmail);
        teacher.setPhone(request.getPhone());
        teacher.setQualification(request.getQualification());
        teacher.setYearsOfExperience(request.getYearsOfExperience());
        teacher.setDepartment(request.getDepartment());
        teacher.setHomeDepartment(homeDepartment);

        return toTeacherDto(teacherRepository.save(teacher));
    }

    public AdminTeacherDto updateTeacher(Long id, UpdateTeacherRequest request) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));

        teacher.setFullName(request.getFullName());
        teacher.setEmail(request.getEmail());
        teacher.setPhone(request.getPhone());
        teacher.setQualification(request.getQualification());
        teacher.setYearsOfExperience(request.getYearsOfExperience());
        teacher.setDepartment(request.getDepartment());
        teacher.setHomeDepartment(requireActiveDepartment(request.getDepartmentId()));

        return toTeacherDto(teacherRepository.save(teacher));
    }

    private Department requireActiveDepartment(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + departmentId));
        if (!departmentRepository.existsActiveInActiveFacultyAndCampus(departmentId)) {
            throw new IllegalArgumentException("Select a department under an active faculty and campus.");
        }
        return department;
    }

    /** Soft delete teacher - marks the underlying user account as inactive. */
    public void removeTeacher(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));
        if (Boolean.TRUE.equals(teacher.getUser().getIsActive())) {
            teacher.getUser().setCredentialVersion(teacher.getUser().getCredentialVersion() + 1);
        }
        teacher.getUser().setIsActive(false);
        teacherRepository.save(teacher);
        refreshTokenService.revokeAllForUser(teacher.getUser().getId());
    }

    public void restoreTeacher(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));
        teacher.getUser().setIsActive(true);
        teacherRepository.save(teacher);
    }

    // ===== STATISTICS =====

    public Long getActiveStudentCount() {
        return studentRepository.countByUserIsActiveTrue();
    }

    public Long getActiveTeacherCount() {
        return teacherRepository.countByUserIsActiveTrue();
    }

    /** Builds the full admin dashboard statistics DTO - kept here, not in the controller. */
    public AdminStatisticsDto getStatistics() {
        AdminStatisticsDto stats = new AdminStatisticsDto();
        stats.setTotalStudents(getActiveStudentCount());
        stats.setTotalTeachers(getActiveTeacherCount());
        stats.setTotalUsers(userRepository.countByIsActiveTrue());
        stats.setDeletedUsersCount(userRepository.countByIsActiveFalse());
        return stats;
    }
}
