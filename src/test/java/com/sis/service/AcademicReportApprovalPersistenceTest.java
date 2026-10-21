package com.sis.service;

import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AcademicReportApprovalRepository;
import com.sis.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.persistence.EntityManager;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
@Import(AcademicReportService.class)
class AcademicReportApprovalPersistenceTest {

    @Autowired
    private AcademicReportApprovalRepository approvalRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AcademicReportService academicReportService;

    @MockBean
    private StudentService studentService;

    @Test
    void approvalPersistsAndRemainsAvailableAfterReload() {
        Student student = studentRepository.saveAndFlush(
                new Student(new User("approval-student", "encoded", Role.STUDENT), "Approval Student", "Year 1"));
        given(studentService.getStudentById(student.getId())).willReturn(Optional.of(student));

        academicReportService.approveAcademicReport(student.getId(), "registrar");
        entityManager.flush();
        entityManager.clear();
        academicReportService.approveAcademicReport(student.getId(), "registrar-2");
        entityManager.flush();
        entityManager.clear();

        assertThat(academicReportService.isOfficiallyApproved(student.getId())).isTrue();
        assertThat(academicReportService.getApprovalStatus(student.getId()).getApprovedBy()).isEqualTo("registrar-2");
        assertThat(academicReportService.getApprovalStatus(student.getId()).getApprovedAt()).isNotBlank();
    }
}
