package com.sis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.logging.Logger;

/**
 * Email Service - Handles email notifications and communications
 * Sends emails for notifications, verification, and alerts
 * Currently configured for logging - integrate with SMTP provider in production
 */
@Service
public class EmailService {

    private static final Logger logger = Logger.getLogger(EmailService.class.getName());

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    /**
     * Send student admission notification
     * Notifies student about successful admission
     * @param studentEmail email address of student
     * @param studentName name of student
     * @param username username assigned
     * @param password temporary password
     */
    public void sendAdmissionNotification(String studentEmail, String studentName, String username, String password) {
        String subject = "Welcome to School Information System - Admission Confirmation";
        String body = buildAdmissionEmailBody(studentName, username, password);
        sendEmail(studentEmail, subject, body);
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
        String subject = "Welcome to School Information System - Hiring Confirmation";
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
        String subject = "Email Verification - School Information System";
        String verificationLink = frontendUrl + "/verify-email?code=" + verificationCode;
        String body = "Dear " + userName + ",\n\n" +
                "Please verify your email address by clicking the link below:\n" +
                verificationLink + "\n\n" +
                "This link will expire in 24 hours.\n\n" +
                "If you did not create this account, please ignore this email.\n\n" +
                "Best regards,\n" +
                "School Information System";
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
        String subject = "Password Reset Request - School Information System";
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        String body = "Dear " + userName + ",\n\n" +
                "We received a request to reset your password. Click the link below to proceed:\n" +
                resetLink + "\n\n" +
                "This link will expire in 1 hour.\n\n" +
                "If you did not request a password reset, please ignore this email and your password will remain unchanged.\n\n" +
                "Best regards,\n" +
                "School Information System";
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
        String subject = "CGPA Update - School Information System";
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
     * Internal method to send email
     * Currently logs to console - integrate with SMTP in production
     * @param recipientEmail recipient email address
     * @param subject email subject
     * @param body email content
     */
    private void sendEmail(String recipientEmail, String subject, String body) {
        if (!mailEnabled) {
            logger.info("Mail service disabled. Would send email to: " + recipientEmail);
            logger.info("Subject: " + subject);
            return;
        }

        try {
            // TODO: Integrate with JavaMailSender or email provider
            // SimpleMailMessage message = new SimpleMailMessage();
            // message.setFrom(fromEmail);
            // message.setTo(recipientEmail);
            // message.setSubject(subject);
            // message.setText(body);
            // mailSender.send(message);

            logger.info("Email sent to: " + recipientEmail);
            logger.info("Subject: " + subject);
        } catch (Exception e) {
            logger.severe("Error sending email: " + e.getMessage());
        }
    }

    /**
     * Build HTML email body for admission notification
     * @param studentName name of student
     * @param username assigned username
     * @param password temporary password
     * @return formatted email body
     */
    private String buildAdmissionEmailBody(String studentName, String username, String password) {
        return "Dear " + studentName + ",\n\n" +
                "Congratulations! You have been successfully admitted to our institution.\n\n" +
                "Your login credentials are:\n" +
                "Username: " + username + "\n" +
                "Password: " + password + "\n\n" +
                "Please change your password after your first login for security.\n\n" +
                "Login URL: " + frontendUrl + "/login\n\n" +
                "Best regards,\n" +
                "Admissions Office";
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
