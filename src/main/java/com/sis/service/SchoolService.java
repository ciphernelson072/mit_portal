package com.sis.service;

import com.sis.dto.*;
import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SchoolService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final AnnouncementRepository announcementRepository;
    private final FeeRepository feeRepository;
    private final CourseMaterialRepository courseMaterialRepository;
    private final MessageRepository messageRepository;
    private final PasswordEncoder passwordEncoder;

    public SchoolService(StudentRepository studentRepository, TeacherRepository teacherRepository,
                         CourseRepository courseRepository, GradeRepository gradeRepository,
                         AttendanceRepository attendanceRepository, UserRepository userRepository,
                         AnnouncementRepository announcementRepository, FeeRepository feeRepository,
                         CourseMaterialRepository courseMaterialRepository, MessageRepository messageRepository,
                         PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
        this.gradeRepository = gradeRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.announcementRepository = announcementRepository;
        this.feeRepository = feeRepository;
        this.courseMaterialRepository = courseMaterialRepository;
        this.messageRepository = messageRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // --- USER METHODS ---
    public Optional<User> getUserByUsername(String username) {
        return username == null ? Optional.empty() : userRepository.findByUsername(username);
    }

    public Optional<User> createUser(String username, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return Optional.empty();
        }
        User user = new User(username, rawPassword, role);
        return Optional.of(userRepository.saveAndFlush(user));
    }

    // --- STUDENT METHODS ---
    public List<Student> listStudents() {
        return studentRepository.findAll();
    }

    public List<AdminStudentDto> listStudentDtos() {
        return studentRepository.findAll().stream()
                .map(student -> new AdminStudentDto(
                        student.getId(),
                        student.getFullName(),
                        student.getClassName(),
                        student.getUser().getUsername()))
                .collect(Collectors.toList());
    }

    public Optional<Student> getStudentById(Long id) {
        return id == null ? Optional.empty() : studentRepository.findById(id);
    }

    public Optional<Student> getStudentByUsername(String username) {
        if (username == null) return Optional.empty();
        return userRepository.findByUsername(username)
                .flatMap(user -> studentRepository.findByUserId(user.getId()));
    }

    public StudentProfileDto getStudentProfile(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        StudentProfileDto dto = new StudentProfileDto();
        dto.setId(student.getId());
        dto.setUsername(student.getUser().getUsername());
        dto.setFullName(student.getFullName());
        dto.setClassName(student.getClassName());
        dto.setCourses(student.getCourses().stream().map(Course::getName).collect(Collectors.toSet()));
        return dto;
    }

    public StudentProfileDto getStudentProfileByUsername(String username) {
        if (username == null) return null;
        return getStudentByUsername(username)
                .map(student -> getStudentProfile(student.getId()))
                .orElse(null);
    }

    public Student createStudent(CreateStudentRequest request) {
        User user = new User(request.getUsername(), request.getPassword(), Role.STUDENT);
        User savedUser = userRepository.saveAndFlush(user);
        
        Student student = new Student(savedUser, request.getFullName(), request.getClassName());
        if (request.getCourseIds() != null) {
            for (Long courseId : request.getCourseIds()) {
                if (courseId != null) {
                    courseRepository.findById(courseId).ifPresent(student.getCourses()::add);
                }
            }
        }
        return studentRepository.save(student);
    }

    // --- TEACHER METHODS ---
    public List<Teacher> listTeachers() {
        return teacherRepository.findAll();
    }

    public List<AdminTeacherDto> listTeacherDtos() {
        return teacherRepository.findAll().stream()
                .map(teacher -> new AdminTeacherDto(
                        teacher.getId(),
                        teacher.getFullName(),
                        teacher.getUser().getUsername()))
                .collect(Collectors.toList());
    }

    public Optional<Teacher> getTeacherByUsername(String username) {
        if (username == null) return Optional.empty();
        return userRepository.findByUsername(username)
                .flatMap(user -> teacherRepository.findByUserId(user.getId()));
    }

    public Optional<TeacherProfileDto> getTeacherProfileByUsername(String username) {
        return getTeacherByUsername(username).map(teacher -> {
            TeacherProfileDto dto = new TeacherProfileDto();
            dto.setId(teacher.getId());
            dto.setUsername(teacher.getUser().getUsername());
            dto.setFullName(teacher.getFullName());
            dto.setCourses(teacher.getCourses().stream().map(Course::getName).collect(Collectors.toSet()));
            return dto;
        });
    }

    public Teacher createTeacher(CreateTeacherRequest request) {
        User user = new User(request.getUsername(), request.getPassword(), Role.TEACHER);
        User savedUser = userRepository.saveAndFlush(user);
        
        Teacher teacher = new Teacher(savedUser, request.getFullName());
        return teacherRepository.save(teacher);
    }

    // --- COURSE METHODS ---
    public List<Course> listCourses() {
        return courseRepository.findAll();
    }

    public List<AdminCourseDto> listCourseDtos() {
        return courseRepository.findAll().stream()
                .map(course -> new AdminCourseDto(
                        course.getId(),
                        course.getName(),
                        course.getTeacher().getFullName()))
                .collect(Collectors.toList());
    }

    public Optional<Course> getCourseById(Long courseId) {
        return courseId == null ? Optional.empty() : courseRepository.findById(courseId);
    }

    public List<CourseDto> getCoursesForStudent(Long studentId) {
        if (studentId == null) return Collections.emptyList();
        return studentRepository.findById(studentId)
                .map(student -> student.getCourses().stream().map(course -> {
                    CourseDto dto = new CourseDto();
                    dto.setId(course.getId());
                    dto.setName(course.getName());
                    dto.setTeacherName(course.getTeacher().getFullName());
                    return dto;
                }).collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    // FIXED: Formatted explicitly into a conditional expression block to completely bypass NetBeans type-inference issues
    public List<Student> listStudentsByCourse(Long courseId) {
        if (courseId == null) {
            return new ArrayList<>();
        }
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isPresent()) {
            return new ArrayList<>(courseOpt.get().getStudents());
        }
        return new ArrayList<>();
    }

    public Course createCourse(CreateCourseRequest request) {
        if (request.getTeacherId() == null) {
            throw new IllegalArgumentException("Teacher ID is required");
        }
        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        Course course = new Course(request.getName(), teacher);
        if (request.getStudentIds() != null) {
            for (Long studentId : request.getStudentIds()) {
                if (studentId != null) {
                    studentRepository.findById(studentId).ifPresent(course.getStudents()::add);
                }
            }
        }
        return courseRepository.save(course);
    }

    // --- GRADES & ATTENDANCE ---
    public Grade saveGrade(Student student, Course course, String gradeValue, String remarks) {
        return gradeRepository.save(new Grade(student, course, gradeValue, remarks));
    }

    public List<Grade> getGradesForStudent(Long studentId) {
        return studentId == null ? Collections.emptyList() : gradeRepository.findByStudentId(studentId);
    }

    public Attendance saveAttendance(Student student, Course course, LocalDate date, boolean present) {
        return attendanceRepository.save(new Attendance(student, course, date, present));
    }

    public List<Attendance> getAttendanceForStudent(Long studentId) {
        return studentId == null ? Collections.emptyList() : attendanceRepository.findByStudentId(studentId);
    }

    // --- ANNOUNCEMENTS ---
    public List<AnnouncementDto> listAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ann -> new AnnouncementDto(
                        ann.getId(),
                        ann.getTitle(),
                        ann.getContent(),
                        ann.getCreatedAt(),
                        ann.getAuthor().getUsername()))
                .collect(Collectors.toList());
    }

    public Announcement createAnnouncement(String title, String content, User author) {
        return announcementRepository.save(new Announcement(title, content, author));
    }

    public List<Map<String, Object>> getPublicAnnouncements() {
        return announcementRepository.findAll().stream()
                .map(ann -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("title", ann.getTitle());
                    map.put("content", ann.getContent());
                    map.put("createdAt", ann.getCreatedAt());
                    map.put("authorName", ann.getAuthor().getUsername());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // --- FEES ---
    public List<FeeDto> getFeesForStudent(Long studentId) {
        if (studentId == null) return Collections.emptyList();
        return feeRepository.findByStudentId(studentId).stream()
                .map(fee -> new FeeDto(
                        fee.getId(),
                        fee.getDescription(),
                        fee.getAmount(),
                        fee.isPaid(),
                        fee.getDueDate(),
                        fee.getPaidDate()))
                .collect(Collectors.toList());
    }

    public Fee createFee(Student student, String description, BigDecimal amount, LocalDate dueDate) {
        return feeRepository.save(new Fee(student, description, amount, dueDate));
    }

    public void markFeePaid(Long feeId) {
        feeRepository.findById(feeId).ifPresent(fee -> {
            fee.setPaid(true);
            fee.setPaidDate(LocalDate.now());
            feeRepository.save(fee);
        });
    }

    // --- COURSE MATERIALS ---
    public List<CourseMaterialDto> getMaterialsForCourse(Long courseId) {
        if (courseId == null) return Collections.emptyList();
        return courseMaterialRepository.findByCourseId(courseId).stream()
                .map(mat -> new CourseMaterialDto(
                        mat.getId(),
                        mat.getTitle(),
                        mat.getDescription(),
                        mat.getFileUrl(),
                        mat.getUploadedAt(),
                        mat.getUploadedBy().getUsername()))
                .collect(Collectors.toList());
    }

    public CourseMaterial createMaterial(Course course, String title, String description, String fileUrl, User uploadedBy) {
        return courseMaterialRepository.save(new CourseMaterial(course, title, description, fileUrl, uploadedBy));
    }

    // --- MESSAGES ---
    public Message sendMessage(User sender, User receiver, String subject, String content) {
        return messageRepository.save(new Message(sender, receiver, subject, content));
    }

    public List<Message> getReceivedMessages(Long userId) {
        return userId == null ? Collections.emptyList() : messageRepository.findByReceiverIdOrderBySentAtDesc(userId);
    }

    public List<Message> getSentMessages(Long userId) {
        return userId == null ? Collections.emptyList() : messageRepository.findBySenderIdOrderBySentAtDesc(userId);
    }

    public void markMessageRead(Long messageId) {
        messageRepository.findById(messageId).ifPresent(message -> {
            message.setRead(true);
            messageRepository.save(message);
        });
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