package com.sis.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlywayMigrationRecoveryConfigurationTest {
    private final FlywayMigrationRecoveryConfiguration configuration =
            new FlywayMigrationRecoveryConfiguration();

    @Test
    void repairsOnlyTheExpectedFailedV57BeforeMigrating() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo failedMigration = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.all()).thenReturn(new MigrationInfo[]{failedMigration});
        when(failedMigration.getState()).thenReturn(MigrationState.FAILED);
        when(failedMigration.getVersion()).thenReturn(MigrationVersion.fromVersion("57"));
        when(failedMigration.isChecksumMatching()).thenReturn(true);
        when(failedMigration.getDescription())
                .thenReturn("configure grade deadlines and deduplicate automation notifications");

        FlywayMigrationStrategy strategy = configuration.flywayMigrationStrategy();
        strategy.migrate(flyway);

        var order = inOrder(flyway);
        order.verify(flyway).repair();
        order.verify(flyway).migrate();
    }

    @Test
    void repairsExpectedMissingFailedV57BeforeMigrating() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo failedMigration = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.all()).thenReturn(new MigrationInfo[]{failedMigration});
        when(failedMigration.getState()).thenReturn(MigrationState.MISSING_FAILED);
        when(failedMigration.getVersion()).thenReturn(MigrationVersion.fromVersion("57"));
        when(failedMigration.isChecksumMatching()).thenReturn(true);
        when(failedMigration.getDescription())
                .thenReturn("configure grade deadlines and deduplicate automation notifications");

        FlywayMigrationStrategy strategy = configuration.flywayMigrationStrategy();
        strategy.migrate(flyway);

        var order = inOrder(flyway);
        order.verify(flyway).repair();
        order.verify(flyway).migrate();
    }

    @Test
    void refusesToRepairAnyOtherFailedMigration() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo failedMigration = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.all()).thenReturn(new MigrationInfo[]{failedMigration});
        when(failedMigration.getState()).thenReturn(MigrationState.FAILED);
        when(failedMigration.getVersion()).thenReturn(MigrationVersion.fromVersion("56"));
        when(failedMigration.isChecksumMatching()).thenReturn(true);
        when(failedMigration.getDescription()).thenReturn("unrelated migration");

        FlywayMigrationStrategy strategy = configuration.flywayMigrationStrategy();

        assertThrows(IllegalStateException.class, () -> strategy.migrate(flyway));
        verify(flyway, never()).repair();
        verify(flyway, never()).migrate();
    }

    @Test
    void repairsOnlyAllowlistedChecksumsWithMatchingMigrationIdentity() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo mismatch = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.all()).thenReturn(new MigrationInfo[]{mismatch});
        when(mismatch.getState()).thenReturn(MigrationState.SUCCESS);
        when(mismatch.getVersion()).thenReturn(MigrationVersion.fromVersion("55"));
        when(mismatch.getDescription()).thenReturn("track student program semester");
        when(mismatch.isChecksumMatching()).thenReturn(false);
        when(mismatch.getAppliedChecksum()).thenReturn(123);
        when(mismatch.getResolvedChecksum()).thenReturn(456);

        configuration.flywayMigrationStrategy().migrate(flyway);

        var order = inOrder(flyway);
        order.verify(flyway).repair();
        order.verify(flyway).migrate();
    }

    @Test
    void refusesToRepairChecksumMismatchOutsideAllowlist() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo mismatch = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.all()).thenReturn(new MigrationInfo[]{mismatch});
        when(mismatch.getState()).thenReturn(MigrationState.SUCCESS);
        when(mismatch.getVersion()).thenReturn(MigrationVersion.fromVersion("54"));
        when(mismatch.getDescription()).thenReturn("unrelated migration");
        when(mismatch.isChecksumMatching()).thenReturn(false);
        when(mismatch.getAppliedChecksum()).thenReturn(123);
        when(mismatch.getResolvedChecksum()).thenReturn(456);

        assertThrows(IllegalStateException.class,
                () -> configuration.flywayMigrationStrategy().migrate(flyway));
        verify(flyway, never()).repair();
        verify(flyway, never()).migrate();
    }
}
