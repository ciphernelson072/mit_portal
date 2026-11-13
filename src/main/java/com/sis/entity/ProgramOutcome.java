package com.sis.entity;

import javax.persistence.*;

@Entity
@Table(name = "program_outcomes", uniqueConstraints = @UniqueConstraint(name = "uk_program_outcome_code", columnNames = {"program_id", "code"}))
public class ProgramOutcome {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "program_id", nullable = false) private Program program;
    @Column(nullable = false, length = 30) private String code;
    @Column(nullable = false, length = 1000) private String description;
    @Column(nullable = false) private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Program getProgram() { return program; }
    public void setProgram(Program program) { this.program = program; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}