package com.sis.repository;

import com.sis.entity.AccreditationRecord;
import com.sis.entity.AccreditationStatus;
import com.sis.entity.Campus;
import com.sis.entity.Department;
import com.sis.entity.Faculty;
import com.sis.entity.Program;
import com.sis.entity.ProgramOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.open-in-view=false",
        "sis.sample-data.enabled=false"
})
class QualityRepositoryFetchTest {

    @Autowired
    private CampusRepository campuses;

    @Autowired
    private FacultyRepository faculties;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private ProgramRepository programs;

    @Autowired
    private ProgramOutcomeRepository outcomes;

    @Autowired
    private AccreditationRecordRepository accreditations;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void qualityEntitiesSerializeAfterTransactionsClose() throws Exception {
        Program program = createProgram();

        ProgramOutcome outcome = new ProgramOutcome();
        outcome.setProgram(program);
        outcome.setCode("OUT-1");
        outcome.setDescription("Graduate outcome");
        outcomes.saveAndFlush(outcome);

        AccreditationRecord accreditation = new AccreditationRecord();
        accreditation.setProgram(program);
        accreditation.setAccreditingBody("Quality Board");
        accreditation.setStatus(AccreditationStatus.SUBMITTED);
        accreditation = accreditations.saveAndFlush(accreditation);
        Long accreditationId = accreditation.getId();

        List<ProgramOutcome> programOutcomes = outcomes.findByProgramIdAndActiveTrueOrderByCodeAsc(program.getId());
        assertThat(programOutcomes).hasSize(1);
        assertProgramDirectoryLoaded(programOutcomes.get(0).getProgram());
        assertThat(objectMapper.writeValueAsString(programOutcomes.get(0))).contains("Quality Campus");

        List<AccreditationRecord> pending = accreditations
                .findByStatusOrderByValidUntilAsc(AccreditationStatus.SUBMITTED);
        assertThat(pending).hasSize(1);
        assertProgramDirectoryLoaded(pending.get(0).getProgram());
        assertThat(objectMapper.writeValueAsString(pending.get(0))).contains("Quality Campus");

        AccreditationRecord byProgram = accreditations.findByProgramIdOrderByValidUntilDesc(program.getId()).get(0);
        assertProgramDirectoryLoaded(byProgram.getProgram());
        assertThat(objectMapper.writeValueAsString(byProgram)).contains("Quality Campus");

        AccreditationRecord byId = accreditations.findByIdWithProgramDirectory(accreditationId).orElseThrow();
        assertProgramDirectoryLoaded(byId.getProgram());
        assertThat(objectMapper.writeValueAsString(byId)).contains("Quality Campus");
        assertProgramDirectoryLoaded(programs.findByIdWithDirectory(program.getId()).orElseThrow());
    }

    private Program createProgram() {
        Campus campus = new Campus();
        campus.setCode("QLT");
        campus.setName("Quality Campus");
        campus = campuses.saveAndFlush(campus);

        Faculty faculty = new Faculty();
        faculty.setCode("QLTF");
        faculty.setName("Quality Faculty");
        faculty.setCampus(campus);
        faculty = faculties.saveAndFlush(faculty);

        Department department = new Department();
        department.setCode("QLTD");
        department.setName("Quality Department");
        department.setFaculty(faculty);
        department = departments.saveAndFlush(department);

        Program program = new Program();
        program.setCode("QLTP");
        program.setName("Quality Program");
        program.setAwardTitle("Quality Award");
        program.setDepartment(department);
        program.setDurationYears(3);
        return programs.saveAndFlush(program);
    }

    private void assertProgramDirectoryLoaded(Program program) {
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(program, "department")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(program.getDepartment(), "faculty")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(program.getDepartment().getFaculty(), "campus")).isTrue();
        assertThat(program.getDepartment().getFaculty().getCampus().getName()).isEqualTo("Quality Campus");
    }
}
