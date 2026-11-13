package com.sis.dto;

import java.time.LocalDate;
import java.util.List;

public class StudentTransferOptionsDto {
    private final List<ProgramOption> programs;
    private final List<AcademicYearOption> academicYears;

    public StudentTransferOptionsDto(List<ProgramOption> programs, List<AcademicYearOption> academicYears) {
        this.programs = programs;
        this.academicYears = academicYears;
    }

    public List<ProgramOption> getPrograms() { return programs; }
    public List<AcademicYearOption> getAcademicYears() { return academicYears; }

    public static class ProgramOption {
        private final Long id;
        private final String code;
        private final String name;
        private final String departmentName;

        public ProgramOption(Long id, String code, String name, String departmentName) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.departmentName = departmentName;
        }

        public Long getId() { return id; }
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getDepartmentName() { return departmentName; }
    }

    public static class AcademicYearOption {
        private final Long id;
        private final String name;
        private final LocalDate startDate;
        private final LocalDate endDate;

        public AcademicYearOption(Long id, String name, LocalDate startDate, LocalDate endDate) {
            this.id = id;
            this.name = name;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
    }
}
