package com.sis.controller;

import com.sis.dto.CreateCurriculumCourseRequest;
import com.sis.dto.CreatePrerequisiteRequest;
import com.sis.entity.CoursePrerequisite;
import com.sis.entity.CurriculumCourse;
import com.sis.service.CurriculumService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/curriculum")
@PreAuthorize("isAuthenticated()")
public class CurriculumController {
    private final CurriculumService curriculumService;

    public CurriculumController(CurriculumService curriculumService) { this.curriculumService = curriculumService; }

    @PostMapping("/courses")
    @PreAuthorize("@permissionSecurity.canManageRegistration()")
    public ResponseEntity<Map<String, Object>> addCourse(@Valid @RequestBody CreateCurriculumCourseRequest request) {
        return ResponseEntity.status(201).body(courseResponse(curriculumService.addCourse(request)));
    }

    @PostMapping("/prerequisites")
    @PreAuthorize("@permissionSecurity.canManageRegistration()")
    public ResponseEntity<Map<String, Object>> addPrerequisite(@Valid @RequestBody CreatePrerequisiteRequest request) {
        return ResponseEntity.status(201).body(prerequisiteResponse(curriculumService.addPrerequisite(request)));
    }

    @GetMapping("/program/{programId}")
    public List<Map<String, Object>> program(@PathVariable Long programId) {
        return curriculumService.programCurriculum(programId).stream().map(this::courseResponse).toList();
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine() {
        return program(curriculumService.studentProgramId(username()));
    }

    @GetMapping("/course/{courseId}/prerequisites")
    public List<Map<String, Object>> prerequisites(@PathVariable Long courseId) {
        return curriculumService.prerequisites(courseId).stream().map(this::prerequisiteResponse).toList();
    }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }

    private Map<String, Object> courseResponse(CurriculumCourse item) {
        return map("id", item.getId(), "programId", item.getProgram().getId(), "courseId", item.getCourse().getId(),
                "courseCode", item.getCourse().getCourseCode(), "courseName", item.getCourse().getName(),
                "credits", item.getCourse().getCredits(), "semesterNumber", item.getSemesterNumber(), "required", item.getRequired());
    }

    private Map<String, Object> prerequisiteResponse(CoursePrerequisite item) {
        return map("id", item.getId(), "courseId", item.getCourse().getId(), "courseCode", item.getCourse().getCourseCode(),
                "prerequisiteCourseId", item.getPrerequisiteCourse().getId(), "prerequisiteCourseCode", item.getPrerequisiteCourse().getCourseCode());
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return result;
    }
}