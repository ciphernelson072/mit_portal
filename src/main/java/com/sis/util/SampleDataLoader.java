package com.sis.util;

import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * Sample Data Loader - Initializes database with sample data for testing
 * Creates test users, students, teachers, courses, and grades
 * Enable by setting: sis.sample-data.enabled=true in properties
 */
@Component
@ConditionalOnProperty(name = "sis.sample-data.enabled", havingValue = "true")
public class SampleDataLoader implements CommandLineRunner {

    private static final Logger logger = Logger.getLogger(SampleDataLoader.class.getName());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseCategoryRepository courseCategoryRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Existing installations may contain only part of the demo catalog. Repair
        // the catalog invariants before deciding that seeding is complete.
        if (userRepository.findByUsernameIgnoreCase("teacher1").isPresent()) {
            repairDemoCatalog();
            logger.info("Sample users already exist. Demo catalog verified and repaired where necessary.");
            return;
        }

        logger.info("Initializing sample data...");

        // Create Admin User
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRole(Role.ADMIN);
        admin.setEmail("admin@school.com");
        admin.setPhone("+1234567890");
        admin.setIsActive(true);
        userRepository.save(admin);
        logger.info("Admin user created");

        // Create Teacher Users
        User teacher1User = new User();
        teacher1User.setUsername("teacher1");
        teacher1User.setPassword(passwordEncoder.encode("teacher123"));
        teacher1User.setRole(Role.TEACHER);
        teacher1User.setEmail("teacher1@school.com");
        teacher1User.setPhone("+1234567891");
        teacher1User.setIsActive(true);
        userRepository.save(teacher1User);

        User teacher2User = new User();
        teacher2User.setUsername("teacher2");
        teacher2User.setPassword(passwordEncoder.encode("teacher123"));
        teacher2User.setRole(Role.TEACHER);
        teacher2User.setEmail("teacher2@school.com");
        teacher2User.setPhone("+1234567892");
        teacher2User.setIsActive(true);
        userRepository.save(teacher2User);

        // Create Teacher Records
        Teacher teacher1 = new Teacher();
        teacher1.setUser(teacher1User);
        teacher1.setFullName("Mr. John Smith");
        teacher1.setEmail("teacher1@school.com");
        teacher1.setPhone("+1234567891");
        teacher1.setQualification("B.Sc in Mathematics, M.Sc in Education");
        teacher1.setYearsOfExperience(10);
        teacher1.setDepartment("Mathematics");
        teacherRepository.save(teacher1);

        Teacher teacher2 = new Teacher();
        teacher2.setUser(teacher2User);
        teacher2.setFullName("Ms. Sarah Johnson");
        teacher2.setEmail("teacher2@school.com");
        teacher2.setPhone("+1234567892");
        teacher2.setQualification("B.Sc in English, M.A in Literature");
        teacher2.setYearsOfExperience(8);
        teacher2.setDepartment("English");
        teacherRepository.save(teacher2);

        logger.info("Teachers created");

        // Create Student Users
        User[] studentUsers = new User[5];
        for (int i = 0; i < 5; i++) {
            User studentUser = new User();
            studentUser.setUsername("student" + (i + 1));
            studentUser.setPassword(passwordEncoder.encode("student123"));
            studentUser.setRole(Role.STUDENT);
            studentUser.setEmail("student" + (i + 1) + "@school.com");
            studentUser.setPhone("+123456789" + (i + 1));
            studentUser.setIsActive(true);
            studentUsers[i] = userRepository.save(studentUser);
        }

        // Create Student Records
        Student[] students = new Student[5];
        String[] names = {"Alice Johnson", "Bob Smith", "Charlie Brown", "Diana Prince", "Eve Wilson"};
        for (int i = 0; i < 5; i++) {
            Student student = new Student();
            student.setUser(studentUsers[i]);
            student.setFullName(names[i]);
            student.setClassName("Grade 10");
            student.setEmail("student" + (i + 1) + "@school.com");
            student.setPhone("+123456789" + (i + 1));
            student.setEnrollmentDate("2024-01-15");
            student.setRegistrationNumber("P-" + java.util.UUID.randomUUID());
            student.setAddress("123 Main St, City, State");
            Student savedStudent = studentRepository.save(student);
            savedStudent.setRegistrationNumber(String.format(
                    "MIT/%s/%05d",
                    savedStudent.getEnrollmentDate().substring(0, 4),
                    savedStudent.getId()
            ));
            students[i] = studentRepository.save(savedStudent);
        }

        logger.info("Students created");

        CourseCategory mathCategory = ensureCategory("MATH", "Mathematics");
        CourseCategory englishCategory = ensureCategory("ENG", "English");

        // Create Courses with all required fields to satisfy DB constraints
        Course math = new Course();
        math.setName("Mathematics");
        math.setCourseCode("MATH101");
        math.setCategory(mathCategory);
        math.setTeacher(teacher1);
        math.setSemester("Spring 2024");
        math.setCredits(4);
        math.getStudents().addAll(java.util.Arrays.asList(students));
        courseRepository.save(math);

        Course english = new Course();
        english.setName("English Literature");
        english.setCourseCode("ENG201");
        english.setCategory(englishCategory);
        english.setTeacher(teacher2);
        english.setSemester("Spring 2024");
        english.setCredits(3);
        english.getStudents().addAll(java.util.Arrays.asList(students));
        courseRepository.save(english);

        logger.info("Courses created");

        // Create Grades
        String[] grades = {"A", "B+", "B", "C+", "C"};
        BigDecimal[] gpaScores = {new BigDecimal("5.0"), new BigDecimal("4.5"), new BigDecimal("4.0"), new BigDecimal("3.5"), new BigDecimal("3.0")};

        for (int i = 0; i < 5; i++) {
            Grade grade1 = new Grade();
            grade1.setStudent(students[i]);
            grade1.setCourse(math);
            grade1.setGradeValue(grades[i]);
            grade1.setGpaScore(gpaScores[i]);
            grade1.setRemarks("Good performance in mathematics");
            gradeRepository.save(grade1);

            Grade grade2 = new Grade();
            grade2.setStudent(students[i]);
            grade2.setCourse(english);
            grade2.setGradeValue(grades[(i + 1) % 5]);
            grade2.setGpaScore(gpaScores[(i + 1) % 5]);
            grade2.setRemarks("Excellent writing skills");
            gradeRepository.save(grade2);
        }

        logger.info("Sample data initialization completed successfully!");
        // Demo account usernames only - intentionally not logging passwords, even for
        // seed/demo accounts. See README.md "Demo Credentials" for the actual values;
        // application logs are not a safe place for any password, real or seeded.
        logger.info("Demo accounts created: admin, teacher1, teacher2, student1-student5 (see README.md for credentials)");
    }

    private CourseCategory ensureCategory(String code, String name) {
        return courseCategoryRepository.findByCodeIgnoreCase(code)
                .orElseGet(() -> courseCategoryRepository.save(new CourseCategory(code, name)));
    }

    private void repairDemoCatalog() {
        CourseCategory otherCategory = ensureCategory("OTHER", "Other");
        ensureCategory("MATH", "Mathematics");
        ensureCategory("ENG", "English");

        for (Course course : courseRepository.findAll()) {
            if (course.getCategory() == null) {
                course.setCategory(otherCategory);
            }
            if (course.getCourseCode() == null || course.getCourseCode().isBlank()) {
                course.setCourseCode("COURSE-" + course.getId());
            }
            if (course.getCredits() == null || course.getCredits() <= 0) {
                course.setCredits(3);
            }
            if (course.getSemester() == null || course.getSemester().isBlank()) {
                course.setSemester("Current Semester");
            }
        }
        linkDemoStudentsToCourse("MATH101");
        linkDemoStudentsToCourse("ENG201");
        courseRepository.flush();
    }

    private void linkDemoStudentsToCourse(String courseCode) {
        courseRepository.findByCourseCode(courseCode).ifPresent(course -> {
            for (int i = 1; i <= 5; i++) {
                studentRepository.findByUserUsername("student" + i)
                        .ifPresent(course.getStudents()::add);
            }
            courseRepository.save(course);
        });
    }
}