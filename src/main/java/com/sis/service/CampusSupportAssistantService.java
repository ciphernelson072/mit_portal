package com.sis.service;

import com.sis.entity.ChatMessage;
import com.sis.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Provides deterministic portal support responses without requiring an external
 * AI provider or storing provider credentials in the application.
 */
@Service
@Transactional
public class CampusSupportAssistantService {

    private final ChatMessageRepository chatMessages;

    public CampusSupportAssistantService(ChatMessageRepository chatMessages) {
        this.chatMessages = chatMessages;
    }

    public ChatMessage respond(Long studentId, String content) {
        String question = content == null ? "" : content.trim();
        chatMessages.save(new ChatMessage(studentId, question, "STUDENT"));
        String response = answer(question);
        return chatMessages.save(new ChatMessage(studentId, response, "ASSISTANT"));
    }

    private String answer(String content) {
        String question = content.toLowerCase(Locale.ROOT);
        if (question.contains("grade") || question.contains("result")) {
            return "Open Academics, then Grades, to view published results. For a missing grade, submit a Help Desk request.";
        }
        if (question.contains("fee") || question.contains("pay") || question.contains("payment")) {
            return "Open Finance to review your fees, download invoices, and pay an outstanding fee recorded on your account.";
        }
        if (question.contains("assignment") || question.contains("submission")) {
            return "Open Academics, then Assignments, to review due dates and submit available assignments.";
        }
        if (question.contains("hostel") || question.contains("accommodation")) {
            return "Open Campus Support, then Hostel, to view your application status or submit an accommodation request.";
        }
        if (question.contains("transport") || question.contains("bus")) {
            return "Open Campus Support, then Transport, to view routes and renew your transport pass.";
        }
        if (question.contains("attendance")) {
            return "Open Attendance to review your records and download a date-range report.";
        }
        return "I can help with grades, fees, assignments, attendance, hostel, and transport. You can also submit a Help Desk request for personal support.";
    }
}
