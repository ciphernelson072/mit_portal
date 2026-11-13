package com.sis.repository;

import com.sis.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByStudentIdOrderByCreatedAtAsc(Long studentId);
}
