package com.kametude.support_service.controller;

import com.kametude.support_service.dto.ChatMessageRequest;
import com.kametude.support_service.entity.ChatMessage;
import com.kametude.support_service.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    public ChatController(ChatService chatService) { this.chatService = chatService; }

    @GetMapping("/orders/{orderId}/messages")
    public ResponseEntity<List<ChatMessage>> getHistory(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(chatService.getHistory(orderId, userId, role));
    }

    @PostMapping("/orders/{orderId}/messages")
    public ResponseEntity<ChatMessage> send(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody ChatMessageRequest request) {
        // L'orderId du chemin fait foi; celui du payload reste pour compatibilite.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(orderId, userId, role, request.getContent()));
    }
}
