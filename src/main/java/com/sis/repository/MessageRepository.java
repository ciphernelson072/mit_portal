package com.sis.repository;

import com.sis.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByReceiverIdOrderBySentAtDesc(Long receiverId);
    List<Message> findBySenderIdOrderBySentAtDesc(Long senderId);
}