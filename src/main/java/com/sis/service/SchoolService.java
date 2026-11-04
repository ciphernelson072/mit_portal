package com.sis.service;

import com.sis.dto.*;
import com.sis.entity.*;
import com.sis.exception.DuplicateResourceException;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Users, students, teachers, courses, and roster/directory lookups - plus a set of
 * thin delegating methods (grades/attendance/status, announcements, fees, materials,
 * messages) kept here so every existing controller can go on injecting SchoolService
 * without a constructor-signature change.
 * <p>
 * The real logic for those delegated concerns has been split into focused,
 * independently testable services - {@link AcademicRecordService}, {@link FeeService},
 * {@link MessagingService}, {@link CourseMaterialService} - each owning exactly one
 * domain instead of this one class owning all ten. This class used to BE that
 * "SchoolService does everything" class; splitting the logic out while keeping the
 * method signatures here is a deliberate, lower-risk way to get the same separation
 * of concerns without having to touch (and re-verify, without a compiler available)
 * every controller's constructor injection in the same pass.
 */
@Service
@Transactional
public class SchoolService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final CourseUnitRepository courseUnitRepository;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final CourseCategoryRepository courseCategoryRepository;
    private final FeeRepository feeRepository;
    private final AssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;

    private final AcademicRecordService academicRecordService;
    private final FeeService feeService;
    private final MessagingService messagingService;
    private final CourseMaterialService courseMaterialService;

    public SchoolService(StudentRepository studentRepository, TeacherRepository teacherRepository,
                         CourseRepository courseRepository, GradeRepository gradeRepository,
                         CourseUnitRepository courseUnitRepository,
                         AttendanceRepository attendanceRepository, UserRepository userRepository,
                         CourseCategoryRepository courseCategoryRepository,
                         FeeRepository feeRepository, AssignmentRepository assignmentRepository,
                         PasswordEncoder passwordEncoder,
                         AcademicRecordService academicRecordService, FeeService feeService,
                         MessagingService messagingService, CourseMaterialService courseMaterialService) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
        this.courseUnitRepository = courseUnitRepository;
        this.gradeRepository = gradeRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.courseCategoryRepository = courseCategoryRepository;
        this.feeRepository = feeRepository;
        this.assignmentRepository = assignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.academicRecordService = academicRecordService;
        this.feeService = feeService;
        this.messagingService = messagingService;
        this.courseMaterialService = courseMaterialService;
    }

    // --- USER METHODS ---
    public Optional<User> getUserByUsername(String username) {
        return username == null ? Optional.empty() : userRepository.findByUsernameIgnoreCase(username.trim());
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public Optional<User> createUser(String username, String rawPassword, Role role) {
        String normalizedUsername = username == null ? null : username.trim();
        if (normalizedUsername == null || userRepository.findByUsernameIgnoreCase(normalizedUsername).isPresent()) {
            return Optional.empty();
        }
        User user = new User(normalizedUsername, passwordEncoder.encode(rawPassword), role);
        return Optional.of(userRepository.saveAndFlush(user));
    }

    // --- STUDENT METHODS ---
    public List<Student> listStudents() {
        return studentRepository.findAll();
    }

    public Optional<Student> getStudentById(Long id) {
        return id == null ? Optional.empty() : studentRepository.findById(id);
    }

    public boolean studentEnrolledInCourse(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) return false;
        return courseRepository.existsEnrolledStudent(courseId, studentId);
    }

    public long countDistinctStudentsInCourses(List<Long> courseIds) {
        return courseIds == null || courseIds.isEmpty()
                ? 0 : courseRepository.countDistinctStudentsByCourseIds(courseIds);
    }

    public long countPendingGradesInCourses(List<Long> courseIds) {
        return courseIds == null || courseIds.isEmpty()
                ? 0 : gradeRepository.countByCourseIdsAndStatusOrNull(courseIds, GradeStatus.PENDING);
    }

    public long countUpcomingAssignmentsInCourses(List<Long> courseIds, java.time.LocalDateTime after) {
        return courseIds == null || courseIds.isEmpty()
                ? 0 : assignmentRepository.countByCourseIdInAndDueAtAfter(courseIds, after);
    }

    public long countStudentsWithoutAttendanceInCourses(List<Long> courseIds, LocalDate date) {
        return courseIds == null || courseIds.isEmpty() || date == null
                ? 0 : courseRepository.countStudentsWithoutAttendanceByCourseIdsAndDate(courseIds, date);
    }

    public Optional<StudentProfileDto> updateStudentProfileByUsername(String username, UpdateStudentRequest request) {
        return getStudentByUsername(username).map(student -> {
            student.setFullName(request.getFullName());
            student.setEmail(request.getEmail());
            student.setPhone(request.getPhone());
            student.setAddress(request.getAddress());
            student.setDateOfBirth(request.getDateOfBirth());
            student.setGender(request.getGender());
            student.setAlternativePhone(request.getAlternativePhone());
            student.setCity(request.getCity());
            student.setCountry(request.getCountry());
            studentRepository.save(student);
            return getStudentProfile(student.getId());
        });
    }

    public Optional<Student> getStudentByUsername(String username) {
        if (username == null) return Optional.empty();
        return userRepository.findByUsernameIgnoreCase(username.trim())
                .flatMap(user -> studentRepository.findByUserId(user.getId()));
    }

    public Optional<Student> getStudentWithCoursesByUsername(String username) {
        if (username == null) return Optional.empty();
        return studentRepository.findByUserUsernameWithCourses(username);
    }

    public Optional<Student> getStudentWithCoursesAndTeachersById(Long studentId) {
        return studentId == null ? Optional.empty() : studentRepository.findByIdWithCoursesAndTeachers(studentId);
    }

    public StudentProfileDto getStudentProfile(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        StudentProfileDto dto = new StudentProfileDto();
        dto.setId(student.getId());
        dto.setUsername(student.getUser().getUsername());
        dto.setFullName(student.getFullName());
        dto.setClassName(student.getClassName());
        List<Grade> approvedGrades = gradeRepository.findByStudentIdAndStatus(studentId, GradeStatus.APPROVED);
        dto.setCgpa(approvedGrades.isEmpty() ? null : GradeCalculationService.calculateApprovedCgpa(approvedGrades));
        dto.setEmail(student.getEmail());
        dto.setPhone(student.getPhone());
        dto.setAddress(student.getAddress());
        dto.setEnrollmentDate(student.getEnrollmentDate());
        dto.setRegistrationNumber(student.getRegistrationNumber());
        dto.setDateOfBirth(student.getDateOfBirth());
        dto.setGender(student.getGender());
        dto.setAlternativePhone(student.getAlternativePhone());
        dto.setCity(student.getCity());
        dto.setCountry(student.getCountry());
        dto.setProfileImagePath(student.getProfileImagePath());
        dto.setUpdatedAt(student.getUpdatedAt() != null ? student.getUpdatedAt().toString() : null);
        dto.setCourses(student.getCourses().stream().map(Course::getName).collect(Collectors.toSet()));
        return dto;
    }

    public StudentProfileDto getStudentProfileByUsername(String username) {
        if (username == null) return null;
        return getStudentByUsername(username)
                .map(student -> getStudentProfile(student.getId()))
                .orElse(null);
    }

    // --- TEACHER METHODS ---
    public List<Teacher> listTeachers() {
        return teacherRepository.findAll();
    }

    public Optional<Teacher> getTeacherByUsername(String username) {
        if (username == null) return Optional.empty();
        return userRepository.findByUsernameIgnoreCase(username.trim())
                .flatMap(user -> teacherRepository.findByUserId(user.getId()));
    }

    public boolean teacherOwnsCourse(String username, Course course) {
        return username != null && course != null && course.getId() != null
                && courseRepository.existsByIdAndTeacherUserUsername(course.getId(), username);
    }

    public Optional<TeacherProfileDto> getTeacherProfileByUsername(String username) {
        return getTeacherByUsername(username).map(teacher -> {
            TeacherProfileDto dto = new TeacherProfileDto();
            dto.setId(teacher.getId());
            dto.setUsername(teacher.getUser().getUsername());
            dto.setFullName(teacher.getFullName());
            dto.setProfileImagePath(teacher.getProfileImagePath());
            if (teacher.getHomeDepartment() != null) {
                dto.setDepartmentId(teacher.getHomeDepartment().getId());
                dto.setDepartmentName(teacher.getHomeDepartment().getName());
            }
            dto.setCourses(teacher.getCourses().stream().map(Course::getName).collect(Collectors.toSet()));
            return dto;
        });
    }

    // --- COURSE METHODS ---
    public List<Course> listCourses() {
        return courseRepository.findAll();
    }

    public List<AdminCourseDto> listCourseDtos() {
        return courseRepository.findAllWithTeacher().stream()
                .map(course -> {
                    AdminCourseDto dto = new AdminCourseDto(
                        course.getId(),
                        course.getName(),
                    course.getCourseCode(),
                        course.getCategory() != null ? course.getCategory().getName() : "Other",
                    course.getTeacher().getFullName(),
                    course.getSemester(),
                    course.getCredits());
                    dto.setCore(course.getCore());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<AdminCourseDto> listCourseDtos(org.springframework.data.domain.Pageable pageable) {
        return courseRepository.findAllWithTeacher(pageable)
                .map(course -> {
                    AdminCourseDto dto = new AdminCourseDto(
                        course.getId(),
                        course.getName(),
                    course.getCourseCode(),
                        course.getCategory() != null ? course.getCategory().getName() : "Other",
                    course.getTeacher().getFullName(),
                    course.getSemester(),
                    course.getCredits());
                    dto.setCore(course.getCore());
                    return dto;
                });
    }

    public Optional<Course> getCourseById(Long courseId) {
        return courseId == null ? Optional.empty() : courseRepository.findById(courseId);
    }

    public List<Long> getUserIdsForStudentsInCourse(Long courseId) {
        return courseId == null ? Collections.emptyList() : studentRepository.findUserIdsByCourseId(courseId);
    }

    public Optional<Long> getStudentUserId(Long studentId) {
        return studentId == null ? Optional.empty() : studentRepository.findUserIdByStudentId(studentId);
    }

    public List<CourseDto> getCoursesForStudent(Long studentId) {
        if (studentId == null) return Collections.emptyList();
        return courseRepository.findByStudentIdWithTeacherAndCategoryIncludingRegistrations(studentId).stream()
                .map(course -> {
                    CourseDto dto = new CourseDto();
                    dto.setId(course.getId());
                    dto.setName(course.getName());
                    dto.setCourseCode(course.getCourseCode());
                    dto.setCategory(course.getCategory() != null ? course.getCategory().getName() : "Other");
                    dto.setTeacherName(course.getTeacher().getFullName());
                    dto.setSemester(course.getSemester());
                    dto.setCredits(course.getCredits());
                    return dto;
                }).collect(Collectors.toList());
    }

    public List<String> getMissingResultCourseNames(Long studentId) {
        java.util.Set<Long> courseIdsWithGrades = gradeRepository.findByStudentId(studentId).stream()
                .map(Grade::getCourse)
                .filter(Objects::nonNull)
                .map(Course::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return getCoursesForStudent(studentId).stream()
                .filter(course -> !courseIdsWithGrades.contains(course.getId()))
                .map(course -> (course.getCourseCode() == null ? "" : course.getCourseCode() + " · ")
                        + course.getName())
                .collect(Collectors.toList());
    }

    public List<CourseDto> getCoursesForTeacher(Long teacherId) {
        if (teacherId == null) return Collections.emptyList();
        return courseRepository.findByTeacherIdWithTeacher(teacherId).stream().map(course -> {
            CourseDto dto = new CourseDto();
            dto.setId(course.getId());
            dto.setName(course.getName());
            dto.setCourseCode(course.getCourseCode());
            dto.setCategory(course.getCategory() != null ? course.getCategory().getName() : "Other");
            dto.setTeacherName(course.getTeacher().getFullName());
            dto.setSemester(course.getSemester());
            dto.setCredits(course.getCredits());
            return dto;
        }).collect(Collectors.toList());
    }

        public List<CourseUnitDto> getCourseUnitsForStudent(Long studentId) {
        if (studentId == null) return Collections.emptyList();
        return courseUnitRepository.findByStudentIdWithCourse(studentId).stream()
            .map(this::toCourseUnitDto)
            .collect(Collectors.toList());
        }

        public List<CourseUnitDto> getCourseUnitsForTeacher(Long teacherId) {
        if (teacherId == null) return Collections.emptyList();
        return courseUnitRepository.findByTeacherIdWithCourse(teacherId).stream()
            .map(this::toCourseUnitDto)
            .collect(Collectors.toList());
        }

        public List<CourseUnitDto> listCourseUnits() {
        return courseUnitRepository.findAllWithCourse().stream()
            .map(this::toCourseUnitDto)
            .collect(Collectors.toList());
        }

        public CourseUnitDto createCourseUnit(CreateCourseUnitRequest request) {
        String unitCode = request.getUnitCode().trim().toUpperCase();
        if (courseUnitRepository.existsByUnitCodeIgnoreCase(unitCode)) {
            throw new IllegalArgumentException("Course unit code already exists: " + unitCode);
        }
        CourseUnit unit = new CourseUnit();
        unit.setUnitCode(unitCode);
        unit.setName(request.getName().trim());
        unit.setCourse(courseRepository.findById(request.getCourseId())
            .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + request.getCourseId())));
        unit.setTeacher(teacherRepository.findById(request.getTeacherId())
            .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + request.getTeacherId())));
        return toCourseUnitDto(courseUnitRepository.save(unit));
        }

        public CourseUnitDto assignCourseUnitTeacher(Long unitId, Long teacherId) {
        CourseUnit unit = courseUnitRepository.findById(unitId)
            .orElseThrow(() -> new ResourceNotFoundException("Course unit not found: " + unitId));
        unit.setTeacher(teacherRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId)));
        return toCourseUnitDto(courseUnitRepository.save(unit));
        }

        public CourseUnitDto enrollStudentsInCourseUnit(Long unitId, List<Long> studentIds) {
        CourseUnit unit = courseUnitRepository.findById(unitId)
            .orElseThrow(() -> new ResourceNotFoundException("Course unit not found: " + unitId));
        if (studentIds != null) {
            studentIds.stream().filter(Objects::nonNull).forEach(studentId ->
                studentRepository.findById(studentId).ifPresent(unit.getStudents()::add));
        }
        return toCourseUnitDto(courseUnitRepository.save(unit));
        }

        private CourseUnitDto toCourseUnitDto(CourseUnit unit) {
        Course course = unit.getCourse();
        return new CourseUnitDto(unit.getId(), unit.getUnitCode(), unit.getName(), course.getId(),
            course.getName(), course.getCourseCode(), course.getCategory().getName(),
            unit.getTeacher().getId(), unit.getTeacher().getFullName());
        }

    public List<Student> listStudentsByCourse(Long courseId) {
        if (courseId == null) {
            return new ArrayList<>();
        }
        return studentRepository.findByCourseIdIncludingRegistrations(courseId);
    }

    public List<Assignment> getCourseAssignments(Long courseId) {
        if (courseId == null) {
            return Collections.emptyList();
        }
        return assignmentRepository.findByCourseIdIn(Collections.singletonList(courseId));
    }

    public Optional<CourseUnit> getCourseUnitById(Long unitId) {
        return unitId == null ? Optional.empty() : courseUnitRepository.findById(unitId);
    }

    public boolean teacherOwnsCourseUnit(String username, Long unitId) {
        if (username == null || unitId == null) return false;
        if (!courseUnitRepository.existsById(unitId)) {
            throw new ResourceNotFoundException("Course unit not found: " + unitId);
        }
        return courseUnitRepository.existsAssignedToTeacherForCourse(unitId, username);
    }

    public List<Student> listStudentsByCourseUnit(Long unitId) {
        return getCourseUnitById(unitId)
                .map(unit -> new ArrayList<>(unit.getStudents()))
                .map(students -> (List<Student>) students)
                .orElse(Collections.emptyList());
    }

    public Course createCourse(CreateCourseRequest request) {
        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + request.getTeacherId()));
        Course course = new Course(request.getName(), teacher);
        course.setCourseCode(request.getCourseCode().trim().toUpperCase());
        course.setCategory(courseCategoryRepository.findById(request.getCategoryId())
            .filter(category -> Boolean.TRUE.equals(category.getActive()))
            .orElseThrow(() -> new ResourceNotFoundException("Active course category not found: " + request.getCategoryId())));
        course.setSemester(request.getSemester());
        if (request.getCredits() != null && request.getCredits() > 0) course.setCredits(request.getCredits());
        course.setCore(Boolean.TRUE.equals(request.getCore()));
        if (request.getStudentIds() != null) {
            for (Long studentId : request.getStudentIds()) {
                if (studentId != null) {
                    studentRepository.findById(studentId).ifPresent(course.getStudents()::add);
                }
            }
        }
        return courseRepository.save(course);
    }

    public AdminCourseDto createCourseDto(CreateCourseRequest request) {
        Course saved = createCourse(request);
        AdminCourseDto dto = new AdminCourseDto(saved.getId(), saved.getName(), saved.getCourseCode(), saved.getCategory().getName(), saved.getTeacher().getFullName(), saved.getSemester(), saved.getCredits());
        dto.setCore(saved.getCore());
        return dto;
    }

    public AdminCourseDto updateCourseDto(Long courseId, UpdateCourseRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));
        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + request.getTeacherId()));
        course.setName(request.getName());
        course.setCourseCode(request.getCourseCode().trim().toUpperCase());
        course.setCategory(courseCategoryRepository.findById(request.getCategoryId())
            .filter(category -> Boolean.TRUE.equals(category.getActive()))
            .orElseThrow(() -> new ResourceNotFoundException("Active course category not found: " + request.getCategoryId())));
        course.setTeacher(teacher);
        course.setSemester(request.getSemester());
        if (request.getCredits() != null && request.getCredits() > 0) course.setCredits(request.getCredits());
        if (request.getCore() != null) course.setCore(request.getCore());
        Course saved = courseRepository.save(course);
        AdminCourseDto dto = new AdminCourseDto(saved.getId(), saved.getName(), saved.getCourseCode(), saved.getCategory().getName(), saved.getTeacher().getFullName(), saved.getSemester(), saved.getCredits());
        dto.setCore(saved.getCore());
        return dto;
    }

    public List<CourseCategoryDto> listCourseCategories() {
        return courseCategoryRepository.findAll().stream()
                .map(category -> new CourseCategoryDto(category.getId(), category.getCode(), category.getName(), Boolean.TRUE.equals(category.getActive())))
                .collect(Collectors.toList());
    }

    public CourseCategoryDto createCourseCategory(CreateCourseCategoryRequest request) {
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        String name = request.getName().trim();
        ensureCourseCategoryAvailable(code, name, null);
        CourseCategory category = new CourseCategory(code, name);
        CourseCategory saved = courseCategoryRepository.save(category);
        return new CourseCategoryDto(saved.getId(), saved.getCode(), saved.getName(), true);
    }

    public CourseCategoryDto setCourseCategoryActive(Long id, boolean active) {
        CourseCategory category = courseCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course category not found: " + id));
        category.setActive(active);
        CourseCategory saved = courseCategoryRepository.save(category);
        return new CourseCategoryDto(saved.getId(), saved.getCode(), saved.getName(), Boolean.TRUE.equals(saved.getActive()));
    }

    public CourseCategoryDto updateCourseCategory(Long id, CreateCourseCategoryRequest request) {
        CourseCategory category = courseCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course category not found: " + id));
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        String name = request.getName().trim();
        ensureCourseCategoryAvailable(code, name, id);
        category.setCode(code);
        category.setName(name);
        CourseCategory saved = courseCategoryRepository.save(category);
        return new CourseCategoryDto(saved.getId(), saved.getCode(), saved.getName(), Boolean.TRUE.equals(saved.getActive()));
    }

    private void ensureCourseCategoryAvailable(String code, String name, Long excludedId) {
        courseCategoryRepository.findByCodeIgnoreCase(code)
                .filter(existing -> excludedId == null || !excludedId.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("A course category with this code already exists.");
                });
        courseCategoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> excludedId == null || !excludedId.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("A course category with this name already exists.");
                });
    }

    public void deleteCourseCategory(Long id) {
        if (!courseCategoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Course category not found: " + id);
        }
        if (courseRepository.countByCategoryId(id) > 0) {
            throw new IllegalArgumentException("Cannot delete a category that still contains courses; deactivate it instead");
        }
        courseCategoryRepository.deleteById(id);
    }

    // --- GRADES & ATTENDANCE (delegated to AcademicRecordService) ---
    public Grade saveGrade(Student student, Course course, String gradeValue, String remarks) {
        return academicRecordService.saveGrade(student, course, gradeValue, remarks);
    }

    public Grade saveGrade(Student student, Course course, String gradeValue, String remarks, String submittedBy) {
        return academicRecordService.saveGrade(student, course, gradeValue, remarks, submittedBy);
    }

    public Grade updateGradeScore(Grade grade, BigDecimal gpaScore) {
        return academicRecordService.updateGradeScore(grade, gpaScore);
    }

    public Grade updateGrade(Grade grade, String gradeValue, String remarks, BigDecimal gpaScore) {
        return academicRecordService.updateGrade(grade, gradeValue, remarks, gpaScore);
    }

    public Grade updateGrade(Grade grade, String gradeValue, String remarks, BigDecimal gpaScore, String actor) {
        return academicRecordService.updateGrade(grade, gradeValue, remarks, gpaScore, actor);
    }

    public Optional<Grade> getGradeById(Long id) {
        return id == null ? Optional.empty() : gradeRepository.findById(id);
    }

    public void deleteGrade(Grade grade) {
        academicRecordService.deleteGrade(grade);
    }

    public List<Grade> getGradesForStudent(Long studentId) {
        return academicRecordService.getGradesForStudent(studentId);
    }

    public List<Attendance> getAttendanceForStudent(Long studentId) {
        return academicRecordService.getAttendanceForStudent(studentId);
    }

    public GradeDto toGradeDto(Grade grade) {
        return academicRecordService.toGradeDto(grade);
    }

    public List<GradeDto> getGradeDtosForStudent(Long studentId) {
        return academicRecordService.getGradeDtosForStudent(studentId);
    }

    public List<AdminGradeDto> getAllGradeDtos() {
        return gradeRepository.findAllWithStudentUserAndCourse().stream()
                .map(grade -> new AdminGradeDto(
                        grade.getId(),
                        grade.getStudent().getId(),
                        grade.getStudent().getFullName(),
                        grade.getStudent().getUser().getUsername(),
                        grade.getCourse().getId(),
                        grade.getCourse().getName(),
                        grade.getGradeValue(),
                        grade.getGpaScore(),
                        grade.getRemarks(),
                        grade.getStatus() != null ? grade.getStatus().name() : "PENDING",
                        grade.getSubmittedBy(),
                        grade.getApprovedBy(),
                        grade.getRejectedBy(),
                        grade.getRejectionReason(),
                        grade.getTotalMark(),
                        grade.getRetakeRequired(),
                        grade.getGradingPolicyVersion()))
                .collect(Collectors.toList());
    }

    public AttendanceDto toAttendanceDto(Attendance attendance) {
        return academicRecordService.toAttendanceDto(attendance);
    }

    public List<AttendanceDto> getAttendanceDtosForStudent(Long studentId) {
        return academicRecordService.getAttendanceDtosForStudent(studentId);
    }

    public StudentStatusDto getStudentStatus(Long studentId) {
        return academicRecordService.getStudentStatus(studentId);
    }

    // --- ANNOUNCEMENTS & MESSAGES (delegated to MessagingService) ---
    public List<AnnouncementDto> listAnnouncements() {
        return messagingService.listAnnouncements();
    }

    public AnnouncementDto createAnnouncement(String title, String content, User author) {
        return messagingService.createAnnouncement(title, content, author);
    }

    public AnnouncementDto createAnnouncement(String title, String content, User author,
                                              com.sis.entity.AnnouncementAudience audience) {
        return messagingService.createAnnouncement(title, content, author, audience);
    }

    public List<Map<String, Object>> getPublicAnnouncements() {
        return messagingService.getPublicAnnouncements();
    }

    public Message sendMessage(User sender, User receiver, String subject, String content) {
        return messagingService.sendMessage(sender, receiver, subject, content);
    }

    public List<Message> getReceivedMessages(Long userId) {
        return messagingService.getReceivedMessages(userId);
    }

    public List<Message> getSentMessages(Long userId) {
        return messagingService.getSentMessages(userId);
    }

    public void markMessageRead(Long messageId) {
        messagingService.markMessageRead(messageId, null);
    }

    public boolean markMessageRead(Long messageId, Long userId) {
        return messagingService.markMessageRead(messageId, userId);
    }

    public MessageDto toMessageDto(Message message) {
        return messagingService.toMessageDto(message);
    }

    public List<MessageDto> getReceivedMessageDtos(Long userId) {
        return messagingService.getReceivedMessageDtos(userId);
    }

    public List<MessageDto> getSentMessageDtos(Long userId) {
        return messagingService.getSentMessageDtos(userId);
    }

    // --- FEES (delegated to FeeService) ---
    public List<FeeDto> getFeesForStudent(Long studentId) {
        return feeService.getFeesForStudent(studentId);
    }

    public Optional<FeeDto> getFeeForStudent(Long feeId, Long studentId) {
        return feeService.getFeeForStudent(feeId, studentId);
    }

    public List<AdminFeeDto> getAllFees() {
        return feeService.getAllFees();
    }

    public FeeDto createFee(Student student, String description, BigDecimal amount, LocalDate dueDate) {
        return feeService.createFee(student, description, amount, dueDate);
    }

    public void markFeePaid(Long feeId) {
        feeService.markFeePaid(feeId);
    }

    public Optional<FeeDto> payFeeForStudent(Long feeId, Long studentId) {
        return feeService.payFeeForStudent(feeId, studentId);
    }


    // --- COURSE MATERIALS (delegated to CourseMaterialService) ---
    public List<CourseMaterialDto> getMaterialsForCourse(Long courseId) {
        return courseMaterialService.getMaterialsForCourse(courseId);
    }

    public CourseMaterialDto createMaterial(Course course, String title, String description, String fileUrl, User uploadedBy) {
        return courseMaterialService.createMaterial(course, title, description, fileUrl, uploadedBy);
    }

    // --- ANALYTICS & STATS ---
    public Map<String, Object> getAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalStudents", studentRepository.count());
        analytics.put("totalTeachers", teacherRepository.count());
        analytics.put("totalCourses", courseRepository.count());
        analytics.put("totalGrades", gradeRepository.count());
        analytics.put("totalAttendance", attendanceRepository.count());
        analytics.put("totalFees", feeRepository.count());
        analytics.put("paidFees", feeRepository.countByPaid(true));
        analytics.put("unpaidFees", feeRepository.countByPaid(false));
        return analytics;
    }

    public Map<String, Long> getPublicStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("students", studentRepository.count());
        stats.put("teachers", teacherRepository.count());
        stats.put("courses", courseRepository.count());
        return stats;
    }
}
