package com.sis.controller;

import com.sis.dto.SendMessageRequest;
import com.sis.entity.Message;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@PreAuthorize("isAuthenticated()")
public class MessageController {

    private final SchoolService schoolService;

    public MessageController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping("/received")
    public ResponseEntity<List<Message>> getReceivedMessages() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .map(user -> ResponseEntity.ok(schoolService.getReceivedMessages(user.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sent")
    public ResponseEntity<List<Message>> getSentMessages() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .map(user -> ResponseEntity.ok(schoolService.getSentMessages(user.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody SendMessageRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .flatMap(sender -> schoolService.getUserByUsername(request.getReceiverUsername())
                        .map(receiver -> schoolService.sendMessage(sender, receiver, request.getSubject(), request.getContent())))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/{messageId}/read")
    public ResponseEntity<?> markRead(@PathVariable Long messageId) {
        schoolService.markMessageRead(messageId);
        return ResponseEntity.ok().build();
    }
}