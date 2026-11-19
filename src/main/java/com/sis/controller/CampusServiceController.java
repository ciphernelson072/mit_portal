package com.sis.controller;

import com.sis.entity.*;
import com.sis.dto.HostelApplicationRequest;
import com.sis.dto.SendCampusChatRequest;
import com.sis.repository.*;
import com.sis.service.SchoolService;
import com.sis.service.CampusSupportAssistantService;
import com.sis.service.CampusTransportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/campus")
public class CampusServiceController {
    private final SchoolService schoolService;
    private final HostelApplicationRepository hostels;
    private final ChatMessageRepository chat;
    private final CampusSupportAssistantService assistant;
    private final CampusTransportService transport;

    public CampusServiceController(SchoolService schoolService, HostelApplicationRepository hostels, ChatMessageRepository chat,
                                   CampusSupportAssistantService assistant, CampusTransportService transport) {
        this.schoolService = schoolService;
        this.hostels = hostels;
        this.chat = chat;
        this.assistant = assistant;
        this.transport = transport;
    }

    @GetMapping("/student/hostel")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<HostelApplication>> hostelApplications() {
        Student student = currentStudent();
        return student == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(hostels.findByStudentIdOrderByCreatedAtDesc(student.getId()));
    }

    @PostMapping("/student/hostel")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<HostelApplication> applyForHostel(@Valid @RequestBody HostelApplicationRequest body) {
        Student student = currentStudent();
        if (student == null) return ResponseEntity.notFound().build();
        return ResponseEntity.status(HttpStatus.CREATED).body(hostels.save(new HostelApplication(student.getId(), body.getDetails())));
    }

    @GetMapping("/student/transport-pass")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<TransportPass> transportPass() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return transport.latestPass(username).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/student/transport-pass/renew")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<TransportPass> renewTransportPass() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(transport.renew(username));
    }

    @GetMapping("/student/chat")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<ChatMessage>> chatHistory() {
        Student student = currentStudent();
        return student == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(chat.findByStudentIdOrderByCreatedAtAsc(student.getId()));
    }

    @PostMapping("/student/chat")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<ChatMessage> sendChat(@Valid @RequestBody SendCampusChatRequest body) {
        Student student = currentStudent();
        if (student == null) return ResponseEntity.notFound().build();
        ChatMessage message = assistant.respond(student.getId(), body.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    private Student currentStudent() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username).orElse(null);
    }
}
