package com.sis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminSystemStatusControllerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsBackupAsNotConfiguredWithoutAStatusFileSetting() {
        AdminSystemStatusController controller = controller("", 36);

        assertThat(controller.status().get("backup"))
                .isEqualTo(Map.of(
                        "status", "not_configured",
                        "label", "Not configured",
                        "detail", "No database backup status file is configured; completion and age cannot be verified."));
        assertThat(((Map<?, ?>) controller.status().get("uploadBackup")).get("status"))
                .isEqualTo("not_configured");
    }

    @Test
    void reportsFreshSuccessfulBackupTimestamp() throws Exception {
        Path statusFile = temporaryDirectory.resolve(".backup-success");
        writeBackupEvidence(statusFile, "database.sql", Instant.now());

        Map<?, ?> backup = (Map<?, ?>) controller(statusFile.toString(), 36).status().get("backup");

        assertThat(backup.get("status")).isEqualTo("online");
        assertThat(backup.get("label")).isEqualTo("Recent");
        assertThat(backup.get("detail").toString()).contains("Last successful database backup completed at");
    }

    @Test
    void reportsStaleOrMissingBackupEvidenceAsUnhealthy() throws Exception {
        Path statusFile = temporaryDirectory.resolve(".backup-success");
        writeBackupEvidence(statusFile, "database.sql", Instant.now().minusSeconds(48 * 60 * 60));

        Map<?, ?> stale = (Map<?, ?>) controller(statusFile.toString(), 36).status().get("backup");
        assertThat(stale.get("status")).isEqualTo("stale");

        Files.delete(statusFile);
        Path missingArtifactStatus = temporaryDirectory.resolve(".missing-artifact-success");
        writeBackupEvidence(missingArtifactStatus, "removed.sql", Instant.now());
        Files.delete(temporaryDirectory.resolve("removed.sql"));
        Files.delete(temporaryDirectory.resolve("removed.sql.sha256"));

        Map<?, ?> missing = (Map<?, ?>) controller(missingArtifactStatus.toString(), 36).status().get("backup");
        assertThat(missing.get("status")).isEqualTo("unavailable");
        assertThat(missing.get("detail")).isEqualTo("The backup artifact or its matching checksum is missing.");
    }

    @Test
    void reportsUploadBackupStatusIndependentlyFromDatabaseBackup() throws Exception {
        Path databaseStatus = temporaryDirectory.resolve(".database-success");
        Path uploadsStatus = temporaryDirectory.resolve(".uploads-success");
        writeBackupEvidence(databaseStatus, "database.sql", Instant.now());
        writeBackupEvidence(uploadsStatus, "uploads.zip", Instant.now().minusSeconds(48 * 60 * 60));

        Map<String, Object> status = controller(databaseStatus.toString(), uploadsStatus.toString(), 36).status();

        assertThat(((Map<?, ?>) status.get("backup")).get("status")).isEqualTo("online");
        assertThat(((Map<?, ?>) status.get("uploadBackup")).get("status")).isEqualTo("stale");
    }

    @Test
    void reportsUnavailableWhenTheArtifactChecksumDiffersFromItsMarker() throws Exception {
        Path statusFile = temporaryDirectory.resolve(".backup-success");
        writeBackupEvidence(statusFile, "tampered.sql", Instant.now());
        Files.writeString(temporaryDirectory.resolve("tampered.sql"), "modified after backup");

        Map<?, ?> backup = (Map<?, ?>) controller(statusFile.toString(), 36).status().get("backup");

        assertThat(backup.get("status")).isEqualTo("unavailable");
        assertThat(backup.get("detail")).isEqualTo(
                "The backup artifact does not match its recorded SHA-256 checksum.");
    }

    @Test
    void reportsEmailHealthyAfterSuccessfulSmtpTest() throws Exception {
        EmailService emailService = mock(EmailService.class);
        Instant sentAt = Instant.now();
        when(emailService.getLastSuccessfulTestEmailAt()).thenReturn(sentAt);
        AdminSystemStatusController controller = new AdminSystemStatusController(
                healthyJdbcTemplate(), new ObjectMapper(), true, "smtp.gmail.com",
                "sender@example.com", 587, emailService, "", "", 36);

        Map<?, ?> email = (Map<?, ?>) controller.status().get("email");

        assertThat(email.get("status")).isEqualTo("online");
        assertThat(email.get("label")).isEqualTo("Test sent");
        assertThat(email.get("detail").toString()).contains(sentAt.toString());
    }

    @Test
    void reportsConfiguredSmtpAsReadyBeforeSendingATestMessage() {
        AdminSystemStatusController controller = new AdminSystemStatusController(
                healthyJdbcTemplate(), new ObjectMapper(), true, "smtp.gmail.com",
                "sender@example.com", 587, mock(EmailService.class), "", "", 36);

        Map<?, ?> email = (Map<?, ?>) controller.status().get("email");

        assertThat(email.get("status")).isEqualTo("online");
        assertThat(email.get("label")).isEqualTo("Configured; ready");
    }

    @Test
    void reportsConfiguredApiEmailProviderAsReady() {
        EmailService emailService = mock(EmailService.class);
        when(emailService.isDeliveryConfigured()).thenReturn(true);
        AdminSystemStatusController controller = new AdminSystemStatusController(
                healthyJdbcTemplate(), new ObjectMapper(), true, "", "", 0,
                emailService, "", "", 36);

        Map<?, ?> email = (Map<?, ?>) controller.status().get("email");

        assertThat(email.get("status")).isEqualTo("online");
        assertThat(email.get("label")).isEqualTo("Configured; ready");
    }

    private AdminSystemStatusController controller(String statusFile, long maxAgeHours) {
        return controller(statusFile, "", maxAgeHours);
    }

    private AdminSystemStatusController controller(String statusFile, String uploadStatusFile, long maxAgeHours) {
        return new AdminSystemStatusController(healthyJdbcTemplate(), new ObjectMapper(), false, "", "", 25,
                mock(EmailService.class), statusFile, uploadStatusFile, maxAgeHours);
    }

    private JdbcTemplate healthyJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        return jdbcTemplate;
    }

    private void writeBackupEvidence(Path markerFile, String artifactName, Instant completedAt) throws Exception {
        Path artifact = temporaryDirectory.resolve(artifactName);
        String artifactContents = "backup test content " + artifactName;
        Files.writeString(artifact, artifactContents);
        String checksum = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(artifactContents.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.writeString(Path.of(artifact + ".sha256"), checksum);
        Files.writeString(markerFile, new ObjectMapper().writeValueAsString(Map.of(
                "completedAt", completedAt.toString(),
                "artifact", artifact.toString(),
                "sha256", checksum)));
    }
}
