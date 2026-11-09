package com.sis.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Email Service - Handles email notifications and communications
 * Sends emails for notifications, verification, and alerts
 * Supports SMTP and the Resend transactional email API.
 */
@Service
public class EmailService {

    private static final Logger logger = Logger.getLogger(EmailService.class.getName());

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;
    private volatile Instant lastSuccessfulTestEmailAt;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this(mailSender, new RestTemplate());
    }

    EmailService(JavaMailSender mailSender, RestTemplate restTemplate) {
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
    }

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.mail.provider:smtp}")
    private String mailProvider;

    @Value("${spring.mail.host:localhost}")
    private String mailHost;

    @Value("${spring.mail.port:25}")
    private int mailPort;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.api-key:}")
    private String mailApiKey;

    @Value("${app.mail.from-email:}")
    private String mailApiFromEmail;

    @Value("${app.mail.from-name:MIT Portal}")
    private String mailFromName;

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    public void sendAdmissionCredentialsEmail(String studentEmail, String studentName,
            String username, String temporaryPassword) {
        String subject = "Your MIT Student Portal sign-in details";
        String body = "Dear " + studentName + ",\n\n" +
                "Your admission is complete and your student portal account is ready.\n\n" +
                "Sign-in page: " + frontendUrl + "/index.html\n" +
                "Username: " + username + "\n" +
                "One-time temporary password: " + temporaryPassword + "\n\n" +
                "For your protection, this temporary password is only for your first sign-in. " +
                "The portal will require you to choose a new password before you can access any student services. " +
                "Do not share this message. If you did not expect this email, contact the Admissions Office.\n\n" +
                "Best regards,\nAdmissions Office\nMakerere Institute of Technology";
        sendEmail(studentEmail, subject, body);
    }

    public void sendApplicationReceivedEmail(String applicantEmail, String applicantName,
            String applicationNumber, String programName, String academicYearName) {
        String subject = "MIT admission application received";
        String body = "Dear " + applicantName + ",\n\n" +
                "We have received your application to Makerere Institute of Technology.\n\n" +
                "Application number: " + applicationNumber + "\n" +
                "Programme: " + programName + "\n" +
                "Academic year: " + academicYearName + "\n\n" +
                "Keep your application number. You can check your application status at " +
                frontendUrl + "/admissions.html using this number and the email address used to apply.\n\n" +
                "This message confirms receipt only; it is not an admission decision or a portal login. " +
                "If your application is accepted and you are enrolled, we will send your student portal " +
                "sign-in instructions separately to this email address.\n\n" +
                "Admissions Office\nMakerere Institute of Technology";
        sendEmail(applicantEmail, subject, body);
    }

    /**
     * Send teacher hiring notification
     * Notifies teacher about successful hiring
     * @param teacherEmail email address of teacher
     * @param teacherName name of teacher
     * @param username username assigned
     * @param password temporary password
     */
    public void sendHiringNotification(String teacherEmail, String teacherName, String username, String password) {
        String subject = "Welcome to Makerere Institute of Technology Student Information Portal - Hiring Confirmation";
        String body = buildHiringEmailBody(teacherName, username, password);
        sendEmail(teacherEmail, subject, body);
    }

    /**
     * Send email verification code
     * Sends verification link for email confirmation
     * @param email recipient email address
     * @param verificationCode unique verification code
     * @param userName name of user
     */
    public void sendEmailVerification(String email, String verificationCode, String userName) {
        String subject = "Email Verification - Makerere Institute of Technology Student Information Portal";
        String verificationLink = frontendUrl + "/verify-email.html#token=" + verificationCode;
        String body = "Dear " + userName + ",\n\n" +
                "Please verify your email address by clicking the link below:\n" +
                verificationLink + "\n\n" +
                "This link will expire in 24 hours.\n\n" +
                "If you did not create this account, please ignore this email.\n\n" +
                "Best regards,\n" +
                "Makerere Institute of Technology Student Information Portal";
        sendEmail(email, subject, body);
    }

    /**
     * Send password reset email
     * Sends password reset link to user
     * @param email recipient email address
     * @param resetToken unique reset token
     * @param userName name of user
     */
    public void sendPasswordReset(String email, String resetToken, String userName) {
        String subject = "Password Reset Request - Makerere Institute of Technology Student Information Portal";
        String resetLink = frontendUrl + "/reset-password.html#token=" + resetToken;
        String body = "Dear " + userName + ",\n\n" +
                "We received a request to reset your password. Click the link below to proceed:\n" +
                resetLink + "\n\n" +
                "This link will expire in 1 hour.\n\n" +
                "If you did not request a password reset, please ignore this email and your password will remain unchanged.\n\n" +
                "Best regards,\n" +
                "Makerere Institute of Technology Student Information Portal";
        sendEmail(email, subject, body);
    }

    /**
     * Send CGPA update notification
     * Notifies student about CGPA calculation/update
     * @param studentEmail email address of student
     * @param studentName name of student
     * @param cgpa current CGPA value
     */
    public void sendCGPAUpdateNotification(String studentEmail, String studentName, String cgpa) {
        String subject = "CGPA Update - Makerere Institute of Technology Student Information Portal";
        String body = "Dear " + studentName + ",\n\n" +
                "Your Cumulative Grade Point Average (CGPA) has been updated.\n\n" +
                "Current CGPA: " + cgpa + "\n\n" +
                "Please log in to your account to view detailed academic information.\n\n" +
                "Best regards,\n" +
                "Academic Administration";
        sendEmail(studentEmail, subject, body);
    }

    /**
     * Send grade notification
     * Notifies student about new grade entry
     * @param studentEmail email address of student
     * @param studentName name of student
     * @param courseName name of course
     * @param grade grade value (A, B, C, etc.)
     */
    public void sendGradeNotification(String studentEmail, String studentName, String courseName, String grade) {
        String subject = "New Grade Posted - " + courseName;
        String body = "Dear " + studentName + ",\n\n" +
                "A new grade has been posted for " + courseName + ".\n\n" +
                "Grade: " + grade + "\n\n" +
                "Please log in to view your complete academic record.\n\n" +
                "Best regards,\n" +
                "Academic Administration";
        sendEmail(studentEmail, subject, body);
    }

    /**
     * Send announcement notification
     * Notifies users about new announcements
     * @param email recipient email address
     * @param userName name of user
     * @param announcementTitle title of announcement
     * @param announcementContent content of announcement
     */
    public void sendAnnouncementNotification(String email, String userName, String announcementTitle, String announcementContent) {
        String subject = "New Announcement: " + announcementTitle;
        String body = "Dear " + userName + ",\n\n" +
                "A new announcement has been posted:\n\n" +
                announcementTitle + "\n\n" +
                announcementContent + "\n\n" +
                "Please log in to view full details.\n\n" +
                "Best regards,\n" +
                "School Administration";
        sendEmail(email, subject, body);
    }

    /**
     * Sends an email using the configured delivery provider.
     * @param recipientEmail recipient email address
     * @param subject email subject
     * @param body email content
     */
    private void sendEmail(String recipientEmail, String subject, String body) {
        if (!mailEnabled || !isDeliveryConfigured()) {
            logger.warning("Email delivery disabled or provider not configured. Provider=" + mailProvider);
            throw new IllegalStateException("Email delivery is not configured for this environment.");
        }

        if (!StringUtils.hasText(recipientEmail)) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        try {
            if ("resend".equalsIgnoreCase(mailProvider)) {
                sendWithResend(recipientEmail, subject, body);
                logger.info("Email accepted by Resend for delivery.");
                return;
            }
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            logger.info("Email sent to: " + recipientEmail);
            logger.info("Subject: " + subject);
        } catch (MessagingException e) {
            logger.severe("Error sending email: " + e.getMessage());
            throw new IllegalStateException("Failed to send email", e);
        } catch (RestClientResponseException e) {
            logger.warning("Email API request failed with HTTP status " + e.getStatusCode().value());
            throw new IllegalStateException(
                    "Email provider rejected the request (HTTP " + e.getStatusCode().value() + ").");
        } catch (Exception e) {
            logger.severe("Error sending email: " + e.getMessage());
            throw new IllegalStateException("Failed to send email", e);
        }
    }

    private void sendWithResend(String recipientEmail, String subject, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mailApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", mailFromName + " <" + mailApiFromEmail + ">");
        payload.put("to", Collections.singletonList(recipientEmail));
        payload.put("subject", subject);
        payload.put("text", body);

        restTemplate.postForEntity(
                "https://api.resend.com/emails",
                new HttpEntity<>(payload, headers),
                String.class);
    }

    public void sendTestEmail(String recipientEmail) {
        sendEmail(recipientEmail, "MIT Portal email test", "This is a test email from the MIT Portal administration service.");
        lastSuccessfulTestEmailAt = Instant.now();
    }

    public void sendComposedEmail(String recipientEmail, String subject, String content) {
        sendEmail(recipientEmail, subject, content);
    }

    public Instant getLastSuccessfulTestEmailAt() {
        return lastSuccessfulTestEmailAt;
    }

    private boolean hasSmtpConfiguration() {
        return StringUtils.hasText(fromEmail)
                && !"your-email@gmail.com".equalsIgnoreCase(fromEmail)
                && StringUtils.hasText(mailHost)
                && mailPort > 0;
    }

    public boolean isDeliveryConfigured() {
        if (!mailEnabled) {
            return false;
        }
        if ("resend".equalsIgnoreCase(mailProvider)) {
            return StringUtils.hasText(mailApiKey) && StringUtils.hasText(mailApiFromEmail);
        }
        return "smtp".equalsIgnoreCase(mailProvider) && mailSender != null && hasSmtpConfiguration();
    }

    /**
     * Build HTML email body for hiring notification
     * @param teacherName name of teacher
     * @param username assigned username
     * @param password temporary password
     * @return formatted email body
     */
    private String buildHiringEmailBody(String teacherName, String username, String password) {
        return "Dear " + teacherName + ",\n\n" +
                "Welcome! You have been successfully hired by our institution.\n\n" +
                "Your login credentials are:\n" +
                "Username: " + username + "\n" +
                "Password: " + password + "\n\n" +
                "Please change your password after your first login for security.\n\n" +
                "Login URL: " + frontendUrl + "/login\n" +
                "Teacher Portal: " + frontendUrl + "/templates/teacher-dashboard.html\n\n" +
                "Best regards,\n" +
                "Human Resources";
    }
}
