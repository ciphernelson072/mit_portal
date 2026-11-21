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
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@Profile("prod")
public class FlywayMigrationRecoveryConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayMigrationRecoveryConfiguration.class);
    private static final String FAILED_VERSION = "57";
    private static final String FAILED_DESCRIPTION =
            "configure grade deadlines and deduplicate automation notifications";
    private static final Map<String, String> KNOWN_CHECKSUM_MISMATCHES = Map.of(
            "55", "track student program semester",
            "56", "queue notification email delivery",
            "58", "require finance approval for new fee charges",
            "59", "coordinate scheduled automation across instances",
            "60", "configure and store admission documents");

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            MigrationInfo[] migrations = flyway.info().all();
            List<MigrationInfo> failedMigrations = Arrays.stream(migrations)
                    .filter(migration -> isFailedState(migration.getState()))
                    .toList();
            List<MigrationInfo> checksumMismatches = Arrays.stream(migrations)
                    .filter(migration -> migration.getVersion() != null)
                    .filter(migration -> !migration.isChecksumMatching())
                    .toList();

            if (!failedMigrations.isEmpty()
                    && (failedMigrations.size() != 1 || !isExpectedFailure(failedMigrations.get(0)))) {
                String failures = failedMigrations.stream()
                        .map(migration -> migration.getVersion() + " (" + migration.getDescription() + ")")
                        .collect(Collectors.joining(", "));
                throw new IllegalStateException("Flyway has unexpected failed migration(s): "
                        + failures + "; refusing automatic repair.");
            }

            List<MigrationInfo> unexpectedChecksumMismatches = checksumMismatches.stream()
                    .filter(migration -> !isKnownChecksumMismatch(migration))
                    .toList();
            if (!unexpectedChecksumMismatches.isEmpty()) {
                String mismatches = unexpectedChecksumMismatches.stream()
                        .map(migration -> migration.getVersion() + " (" + migration.getDescription() + ")")
                        .collect(Collectors.joining(", "));
                throw new IllegalStateException("Flyway has unexpected checksum mismatch(es): "
                        + mismatches + "; refusing automatic repair.");
            }

            if (!failedMigrations.isEmpty() || !checksumMismatches.isEmpty()) {
                LOGGER.warn("Repairing {} known failed/checksum migration record(s); migrations will be validated and resumed.",
                        failedMigrations.size() + checksumMismatches.size());
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

    private static boolean isKnownChecksumMismatch(MigrationInfo migration) {
        String version = migration.getVersion().toString();
        return KNOWN_CHECKSUM_MISMATCHES.getOrDefault(version, "").equals(migration.getDescription())
                && migration.getAppliedChecksum() != null
                && migration.getResolvedChecksum() != null;
    }

    private static boolean isFailedState(MigrationState state) {
        return state == MigrationState.FAILED || state == MigrationState.MISSING_FAILED;
    }
}
