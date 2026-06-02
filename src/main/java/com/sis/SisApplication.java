package com.sis;

import com.sis.dto.*;
import com.sis.entity.Role;
import com.sis.service.SchoolService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class SisApplication {

    public static void main(String[] args) {
        SpringApplication.run(SisApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedData(SchoolService schoolService, PasswordEncoder passwordEncoder) {
        return args -> {
            try {
                // 1. Seed Admin
                schoolService.createUser("admin", passwordEncoder.encode("admin123"), Role.ADMIN);
                
                // 2. Seed Teacher (Using listTeacherDtos to match SchoolService)
                if (schoolService.listTeacherDtos().isEmpty()) {
                    CreateTeacherRequest tr = new CreateTeacherRequest();
                    tr.setUsername("teacher1");
                    tr.setPassword(passwordEncoder.encode("teacher123"));
                    tr.setFullName("Ms. Alvarez");
                    schoolService.createTeacher(tr);
                }
                
                // 3. Seed Student (Using listStudentDtos to match SchoolService)
                if (schoolService.listStudentDtos().isEmpty()) {
                    CreateStudentRequest sr = new CreateStudentRequest();
                    sr.setUsername("student1");
                    sr.setPassword(passwordEncoder.encode("student123"));
                    sr.setFullName("John Doe");
                    sr.setClassName("10A");
                    schoolService.createStudent(sr);
                }
                
                // 4. Seed Course
                if (schoolService.listCourseDtos().isEmpty()) {
                    CreateCourseRequest cr = new CreateCourseRequest();
                    cr.setName("Mathematics");
                    schoolService.getTeacherByUsername("teacher1").ifPresent(t -> {
                        cr.setTeacherId(t.getId());
                        schoolService.createCourse(cr);
                    });
                }
                System.out.println(">>> Seeding completed successfully.");
            } catch (Exception e) {
                System.out.println(">>> Seeding info: " + e.getMessage());
            }
        };
    }
}