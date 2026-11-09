package com.sis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Application entry point.
 * <p>
 * Demo/seed data lives solely in {@link com.sis.util.SampleDataLoader} now. This class
 * used to also define its own {@code CommandLineRunner} that seeded a second,
 * overlapping set of admin/teacher/student accounts through {@code SchoolService} -
 * redundant with SampleDataLoader, and it passed an already-BCrypt-encoded password
 * into a method that (after fixing the plaintext-password bug) encodes its input
 * itself, which would have double-encoded the admin password and broken that login.
 * One seeding path is enough, and it belongs in a dedicated component, not the
 * application bootstrap class.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.sis.entity")
@EnableJpaRepositories(basePackages = "com.sis.repository")
public class SisApplication {

    public static void main(String[] args) {
        SpringApplication.run(SisApplication.class, args);
    }
}
