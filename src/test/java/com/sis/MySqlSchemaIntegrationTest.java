package com.sis;

import com.sis.entity.AcademicTerm;
import com.sis.entity.AcademicYear;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.AcademicYearRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("mysql")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class MySqlSchemaIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("sis_db")
            .withUsername("sis_app")
            .withPassword("integration-password");

        @Autowired
        private Flyway flyway;

        @Autowired
        private AcademicTermRepository academicTerms;

        @Autowired
        private AcademicYearRepository academicYears;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("jwt.secret", () -> "IntegrationOnlySecretKeyThatIsLongEnoughForHS256");
        registry.add("file.upload.directory", () -> System.getProperty("java.io.tmpdir") + "/sis-mysql-test-uploads/");
        registry.add("app.security.cookie-secure", () -> "false");
        registry.add("app.mail.enabled", () -> "false");
        registry.add("app.frontend.url", () -> "http://localhost:8080");
        registry.add("server.port", () -> "0");
    }

    @Test
    void flywayMigrationsBuildSchemaAcceptedByHibernate() {
        assumeTrue(MYSQL.isRunning(), "Docker/Testcontainers MySQL is unavailable in this environment");
        assertTrue(MYSQL.isRunning());
        var latestMigrationVersion = Arrays.stream(flyway.info().all())
                .map(migration -> migration.getVersion())
                .filter(version -> version != null)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new AssertionError("No versioned Flyway migrations were discovered"));
        assertEquals(latestMigrationVersion, flyway.info().current().getVersion());
    }

    @Test
    @Transactional
    void locksAcademicTermWithMysqlCompatibleQuery() {
        AcademicYear year = new AcademicYear();
        year.setCode("MYSQL-LOCK-AY");
        year.setName("MySQL Lock Test Year");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year = academicYears.saveAndFlush(year);

        AcademicTerm term = new AcademicTerm();
        term.setCode("MYSQL-LOCK-T1");
        term.setName("MySQL Lock Test Term");
        term.setAcademicYear(year);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        term = academicTerms.saveAndFlush(term);

        assertEquals("MYSQL-LOCK-T1",
                academicTerms.findByIdForUpdate(term.getId()).orElseThrow().getCode());
    }
}