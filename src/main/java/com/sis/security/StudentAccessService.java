package com.sis.security;

import com.sis.repository.CourseRepository;
import com.sis.repository.CourseRegistrationRepository;
import com.sis.repository.StudentRepository;
import com.sis.entity.RegistrationStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Referenced from @PreAuthorize SpEL expressions (e.g. "@studentAccessService.isOwnStudent(#id)")
 * so a STUDENT can access their own resources (profile image, academic report) without
 * needing the broader ADMIN/TEACHER authority.
 *
 * This bean was referenced by several @PreAuthorize expressions in the original codebase
 * but never actually implemented, which meant every self-access branch failed at
 * evaluation time - students could not view their own report or upload their own photo.
 */
@Component("studentAccessService")
public class StudentAccessService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CourseRegistrationRepository registrations;

    public StudentAccessService(StudentRepository studentRepository, CourseRepository courseRepository,
                                CourseRegistrationRepository registrations) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.registrations = registrations;
    }

    public boolean isOwnStudent(Long studentId) {
        if (studentId == null) {
            return false;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
        if (isAdmin) {
            return true;
        }

        String username = authentication.getName();
        return studentRepository.findByUserUsername(username)
                .map(student -> student.getId().equals(studentId))
                .orElse(false);
    }

    public boolean isEnrolledInCourse(Long courseId) {
        if (courseId == null) {
            return false;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
        if (isAdmin) {
            return true;
        }

        String username = authentication.getName();
        return studentRepository.findByUserUsername(username)
                .map(student -> courseRepository.existsEnrolledStudent(courseId, student.getId())
                        || registrations.existsByStudentIdAndSectionCourseIdAndStatus(
                                student.getId(), courseId, RegistrationStatus.REGISTERED))
                .orElse(false);
    }
}
