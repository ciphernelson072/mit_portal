package com.sis.security;

import com.sis.repository.CourseRepository;
import com.sis.repository.CourseRegistrationRepository;
import com.sis.repository.TeacherRepository;
import com.sis.entity.RegistrationStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Referenced from @PreAuthorize SpEL expressions so a TEACHER can access their own
 * resources without needing the broader ADMIN authority. See StudentAccessService for
 * why this bean needed to exist.
 */
@Component("teacherAccessService")
public class TeacherAccessService {

    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final CourseRegistrationRepository registrations;

    public TeacherAccessService(TeacherRepository teacherRepository, CourseRepository courseRepository,
                                CourseRegistrationRepository registrations) {
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
        this.registrations = registrations;
    }

    public boolean isOwnTeacher(Long teacherId) {
        if (teacherId == null) {
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
        return teacherRepository.findByUserUsername(username)
                .map(teacher -> teacher.getId().equals(teacherId))
                .orElse(false);
    }

    public boolean teachesCourse(Long courseId) {
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
        return teacherRepository.findByUserUsername(username)
                .map(teacher -> courseRepository.findById(courseId)
                        .map(course -> course.getTeacher() != null && course.getTeacher().getId().equals(teacher.getId()))
                        .orElse(false))
                .orElse(false);
    }

    public boolean teachesStudent(Long studentId) {
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

        return teacherRepository.findByUserUsername(authentication.getName())
                .map(teacher -> courseRepository.existsAssignedStudent(teacher.getId(), studentId)
                        || registrations.existsBySectionCourseTeacherIdAndStudentIdAndStatus(
                                teacher.getId(), studentId, RegistrationStatus.REGISTERED))
                .orElse(false);
    }

    public boolean teachesStudentInCourse(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
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
        return teacherRepository.findByUserUsername(username)
                .map(teacher -> courseRepository.existsByIdAndTeacherUserUsername(courseId, username)
                        && courseRepository.existsEnrolledStudent(courseId, studentId))
                .orElse(false);
    }
}
