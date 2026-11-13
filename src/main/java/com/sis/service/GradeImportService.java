package com.sis.service;

import com.sis.dto.AdminGradeDto;
import com.sis.dto.AdminGradeImportRequest;
import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.Student;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GradeImportService {

    private final SchoolService schoolService;
    private final AuditLogRepository auditLogRepository;
    private final GradeRepository gradeRepository;
    private final UserRepository userRepository;

    public GradeImportService(SchoolService schoolService, AuditLogRepository auditLogRepository,
                              GradeRepository gradeRepository,
                              UserRepository userRepository) {
        this.schoolService = schoolService;
        this.auditLogRepository = auditLogRepository;
        this.gradeRepository = gradeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public List<AdminGradeDto> importGrades(MultipartFile file, String importedBy) {
        Long actorId = actorId(importedBy);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Grade import file is required.");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<AdminGradeImportRequest> rows;
        if (original.endsWith(".csv")) {
            rows = parseCsv(file);
        } else if (original.endsWith(".xls") || original.endsWith(".xlsx")) {
            rows = parseExcel(file);
        } else {
            throw new IllegalArgumentException("Unsupported grade import format. Use CSV, XLS, or XLSX.");
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Grade import file must contain at least one data row.");
        }

        Set<String> importKeys = new HashSet<>();
        for (AdminGradeImportRequest row : rows) {
            validate(row);
            String importKey = row.getStudentId() + ":" + row.getCourseId();
            if (!importKeys.add(importKey)) {
                throw new IllegalArgumentException("Duplicate grade row for student " + row.getStudentId()
                        + " and course " + row.getCourseId() + ".");
            }
        }

        List<PreparedImport> preparedRows = rows.stream().map(this::prepare).toList();
        List<AdminGradeDto> output = new ArrayList<>();
        for (PreparedImport prepared : preparedRows) {
            Grade grade = saveImportedGrade(prepared, importedBy);
            auditImport(actorId, "GRADE_IMPORTED_FILE", grade, prepared.student(), prepared.course());
            output.add(toDto(grade, prepared.student(), prepared.course()));
        }
        return output;
    }

    @Transactional
    public AdminGradeDto importGrade(AdminGradeImportRequest row, String importedBy) {
        Long actorId = actorId(importedBy);
        validate(row);
        PreparedImport prepared = prepare(row);
        Grade grade = saveImportedGrade(prepared, importedBy);
        auditImport(actorId, "GRADE_IMPORTED", grade, prepared.student(), prepared.course());
        return toDto(grade, prepared.student(), prepared.course());
    }

    private Long actorId(String importedBy) {
        return userRepository.findByUsernameNormalized(importedBy)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + importedBy))
                .getId();
    }

    private PreparedImport prepare(AdminGradeImportRequest row) {
        BigDecimal calculatedGpa = GradeCalculationService.gradeToGpa(row.getGradeValue());
        if (row.getGpaScore() != null && row.getGpaScore().compareTo(calculatedGpa) != 0) {
            throw new IllegalArgumentException("Imported GPA score does not match the configured grade mapping.");
        }
        Student student = schoolService.getStudentById(row.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + row.getStudentId()));
        Course course = schoolService.getCourseById(row.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + row.getCourseId()));
        if (!schoolService.studentEnrolledInCourse(student.getId(), course.getId())) {
            throw new IllegalArgumentException("Student " + student.getId()
                    + " is not enrolled in course " + course.getId() + ".");
        }
        if (gradeRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new IllegalArgumentException("A grade attempt already exists for student " + student.getId()
                    + " and course " + course.getId() + ". Use the correction or retake workflow instead.");
        }
        row.setGradeValue(row.getGradeValue().trim().toUpperCase(Locale.ROOT));
        return new PreparedImport(row, student, course);
    }

    private Grade saveImportedGrade(PreparedImport prepared, String importedBy) {
        return schoolService.saveGrade(prepared.student(), prepared.course(),
                prepared.row().getGradeValue(), prepared.row().getRemarks(), importedBy);
    }

    private void auditImport(Long actorId, String action, Grade grade, Student student, Course course) {
        auditLogRepository.save(new com.sis.entity.AuditLog(actorId, action,
                "Grade " + grade.getId() + " imported for student " + student.getId()
                        + " in course " + course.getId()));
    }

    private AdminGradeDto toDto(Grade grade, Student student, Course course) {
        return new AdminGradeDto(
                grade.getId(),
                student.getId(),
                student.getFullName(),
                student.getUser().getUsername(),
                course.getId(),
                course.getName(),
                grade.getGradeValue(),
                grade.getGpaScore(),
                grade.getRemarks(),
                grade.getStatus() != null ? grade.getStatus().name() : "PENDING",
                grade.getSubmittedBy(),
                grade.getApprovedBy(),
                grade.getRejectedBy(),
                grade.getRejectionReason(),
                grade.getTotalMark(),
                grade.getRetakeRequired(),
                grade.getGradingPolicyVersion());
    }

    private void validate(AdminGradeImportRequest row) {
        if (row == null) {
            throw new IllegalArgumentException("Grade import row is required.");
        }
        if (row.getStudentId() == null) {
            throw new IllegalArgumentException("studentId is required in each grade import row.");
        }
        if (row.getCourseId() == null) {
            throw new IllegalArgumentException("courseId is required in each grade import row.");
        }
        if (row.getGradeValue() == null || row.getGradeValue().isBlank()) {
            throw new IllegalArgumentException("gradeValue is required in each grade import row.");
        }
    }

    private record PreparedImport(AdminGradeImportRequest row, Student student, Course course) {
    }

    private List<AdminGradeImportRequest> parseCsv(MultipartFile file) {
        List<AdminGradeImportRequest> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> records = csvRecords(reader);
            if (records.isEmpty()) {
                throw new IllegalArgumentException("Grade import CSV must include a header row.");
            }
            String[] headers = records.get(0);
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                index.put(normalize(headers[i]), i);
            }
            validateHeaders(index);

            for (int i = 1; i < records.size(); i++) {
                String[] values = records.get(i);
                AdminGradeImportRequest row = new AdminGradeImportRequest();
                row.setStudentId(toLong(getColumn(index, values, "studentid", "student_id", "student")));
                row.setCourseId(toLong(getColumn(index, values, "courseid", "course_id", "course")));
                row.setGradeValue(getColumn(index, values, "gradevalue", "grade_value", "grade"));
                row.setGpaScore(parseBigDecimal(getColumn(index, values, "gpascore", "gpa_score", "gpa")));
                row.setRemarks(getColumn(index, values, "remarks", "remark", "comments"));
                rows.add(row);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read grade import file.", ex);
        }
        return rows;
    }

    private List<String[]> csvRecords(BufferedReader reader) throws IOException {
        List<String[]> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean firstCharacter = true;
        int current;

        while ((current = reader.read()) != -1) {
            char character = (char) current;
            if (firstCharacter) {
                firstCharacter = false;
                if (character == '\uFEFF') continue;
            }

            if (quoted) {
                if (character == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (next != -1) reader.reset();
                    }
                } else {
                    field.append(character);
                }
                continue;
            }

            if (character == '"') {
                if (field.length() > 0) {
                    throw new IllegalArgumentException("Invalid quote in grade import CSV.");
                }
                quoted = true;
            } else if (character == ',') {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else if (character == '\r' || character == '\n') {
                fields.add(field.toString().trim());
                addCsvRecord(records, fields);
                fields = new ArrayList<>();
                field.setLength(0);
                if (character == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.reset();
                }
            } else {
                field.append(character);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Unclosed quoted field in grade import CSV.");
        }
        if (!fields.isEmpty() || field.length() > 0) {
            fields.add(field.toString().trim());
            addCsvRecord(records, fields);
        }
        return records;
    }

    private void addCsvRecord(List<String[]> records, List<String> fields) {
        if (fields.size() == 1 && fields.get(0).isBlank()) return;
        records.add(fields.toArray(new String[0]));
    }

    private List<AdminGradeImportRequest> parseExcel(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             Workbook workbook = file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xls")
                     ? new HSSFWorkbook(input)
                     : new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("Grade import workbook is empty.");
            }

            Row header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() <= 0) {
                throw new IllegalArgumentException("Grade import workbook must include a header row.");
            }
            Map<String, Integer> index = new HashMap<>();
            DataFormatter formatter = new DataFormatter();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell cell = header.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String headerName = cell == null ? "" : formatter.formatCellValue(cell);
                index.put(normalize(headerName), i);
            }
            validateHeaders(index);

            List<AdminGradeImportRequest> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                boolean blank = true;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null && formatter.formatCellValue(cell).trim().length() > 0) {
                        blank = false;
                        break;
                    }
                }
                if (blank) continue;

                AdminGradeImportRequest request = new AdminGradeImportRequest();
                request.setStudentId(toLong(getExcelCell(formatter, row, index, "studentid", "student_id", "student")));
                request.setCourseId(toLong(getExcelCell(formatter, row, index, "courseid", "course_id", "course")));
                request.setGradeValue(getExcelCell(formatter, row, index, "gradevalue", "grade_value", "grade"));
                request.setGpaScore(parseBigDecimal(getExcelCell(formatter, row, index, "gpascore", "gpa_score", "gpa")));
                request.setRemarks(getExcelCell(formatter, row, index, "remarks", "remark", "comments"));
                rows.add(request);
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to parse grade import workbook.", ex);
        }
    }

    private void validateHeaders(Map<String, Integer> index) {
        requireHeader(index, "studentId", "student_id", "student");
        requireHeader(index, "courseId", "course_id", "course");
        requireHeader(index, "gradeValue", "grade_value", "grade");
    }

    private void requireHeader(Map<String, Integer> index, String... aliases) {
        if (Arrays.stream(aliases).map(this::normalize).noneMatch(index::containsKey)) {
            throw new IllegalArgumentException("Grade import file is missing a required column: " + aliases[0]);
        }
    }

    private String getColumn(Map<String, Integer> index, String[] values, String... aliases) {
        for (String alias : aliases) {
            Integer pos = index.get(alias);
            if (pos != null && pos < values.length) {
                return values[pos];
            }
        }
        return null;
    }

    private String getExcelCell(DataFormatter formatter, Row row, Map<String, Integer> index, String... aliases) {
        for (String alias : aliases) {
            Integer pos = index.get(alias);
            if (pos != null) {
                Cell cell = row.getCell(pos, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null) {
                    return formatter.formatCellValue(cell);
                }
            }
        }
        return null;
    }

    private Long toLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        return new BigDecimal(value.trim());
    }

    private String normalize(String name) {
        return name == null ? "" : name.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT)
                .replace(" ", "_").replace("-", "_");
    }
}
