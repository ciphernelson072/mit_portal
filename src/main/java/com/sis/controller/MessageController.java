package com.sis.controller;

import com.sis.dto.MessageDto;
import com.sis.dto.SendMessageRequest;
import com.sis.service.SchoolService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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
    public ResponseEntity<List<MessageDto>> getReceivedMessages() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .map(user -> ResponseEntity.ok(schoolService.getReceivedMessageDtos(user.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sent")
    public ResponseEntity<List<MessageDto>> getSentMessages() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .map(user -> ResponseEntity.ok(schoolService.getSentMessageDtos(user.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MessageDto> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .flatMap(sender -> schoolService.getUserByUsername(request.getReceiverUsername())
                        .map(receiver -> schoolService.sendMessage(sender, receiver, request.getSubject(), request.getContent())))
                .map(message -> ResponseEntity.status(HttpStatus.CREATED).body(schoolService.toMessageDto(message)))
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/{messageId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long messageId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
            .filter(user -> schoolService.markMessageRead(messageId, user.getId()))
            .map(user -> ResponseEntity.ok().<Void>build())
            .orElse(ResponseEntity.notFound().build());
    }
}
