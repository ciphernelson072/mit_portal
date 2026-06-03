package com.sis.util;

import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
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
    private GradeRepository gradeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Check if sample data already exists - check for teacher1 instead of admin
        if (userRepository.findByUsername("teacher1").isPresent()) {
            logger.info("Sample data already exists. Skipping initialization.");
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
            student.setCgpa(new BigDecimal("3.5").subtract(new BigDecimal(i * 0.2)));
            student.setEnrollmentDate("2024-01-15");
            student.setAddress("123 Main St, City, State");
            students[i] = studentRepository.save(student);
        }

        logger.info("Students created");

        // Create Courses
        Course math = new Course();
        math.setName("Mathematics");
        math.setTeacher(teacher1);
        math.setSemester("Spring 2024");
        math.setCredits(4);
        courseRepository.save(math);

        Course english = new Course();
        english.setName("English Literature");
        english.setTeacher(teacher2);
        english.setSemester("Spring 2024");
        english.setCredits(3);
        courseRepository.save(english);

        logger.info("Courses created");

        // Create Grades
        String[] grades = {"A", "B+", "A-", "B", "C+"};
        BigDecimal[] gpaScores = {new BigDecimal("4.0"), new BigDecimal("3.3"), new BigDecimal("3.7"), new BigDecimal("3.0"), new BigDecimal("2.3")};

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
            grade2.setGpaScore(gpaScores[(i + 2) % 5]);
            grade2.setRemarks("Excellent writing skills");
            gradeRepository.save(grade2);
        }

        logger.info("Sample data initialization completed successfully!");
        logger.info("Test credentials:");
        logger.info("Admin - username: admin, password: admin123");
        logger.info("Teacher - username: teacher1, password: teacher123");
        logger.info("Student - username: student1, password: student123");
    }
}