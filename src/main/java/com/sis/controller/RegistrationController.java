package com.sis.controller;

import com.sis.dto.AutomaticCourseAssignmentSummary;
import com.sis.dto.CreateCourseSectionRequest;
import com.sis.entity.CourseRegistration;
import com.sis.entity.CourseSection;
import com.sis.entity.Teacher;
import com.sis.service.AutomaticCourseAssignmentService;
import com.sis.service.RegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registration")
@PreAuthorize("isAuthenticated()")
public class RegistrationController {
    private final RegistrationService registrationService;
    private final AutomaticCourseAssignmentService automaticCourseAssignments;

    public RegistrationController(RegistrationService registrationService,
                                  AutomaticCourseAssignmentService automaticCourseAssignments) {
        this.registrationService = registrationService;
        this.automaticCourseAssignments = automaticCourseAssignments;
    }

    @GetMapping("/sections")
    @PreAuthorize("@permissionSecurity.canViewRegistrationSections()")
    public List<Map<String, Object>> sections(@RequestParam Long termId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isStudent = authentication.getAuthorities().stream()
                .anyMatch(authority -> "STUDENT".equals(authority.getAuthority()));
        List<CourseSection> availableSections = isStudent
                ? registrationService.studentSections(authentication.getName(), termId)
                : registrationService.sections(termId);
        Map<Long, Long> registeredCounts = registrationService.registeredCounts(availableSections);
        return availableSections.stream()
                .map(section -> sectionResponse(section, registeredCounts.getOrDefault(section.getId(), 0L)))
                .toList();
    }

    @PostMapping("/sections")
    @PreAuthorize("@permissionSecurity.canManageRegistration()")
    public ResponseEntity<Map<String, Object>> createSection(@Valid @RequestBody CreateCourseSectionRequest request) {
        CourseSection section = registrationService.createSection(request);
        AutomaticCourseAssignmentSummary assignments = null;
        if (Boolean.TRUE.equals(section.getAcademicTerm().getRegistrationOpen())) {
            assignments = automaticCourseAssignments.assignActiveStudentsForTerm(
                    section.getAcademicTerm().getId());
        }
        long registeredCount = registrationService.registeredCounts(List.of(section))
                .getOrDefault(section.getId(), 0L);
        Map<String, Object> response = sectionResponse(section, registeredCount);
        if (assignments != null) response.put("automaticAssignments", assignments);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('STUDENT')")
    public List<Map<String, Object>> mine() {
        return registrationService.studentRegistrations(username()).stream()
                .map(this::registrationResponse).toList();
    }

    @PostMapping("/sections/{sectionId}")
    @PreAuthorize("hasAuthority('STUDENT')")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(@PathVariable Long sectionId) {
        CourseRegistration registration = registrationService.register(username(), sectionId);
        Map<String, Object> response = registrationResponse(registration);
        response.put("automaticAssignments",
                automaticCourseAssignments.assignStudentForOpenTerms(registration.getStudent().getId()));
        return ResponseEntity.status(201).body(response);
    }

    @DeleteMapping("/{registrationId}")
    @PreAuthorize("hasAuthority('STUDENT')")
    @Transactional
    public ResponseEntity<Void> drop(@PathVariable Long registrationId) {
        CourseRegistration droppedRegistration = registrationService.drop(username(), registrationId);
        automaticCourseAssignments.assignActiveStudentsForCourseInTerm(
                droppedRegistration.getStudent().getId(),
                droppedRegistration.getSection().getCourse().getId(),
                droppedRegistration.getSection().getAcademicTerm().getId());
        return ResponseEntity.noContent().build();
    }

    private String username() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Map<String, Object> sectionResponse(CourseSection section, long registeredCount) {
        long capacity = section.getCapacity();
        Teacher teacher = section.getCourse().getTeacher();
        boolean instructorAssigned = teacher != null && teacher.getUser() != null
                && Boolean.TRUE.equals(teacher.getUser().getIsActive());
        return map("id", section.getId(), "courseId", section.getCourse().getId(),
                "courseCode", section.getCourse().getCourseCode(), "courseName", section.getCourse().getName(),
                "credits", section.getCourse().getCredits(), "termId", section.getAcademicTerm().getId(),
                "termName", section.getAcademicTerm().getName(),
                "sectionCode", section.getSectionCode(), "capacity", capacity,
                "registeredCount", registeredCount, "seatsAvailable", Math.max(0L, capacity - registeredCount),
                "active", section.getActive(), "teacherName", instructorAssigned ? teacher.getFullName() : "",
                "instructorAssigned", instructorAssigned);
    }

    private Map<String, Object> registrationResponse(CourseRegistration registration) {
        Teacher teacher = registration.getSection().getCourse().getTeacher();
        boolean instructorAssigned = teacher != null && teacher.getUser() != null
                && Boolean.TRUE.equals(teacher.getUser().getIsActive());
        return map("id", registration.getId(), "sectionId", registration.getSection().getId(),
                "sectionCode", registration.getSection().getSectionCode(),
                "courseId", registration.getSection().getCourse().getId(),
                "courseCode", registration.getSection().getCourse().getCourseCode(),
                "courseName", registration.getSection().getCourse().getName(),
                "teacherName", instructorAssigned ? teacher.getFullName() : "",
                "instructorAssigned", instructorAssigned,
                "termId", registration.getSection().getAcademicTerm().getId(),
                "termName", registration.getSection().getAcademicTerm().getName(),
                "status", registration.getStatus(), "registeredAt", registration.getRegisteredAt());
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }
}