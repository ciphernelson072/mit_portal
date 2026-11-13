package com.sis.controller;

import com.sis.repository.AcademicTermRepository;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.CampusRepository;
import com.sis.repository.DepartmentRepository;
import com.sis.repository.FacultyRepository;
import com.sis.repository.ProgramRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/institution")
@PreAuthorize("isAuthenticated()")
public class InstitutionController {
    private final CampusRepository campuses;
    private final FacultyRepository faculties;
    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final AcademicYearRepository academicYears;
    private final AcademicTermRepository academicTerms;

    public InstitutionController(CampusRepository campuses, FacultyRepository faculties,
                                 DepartmentRepository departments, ProgramRepository programs,
                                 AcademicYearRepository academicYears, AcademicTermRepository academicTerms) {
        this.campuses = campuses;
        this.faculties = faculties;
        this.departments = departments;
        this.programs = programs;
        this.academicYears = academicYears;
        this.academicTerms = academicTerms;
    }

    @GetMapping("/campuses")
    public List<Map<String, Object>> campuses() {
        return campuses.findByActiveTrueOrderByNameAsc().stream()
                .map(item -> map("id", item.getId(), "code", item.getCode(), "name", item.getName()))
                .toList();
    }

    @GetMapping("/faculties")
    public List<Map<String, Object>> faculties(@RequestParam Long campusId) {
        return faculties.findByCampusIdInActiveCampusOrderByNameAsc(campusId).stream()
                .map(item -> map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                        "campusId", item.getCampus().getId()))
                .toList();
    }

    @GetMapping("/departments")
    public List<Map<String, Object>> departments(@RequestParam Long facultyId) {
        return departments.findByFacultyInActiveDirectoryOrderByNameAsc(facultyId).stream()
                .map(item -> map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                        "facultyId", item.getFaculty().getId()))
                .toList();
    }

    @GetMapping("/programs")
    public List<Map<String, Object>> programs(@RequestParam Long departmentId) {
        return programs.findByDepartmentIdAndActiveDirectoryOrderByNameAsc(departmentId).stream()
                .map(item -> map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                        "awardTitle", item.getAwardTitle(), "durationYears", item.getDurationYears(),
                        "departmentId", item.getDepartment().getId()))
                .toList();
    }

    @GetMapping("/academic-years/current")
    public Map<String, Object> currentAcademicYear() {
        return academicYears.findFirstByActiveTrueOrderByStartDateDesc()
                .map(item -> map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                        "startDate", item.getStartDate(), "endDate", item.getEndDate()))
                .orElseGet(LinkedHashMap::new);
    }

    @GetMapping("/academic-terms/open")
    public List<Map<String, Object>> openTerms() {
        return academicTerms.findByRegistrationOpenTrueOrderByStartDateAsc().stream()
                .map(item -> map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                        "academicYearId", item.getAcademicYear().getId(), "startDate", item.getStartDate(),
                        "endDate", item.getEndDate()))
                .toList();
    }

        private Map<String, Object> map(Object... values) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (int index = 0; index < values.length; index += 2) {
                        result.put((String) values[index], values[index + 1]);
                }
                return result;
        }
}