package com.sis.controller;

import com.sis.dto.CreateAcademicYearRequest;
import com.sis.dto.CreateAcademicTermRequest;
import com.sis.dto.UpdateAcademicTermRegistrationWindowRequest;
import com.sis.dto.CreateCampusRequest;
import com.sis.dto.CreateDepartmentRequest;
import com.sis.dto.CreateFacultyRequest;
import com.sis.dto.CreateProgramRequest;
import com.sis.entity.AcademicYear;
import com.sis.entity.AcademicTerm;
import com.sis.entity.Campus;
import com.sis.entity.Department;
import com.sis.entity.Faculty;
import com.sis.entity.Program;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.CampusRepository;
import com.sis.repository.DepartmentRepository;
import com.sis.repository.FacultyRepository;
import com.sis.repository.ProgramRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/institution")
@PreAuthorize("@permissionSecurity.canManageAcademicOperations() or @permissionSecurity.canManageRegistration()")
public class InstitutionAdminController {
    private final CampusRepository campuses;
    private final FacultyRepository faculties;
    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final AcademicYearRepository academicYears;
    private final AcademicTermRepository academicTerms;

    public InstitutionAdminController(CampusRepository campuses, FacultyRepository faculties,
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

    @PostMapping("/campuses")
    public ResponseEntity<Map<String, Object>> createCampus(@Valid @RequestBody CreateCampusRequest request) {
        Campus campus = new Campus();
        campus.setCode(request.getCode().trim());
        campus.setName(request.getName().trim());
        campus.setActive(true);
        Campus saved = campuses.save(campus);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(map("id", saved.getId(), "code", saved.getCode(), "name", saved.getName()));
    }

    @GetMapping("/faculties")
    public List<Map<String, Object>> faculties(@RequestParam Long campusId) {
        requireActiveCampus(campusId);
        return faculties.findByCampusIdInActiveCampusOrderByNameAsc(campusId).stream()
                .map(item -> map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                        "campusId", item.getCampus().getId()))
                .toList();
    }

    @PostMapping("/faculties")
    public ResponseEntity<Map<String, Object>> createFaculty(@Valid @RequestBody CreateFacultyRequest request) {
        Campus campus = requireActiveCampus(request.getCampusId());
        Faculty faculty = new Faculty();
        faculty.setCode(request.getCode().trim());
        faculty.setName(request.getName().trim());
        faculty.setCampus(campus);
        faculty.setActive(true);
        Faculty saved = faculties.save(faculty);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(map("id", saved.getId(), "code", saved.getCode(), "name", saved.getName(),
                        "campusId", campus.getId()));
    }

    @GetMapping("/departments")
    public List<Map<String, Object>> departments(@RequestParam Long facultyId) {
        requireActiveFaculty(facultyId);
        return departments.findByFacultyInActiveDirectoryOrderByNameAsc(facultyId).stream()
                .map(item -> map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                        "facultyId", item.getFaculty().getId()))
                .toList();
    }

    @GetMapping("/departments/active")
    public List<Map<String, Object>> activeDepartments() {
        return departments.findAllActiveInActiveDirectory().stream()
                .map(item -> map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                        "facultyId", item.getFaculty().getId(), "facultyName", item.getFaculty().getName(),
                        "campusId", item.getFaculty().getCampus().getId(),
                        "campusName", item.getFaculty().getCampus().getName()))
                .toList();
    }

    @PostMapping("/departments")
    public ResponseEntity<Map<String, Object>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {
        Faculty faculty = requireActiveFaculty(request.getFacultyId());
        Department department = new Department();
        department.setCode(request.getCode().trim());
        department.setName(request.getName().trim());
        department.setFaculty(faculty);
        department.setActive(true);
        Department saved = departments.save(department);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(map("id", saved.getId(), "code", saved.getCode(), "name", saved.getName(),
                        "facultyId", faculty.getId()));
    }

    @GetMapping("/programs")
    public List<Map<String, Object>> programs(@RequestParam Long departmentId) {
        requireActiveDepartment(departmentId);
        return programs.findByDepartmentIdAndActiveDirectoryOrderByNameAsc(departmentId).stream()
                .map(this::programResponse)
                .toList();
    }

    @PostMapping("/programs")
    public ResponseEntity<Map<String, Object>> createProgram(@Valid @RequestBody CreateProgramRequest request) {
        Department department = requireActiveDepartment(request.getDepartmentId());
        Program program = new Program();
        program.setCode(request.getCode().trim());
        program.setName(request.getName().trim());
        program.setAwardTitle(request.getAwardTitle().trim());
        program.setDepartment(department);
        program.setDurationYears(request.getDurationYears());
        program.setActive(true);
        Program saved = programs.save(program);
        return ResponseEntity.status(HttpStatus.CREATED).body(programResponse(saved));
    }

    @GetMapping("/academic-years")
    public List<Map<String, Object>> academicYears() {
        return academicYears.findAllByOrderByStartDateDesc().stream()
                .map(this::academicYearResponse)
                .toList();
    }

    @PostMapping("/academic-years")
    @Transactional
    public ResponseEntity<Map<String, Object>> createAcademicYear(
            @Valid @RequestBody CreateAcademicYearRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Academic year end date must be on or after its start date.");
        }
        AcademicYear year = new AcademicYear();
        year.setCode(request.getCode().trim());
        year.setName(request.getName().trim());
        year.setStartDate(request.getStartDate());
        year.setEndDate(request.getEndDate());
        year.setActive(false);
        AcademicYear saved = academicYears.save(year);
        if (Boolean.TRUE.equals(request.getActivate())) {
            saved = activateAcademicYear(saved);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(academicYearResponse(saved));
    }

    @PatchMapping("/academic-years/{id}/activate")
    @Transactional
    public Map<String, Object> activateAcademicYear(@PathVariable Long id) {
        AcademicYear year = academicYears.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found: " + id));
        return academicYearResponse(activateAcademicYear(year));
    }

    @GetMapping("/academic-terms")
    public List<Map<String, Object>> academicTerms(@RequestParam Long academicYearId) {
        return academicTerms.findByAcademicYearIdOrderByStartDateAsc(academicYearId).stream()
                .map(this::academicTermResponse)
                .toList();
    }

    @PostMapping("/academic-terms")
    public ResponseEntity<Map<String, Object>> createAcademicTerm(
            @Valid @RequestBody CreateAcademicTermRequest request) {
        AcademicYear year = academicYears.findById(request.getAcademicYearId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Academic year not found: " + request.getAcademicYearId()));
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Academic term end date must be on or after its start date.");
        }
        if (request.getStartDate().isBefore(year.getStartDate())
                || request.getEndDate().isAfter(year.getEndDate())) {
            throw new IllegalArgumentException("Academic term dates must be within the selected academic year.");
        }
        String code = request.getCode().trim();
        if (academicTerms.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("An academic term with this code already exists.");
        }
        AcademicTerm term = new AcademicTerm();
        term.setCode(code);
        term.setName(request.getName().trim());
        term.setAcademicYear(year);
        term.setStartDate(request.getStartDate());
        term.setEndDate(request.getEndDate());
        term.setRegistrationOpen(request.getRegistrationOpen());
        AcademicTerm saved = academicTerms.save(term);
        return ResponseEntity.status(HttpStatus.CREATED).body(academicTermResponse(saved));
    }

    @PatchMapping("/academic-terms/{id}/registration-window")
    public Map<String, Object> updateAcademicTermRegistrationWindow(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAcademicTermRegistrationWindowRequest request) {
        AcademicTerm term = academicTerms.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Academic term not found: " + id));
        term.setRegistrationOpen(request.getRegistrationOpen());
        return academicTermResponse(academicTerms.save(term));
    }

    private AcademicYear activateAcademicYear(AcademicYear selectedYear) {
        academicYears.findAll().forEach(year -> {
            if (!year.getId().equals(selectedYear.getId()) && Boolean.TRUE.equals(year.getActive())) {
                year.setActive(false);
            }
        });
        selectedYear.setActive(true);
        return academicYears.save(selectedYear);
    }

    private Campus requireActiveCampus(Long id) {
        Campus campus = campuses.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campus not found: " + id));
        if (!Boolean.TRUE.equals(campus.getActive())) {
            throw new IllegalArgumentException("Select an active campus.");
        }
        return campus;
    }

    private Faculty requireActiveFaculty(Long id) {
        Faculty faculty = faculties.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty not found: " + id));
        if (!Boolean.TRUE.equals(faculty.getActive())
                || !faculties.existsActiveInActiveCampus(id)) {
            throw new IllegalArgumentException("Select a faculty under an active campus.");
        }
        return faculty;
    }

    private Department requireActiveDepartment(Long id) {
        Department department = departments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
        if (!Boolean.TRUE.equals(department.getActive())
                || !departments.existsActiveInActiveFacultyAndCampus(id)) {
            throw new IllegalArgumentException("Select a department under an active faculty and campus.");
        }
        return department;
    }

    private Map<String, Object> programResponse(Program item) {
        return map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                "awardTitle", item.getAwardTitle(), "durationYears", item.getDurationYears(),
                "departmentId", item.getDepartment().getId());
    }

    private Map<String, Object> academicYearResponse(AcademicYear item) {
        return map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                "startDate", item.getStartDate(), "endDate", item.getEndDate(), "active", item.getActive());
    }

    private Map<String, Object> academicTermResponse(AcademicTerm item) {
        return map("id", item.getId(), "code", item.getCode(), "name", item.getName(),
                "academicYearId", item.getAcademicYear().getId(),
                "academicYearCode", item.getAcademicYear().getCode(),
                "startDate", item.getStartDate(), "endDate", item.getEndDate(),
                "registrationOpen", item.getRegistrationOpen());
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }
}
