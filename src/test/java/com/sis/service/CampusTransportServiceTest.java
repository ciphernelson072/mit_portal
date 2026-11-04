package com.sis.service;

import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.TransportPass;
import com.sis.entity.User;
import com.sis.repository.StudentRepository;
import com.sis.repository.TransportPassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
@Import(CampusTransportService.class)
class CampusTransportServiceTest {
    @Autowired private CampusTransportService transport;
    @Autowired private StudentRepository students;
    @Autowired private TransportPassRepository passes;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void expiredPassCanBeRenewedAndIsMarkedExpired() {
        Student student = createStudent("transport-expired");
        TransportPass oldPass = new TransportPass(student.getId(), LocalDate.now().minusDays(1));
        passes.saveAndFlush(oldPass);

        TransportPass renewed = transport.renew("transport-expired");

        assertThat(passes.findById(oldPass.getId()).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(renewed.getStatus()).isEqualTo("ACTIVE");
        assertThat(renewed.getValidUntil()).isEqualTo(LocalDate.now().plusMonths(6));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aStillValidPassPreventsRenewal() {
        Student student = createStudent("transport-active");
        passes.saveAndFlush(new TransportPass(student.getId(), LocalDate.now().plusDays(1)));

        assertThatThrownBy(() -> transport.renew("transport-active"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private Student createStudent(String username) {
        return students.saveAndFlush(new Student(new User(username, "encoded", Role.STUDENT), username, "Year 1"));
    }
}
