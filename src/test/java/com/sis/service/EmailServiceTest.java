package com.sis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;

class EmailServiceTest {

    private static final String FRONTEND_URL = "https://portal.example.test";

    private JavaMailSender mailSender;
    private EmailService emailService;
    private MimeMessage message;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromEmail", "portal@example.test");
        ReflectionTestUtils.setField(emailService, "mailHost", "smtp.example.test");
        ReflectionTestUtils.setField(emailService, "mailPort", 587);
        ReflectionTestUtils.setField(emailService, "mailPassword", "test-smtp-password");
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "mailProvider", "smtp");
        ReflectionTestUtils.setField(emailService, "frontendUrl", FRONTEND_URL);
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
    }

    @Test
    void passwordResetLinkUsesStaticPageAndUrlFragment() throws Exception {
        emailService.sendPasswordReset("student@example.test", "reset-secret", "student");

        String body = emailBody();
        assertTrue(body.contains(FRONTEND_URL + "/reset-password.html#token=reset-secret"));
        assertFalse(body.contains("?token="));
        verify(mailSender).send(message);
    }

    @Test
    void admissionEmailContainsOneTimeLoginCredentialsAndRequiresPasswordChange() throws Exception {
        emailService.sendAdmissionCredentialsEmail(
                "student@example.test", "Student Name", "student.user", "one-time-secret");

        String body = emailBody();
        assertTrue(body.contains(FRONTEND_URL + "/index.html"));
        assertTrue(body.contains("Username: student.user"));
        assertTrue(body.contains("One-time temporary password: one-time-secret"));
        assertTrue(body.contains("require you to choose a new password before you can access any student services"));
        verify(mailSender).send(message);
    }

    @Test
    void applicationReceiptEmailConfirmsSubmissionButDoesNotCreateLoginCredentials() throws Exception {
        emailService.sendApplicationReceivedEmail(
                "applicant@example.test", "Applicant Name", "APP-123", "Computer Science", "2027/2028");

        String body = emailBody();
        assertTrue(body.contains("Application number: APP-123"));
        assertTrue(body.contains("Programme: Computer Science"));
        assertTrue(body.contains("Academic year: 2027/2028"));
        assertTrue(body.contains(FRONTEND_URL + "/admissions.html"));
        assertTrue(body.contains("not an admission decision or a portal login"));
        assertTrue(body.contains("sign-in instructions separately"));
        assertFalse(body.toLowerCase().contains("password:"));
        verify(mailSender).send(message);
    }

    @Test
    void hiringEmailUsesCurrentPortalRoutesAndLabelsPasswordAsOneTime() throws Exception {
        emailService.sendHiringNotification(
                "teacher@example.test", "Teacher Name", "teacher.user", "one-time-secret");

        String body = emailBody();
        assertTrue(body.contains(FRONTEND_URL + "/portal-login.html"));
        assertTrue(body.contains(FRONTEND_URL + "/teacher-dashboard.html"));
        assertTrue(body.contains("One-time temporary password: one-time-secret"));
        assertTrue(body.contains("require you to choose a new password before you can access teacher services"));
        assertFalse(body.contains("/templates/"));
        assertFalse(body.contains(FRONTEND_URL + "/login"));
        verify(mailSender).send(message);
    }

    @Test
    void emailVerificationLinkUsesStaticPageAndUrlFragment() throws Exception {
        emailService.sendEmailVerification("student@example.test", "verification-secret", "student");

        String body = emailBody();
        assertTrue(body.contains(FRONTEND_URL + "/verify-email.html#token=verification-secret"));
        assertFalse(body.contains("?code="));
        verify(mailSender).send(message);
    }

    @Test
    void successfulTestEmailRecordsLastSuccessfulSendTime() {
        emailService.sendTestEmail("admin@example.test");

        assertTrue(emailService.getLastSuccessfulTestEmailAt() != null);
        verify(mailSender).send(message);
    }

    @Test
    void failedTestEmailDoesNotRecordSuccessfulSend() {
        org.mockito.Mockito.doThrow(new MailSendException("SMTP failure")).when(mailSender).send(message);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> emailService.sendTestEmail("admin@example.test"));

        assertTrue(emailService.getLastSuccessfulTestEmailAt() == null);
    }

    @Test
    void smtpIsNotConfiguredWithoutAuthenticationPassword() {
        ReflectionTestUtils.setField(emailService, "mailPassword", "");

        assertFalse(emailService.isDeliveryConfigured());
    }

    @Test
    void resendIsNotConfiguredWithAnInvalidSenderAddress() {
        configureResend(emailService);
        ReflectionTestUtils.setField(emailService, "mailApiFromEmail", "not-an-email");

        assertFalse(emailService.isDeliveryConfigured());
    }

    @Test
    void invalidRecipientIsRejectedBeforeProviderCall() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> emailService.sendTestEmail("not-an-email"));

        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.never()).send(message);
    }

    @Test
    void resendProviderSendsAuthenticatedTransactionalEmailOverHttps() {
        RestTemplate apiClient = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(apiClient);
        EmailService resendService = new EmailService(mailSender, apiClient);
        configureResend(resendService);
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-resend-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"from\":\"MIT Portal <verified@example.test>\","
                        + "\"to\":[\"admin@example.test\"],"
                        + "\"subject\":\"MIT Portal email test\","
                        + "\"text\":\"This is a test email from the MIT Portal administration service.\"}"))
                .andRespond(withSuccess("{\"id\":\"email-test-id\"}", MediaType.APPLICATION_JSON));

        resendService.sendTestEmail("admin@example.test");

        server.verify();
        assertTrue(resendService.getLastSuccessfulTestEmailAt() != null);
        assertTrue(resendService.isDeliveryConfigured());
    }

    @Test
    void resendProviderFailureDoesNotReportTestEmailAsSuccessful() {
        RestTemplate apiClient = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(apiClient);
        EmailService resendService = new EmailService(mailSender, apiClient);
        configureResend(resendService);
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withUnauthorizedRequest());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> resendService.sendTestEmail("admin@example.test"));

        server.verify();
        assertTrue(resendService.getLastSuccessfulTestEmailAt() == null);
    }

    private void configureResend(EmailService service) {
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "mailProvider", "resend");
        ReflectionTestUtils.setField(service, "mailApiKey", "test-resend-api-key");
        ReflectionTestUtils.setField(service, "mailApiFromEmail", "verified@example.test");
        ReflectionTestUtils.setField(service, "mailFromName", "MIT Portal");
    }

    private String emailBody() throws Exception {
        return extractText(message.getContent());
    }

    private String extractText(Object content) throws Exception {
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof Multipart) {
            StringBuilder text = new StringBuilder();
            Multipart multipart = (Multipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                text.append(extractText(part.getContent()));
            }
            return text.toString();
        }
        return "";
    }
}
