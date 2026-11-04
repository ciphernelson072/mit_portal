package com.sis.repository;

import com.sis.entity.AcademicYear;
import com.sis.entity.AdmissionApplication;
import com.sis.entity.AdmissionApplicationStatus;
import com.sis.entity.Campus;
import com.sis.entity.Department;
import com.sis.entity.Faculty;
import com.sis.entity.Program;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.StudentProgram;
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admission_application_fetch_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.open-in-view=false",
        "sis.sample-data.enabled=false"
})
class AdmissionApplicationRepositoryFetchTest {

    @Autowired
    private AdmissionApplicationRepository applications;

    @Autowired
    private CampusRepository campuses;

    @Autowired
    private FacultyRepository faculties;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private ProgramRepository programs;

    @Autowired
    private AcademicYearRepository academicYears;

    @Autowired
    private StudentRepository students;

    @Autowired
    private StudentProgramRepository studentPrograms;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void admissionReadQueriesLoadProgramAndAcademicYearForResponseMapping() {
        Campus campus = new Campus();
        campus.setCode("ADM");
        campus.setName("Admissions Campus");
        campus = campuses.saveAndFlush(campus);

        Faculty faculty = new Faculty();
        faculty.setCode("ADMF");
        faculty.setName("Admissions Faculty");
        faculty.setCampus(campus);
        faculty = faculties.saveAndFlush(faculty);

        Department department = new Department();
        department.setCode("ADMD");
        department.setName("Admissions Department");
        department.setFaculty(faculty);
        department = departments.saveAndFlush(department);

        Program program = new Program();
        program.setCode("ADMP");
        program.setName("Admissions Program");
        program.setAwardTitle("Bachelor of Admissions");
        program.setDepartment(department);
        program.setDurationYears(3);
        program = programs.saveAndFlush(program);

        Program inactiveProgram = new Program();
        inactiveProgram.setCode("ADMP2");
        inactiveProgram.setName("Inactive Admissions Program");
        inactiveProgram.setAwardTitle("Bachelor of Admissions");
        inactiveProgram.setDepartment(department);
        inactiveProgram.setDurationYears(3);
        inactiveProgram.setActive(false);
        programs.saveAndFlush(inactiveProgram);

        AcademicYear year = new AcademicYear();
        year.setCode("2026-AD");
        year.setName("2026 Admissions Year");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year = academicYears.saveAndFlush(year);

        AdmissionApplication application = new AdmissionApplication();
        application.setApplicationNumber("APP-ADMISSION-1");
        application.setApplicantName("Admission Applicant");
        application.setEmail("admission@example.test");
        application.setProgram(program);
        application.setAcademicYear(year);
        application.setStatus(AdmissionApplicationStatus.SUBMITTED);
        application = applications.saveAndFlush(application);
        Long applicationId = application.getId();

        AdmissionApplication statusMatch = applications.findByApplicationNumberAndEmail(
                "APP-ADMISSION-1", " ADMISSION@example.test ").orElseThrow();
        assertApplicationRelationsLoaded(statusMatch);
        assertThat(applications.findByApplicationNumberAndEmail(
                "APP-ADMISSION-1", "wrong@example.test")).isEmpty();

        AdmissionApplication unrelatedApplication = new AdmissionApplication();
        unrelatedApplication.setApplicationNumber("APP-ADMISSION-2");
        unrelatedApplication.setApplicantName("Different Applicant");
        unrelatedApplication.setEmail("different@example.test");
        unrelatedApplication.setProgram(program);
        unrelatedApplication.setAcademicYear(year);
        applications.saveAndFlush(unrelatedApplication);

        User studentUser = new User("admitted-student", "password", Role.STUDENT);
        Student enrolledStudent = new Student(studentUser, "Admission Applicant", "Admissions Program");
        enrolledStudent.setEmail(" ADMISSION@example.test ");
        enrolledStudent = students.saveAndFlush(enrolledStudent);

        StudentProgram enrollment = new StudentProgram();
        enrollment.setStudent(enrolledStudent);
        enrollment.setProgram(program);
        enrollment.setAcademicYear(year);
        studentPrograms.saveAndFlush(enrollment);

        assertThat(applications.findEnrolledApplicationIds()).containsExactly(applicationId);

        assertApplicationRelationsLoaded(applications.findByApplicationNumber("APP-ADMISSION-1").orElseThrow());
        assertApplicationRelationsLoaded(applications.findByIdWithProgramAndAcademicYear(applicationId).orElseThrow());
        assertApplicationRelationsLoaded(applications.findByStatusOrderBySubmittedAtAsc(
                AdmissionApplicationStatus.SUBMITTED).get(0));
        assertThat(programs.findActiveProgramsInActiveDirectoryOrderByName())
                .extracting(Program::getName)
                .containsExactly("Admissions Program");
        assertThat(faculties.findByCampusIdInActiveCampusOrderByNameAsc(campus.getId()))
                .extracting(Faculty::getName)
                .containsExactly("Admissions Faculty");
        assertThat(departments.findByFacultyInActiveDirectoryOrderByNameAsc(faculty.getId()))
                .extracting(Department::getName)
                .containsExactly("Admissions Department");
        assertThat(programs.existsActiveInDirectory(program.getId())).isTrue();

        department.setActive(false);
        departments.saveAndFlush(department);
        assertThat(programs.findActiveProgramsInActiveDirectoryOrderByName()).isEmpty();
        assertThat(programs.existsActiveInDirectory(program.getId())).isFalse();
        department.setActive(true);
        departments.saveAndFlush(department);

        Campus inactiveCampus = new Campus();
        inactiveCampus.setCode("ADM-INACTIVE-C");
        inactiveCampus.setName("Inactive Admissions Campus");
        inactiveCampus.setActive(false);
        inactiveCampus = campuses.saveAndFlush(inactiveCampus);
        Faculty inactiveFaculty = new Faculty();
        inactiveFaculty.setCode("ADM-INACTIVE-F");
        inactiveFaculty.setName("Inactive Admissions Faculty");
        inactiveFaculty.setCampus(inactiveCampus);
        inactiveFaculty = faculties.saveAndFlush(inactiveFaculty);
        Department inactiveDepartment = new Department();
        inactiveDepartment.setCode("ADM-INACTIVE-D");
        inactiveDepartment.setName("Inactive Admissions Department");
        inactiveDepartment.setFaculty(inactiveFaculty);
        inactiveDepartment = departments.saveAndFlush(inactiveDepartment);
        assertThat(faculties.findByCampusIdInActiveCampusOrderByNameAsc(inactiveCampus.getId())).isEmpty();
        assertThat(departments.findByFacultyInActiveDirectoryOrderByNameAsc(inactiveFaculty.getId())).isEmpty();
        Program inactiveAncestorProgram = new Program();
        inactiveAncestorProgram.setCode("ADM-INACTIVE-P");
        inactiveAncestorProgram.setName("Program in Inactive Campus");
        inactiveAncestorProgram.setAwardTitle("Bachelor of Admissions");
        inactiveAncestorProgram.setDepartment(inactiveDepartment);
        inactiveAncestorProgram.setDurationYears(3);
        programs.saveAndFlush(inactiveAncestorProgram);
        assertThat(programs.findActiveProgramsInActiveDirectoryOrderByName())
                .extracting(Program::getName)
                .containsExactly("Admissions Program");
        assertThat(programs.findByDepartmentIdAndActiveDirectoryOrderByNameAsc(inactiveDepartment.getId()))
                .isEmpty();

        List<AdmissionApplication> all = applications.findAllWithProgramAndAcademicYear();
        assertThat(all).hasSize(2);
        assertApplicationRelationsLoaded(all.get(0));

        AdmissionApplication duplicate = new AdmissionApplication();
        duplicate.setApplicationNumber("APP-ADMISSION-DUP");
        duplicate.setApplicantName("Duplicate Applicant");
        duplicate.setEmail(" ADMISSION@example.test ");
        duplicate.setProgram(program);
        duplicate.setAcademicYear(year);
        duplicate.setStatus(AdmissionApplicationStatus.SUBMITTED);
        assertThatThrownBy(() -> applications.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertApplicationRelationsLoaded(AdmissionApplication application) {
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(application, "program")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(application, "academicYear")).isTrue();
        assertThat(application.getProgram().getName()).isEqualTo("Admissions Program");
        assertThat(application.getAcademicYear().getName()).isEqualTo("2026 Admissions Year");
    }
}
