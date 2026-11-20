package com.sis.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@Profile("prod")
public class FlywayMigrationRecoveryConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayMigrationRecoveryConfiguration.class);
    private static final String FAILED_VERSION = "57";
    private static final String FAILED_DESCRIPTION =
            "configure grade deadlines and deduplicate automation notifications";

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            List<MigrationInfo> failedMigrations = Arrays.stream(flyway.info().all())
                    .filter(migration -> migration.getState() == MigrationState.FAILED)
                    .toList();
            if (!failedMigrations.isEmpty()) {
                if (failedMigrations.size() != 1 || !isExpectedFailure(failedMigrations.get(0))) {
                    String failures = failedMigrations.stream()
                            .map(migration -> migration.getVersion() + " (" + migration.getDescription() + ")")
                            .collect(Collectors.joining(", "));
                    throw new IllegalStateException("Flyway has unexpected failed migration(s): "
                            + failures + "; refusing automatic repair.");
                }
                LOGGER.warn("Repairing failed Flyway migration V57; its idempotent migration will resume.");
                flyway.repair();
            }
            flyway.migrate();
        };
    }

    private static boolean isExpectedFailure(MigrationInfo migration) {
        return migration.getVersion() != null
                && FAILED_VERSION.equals(migration.getVersion().toString())
                && FAILED_DESCRIPTION.equals(migration.getDescription());
    }
}
