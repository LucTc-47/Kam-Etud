package com.kametude.support_service.repository;

import com.kametude.support_service.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByOrderIdOrderByTimestampAsc(UUID orderId);
}