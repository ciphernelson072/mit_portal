package com.sis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.entity.NotificationCategory;
import com.sis.entity.Role;
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackupHealthMonitorServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void freshChecksummedDatabaseAndUploadEvidenceDoesNotAlert() throws Exception {
        Path databaseMarker = writeEvidence("database.sql", Instant.now());
        Path uploadMarker = writeEvidence("uploads.zip", Instant.now());
        NotificationService notifications = mock(NotificationService.class);
        NotificationRecipientService recipients = mock(NotificationRecipientService.class);
        BackupHealthMonitorService monitor = new BackupHealthMonitorService(
                notifications, recipients, new ObjectMapper(),
                databaseMarker.toString(), uploadMarker.toString(), 36, "Africa/Nairobi");

        assertThat(monitor.monitor(LocalDate.of(2026, 11, 20))).isZero();
        verify(notifications, never()).createAutomated(
                anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void staleEvidenceCreatesOneDailyAlertPerAdministratorAndKind() throws Exception {
        Path databaseMarker = writeEvidence("database.sql", Instant.now().minusSeconds(48 * 60 * 60));
        NotificationService notifications = mock(NotificationService.class);
        NotificationRecipientService recipients = mock(NotificationRecipientService.class);
        User administrator = new User();
        administrator.setId(7L);
        when(recipients.findActiveUsersWithPermission("PORTAL_SETTINGS_MANAGE", Role.ADMIN))
                .thenReturn(List.of(administrator));
        when(notifications.createAutomated(
                eq("backup-health:database:2026-11-20"), eq(7L),
                eq("Database backup needs attention"), anyString(), eq(NotificationCategory.SYSTEM)))
                .thenReturn(true);
        BackupHealthMonitorService monitor = new BackupHealthMonitorService(
                notifications, recipients, new ObjectMapper(),
                databaseMarker.toString(), "", 36, "Africa/Nairobi");

        assertThat(monitor.monitor(LocalDate.of(2026, 11, 20))).isEqualTo(1);
        verify(notifications).createAutomated(
                eq("backup-health:database:2026-11-20"), eq(7L),
                eq("Database backup needs attention"), anyString(), eq(NotificationCategory.SYSTEM));
    }

    @Test
    void missingOrTamperedEvidenceIsUnavailable() throws Exception {
        Path marker = writeEvidence("database.sql", Instant.now());
        Files.writeString(Path.of(marker.toString()), "{invalid");
        BackupHealthMonitorService monitor = new BackupHealthMonitorService(
                mock(NotificationService.class), mock(NotificationRecipientService.class),
                new ObjectMapper(), marker.toString(), "", 36, "Africa/Nairobi");

        assertThat(monitor.checkStatus(marker.toString())).isEqualTo("unavailable");
    }

    private Path writeEvidence(String artifactName, Instant completedAt) throws Exception {
        Path artifact = temporaryDirectory.resolve(artifactName);
        String content = "backup test content " + artifactName;
        Files.writeString(artifact, content);
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(content.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(Path.of(artifact + ".sha256"), checksum);
        Path marker = temporaryDirectory.resolve("." + artifactName + "-success");
        Files.writeString(marker, new ObjectMapper().writeValueAsString(Map.of(
                "completedAt", completedAt.toString(),
                "artifact", artifact.toString(),
                "sha256", checksum)));
        return marker;
    }
}
