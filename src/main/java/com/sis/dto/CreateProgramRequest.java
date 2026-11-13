package com.sis.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import com.sis.entity.ProgramAwardType;

public class CreateProgramRequest {
    @NotBlank
    @Size(max = 30)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Size(max = 150)
    private String awardTitle;

    @NotNull
    private Long departmentId;

    @NotNull
    @Min(1)
    private Integer durationYears;

    @NotNull
    private ProgramAwardType awardType;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAwardTitle() { return awardTitle; }
    public void setAwardTitle(String awardTitle) { this.awardTitle = awardTitle; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Integer getDurationYears() { return durationYears; }
    public void setDurationYears(Integer durationYears) { this.durationYears = durationYears; }
    public ProgramAwardType getAwardType() { return awardType; }
    public void setAwardType(ProgramAwardType awardType) { this.awardType = awardType; }
}
