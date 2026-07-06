package com.kametude.support_service.service;

import com.kametude.support_service.client.BusinessClient;
import com.kametude.support_service.client.BusinessOrderContext;
import com.kametude.support_service.client.IdentityClient;
import com.kametude.support_service.entity.ChatMessage;
import com.kametude.support_service.enums.NotificationType;
import com.kametude.support_service.exception.SupportException;
import com.kametude.support_service.repository.ChatMessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {
    private final ChatMessageRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    private final BusinessClient businessClient;
    private final IdentityClient identityClient;
    private final NotificationService notificationService;

    public ChatService(ChatMessageRepository repository, SimpMessagingTemplate messagingTemplate,
                       BusinessClient businessClient, IdentityClient identityClient,
                       NotificationService notificationService) {
        this.repository = repository;
        this.messagingTemplate = messagingTemplate;
        this.businessClient = businessClient;
        this.identityClient = identityClient;
        this.notificationService = notificationService;
    }

    @Transactional
    public ChatMessage sendMessage(UUID orderId, UUID senderId, String role, String content) {
        BusinessOrderContext order = requireParticipant(orderId, senderId, role);
        ChatMessage message = new ChatMessage();
        message.setOrderId(orderId);
        message.setSenderId(senderId);
        // Ancien contrat WebSocket : senderId pouvait venir du payload.
        message.setSenderName(identityClient.getProfile(senderId).displayName());
        message.setContent(content.trim());
        message.setType("text");
        ChatMessage saved = repository.save(message);
        messagingTemplate.convertAndSend("/topic/order." + orderId, saved);

        UUID recipientId = order.otherParticipant(senderId);
        String link = recipientId.equals(order.studentId()) ? "/mes-missions" : "/mes-commandes";
        notificationService.create(recipientId, "Nouveau message", saved.getSenderName() + " : " + saved.getContent(),
                NotificationType.NEW_MESSAGE, link);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getHistory(UUID orderId, UUID userId, String role) {
        requireParticipant(orderId, userId, role);
        return repository.findByOrderIdOrderByTimestampAsc(orderId);
    }

    private BusinessOrderContext requireParticipant(UUID orderId, UUID userId, String role) {
        BusinessOrderContext order = businessClient.getOrder(orderId);
        boolean staff = "ADMIN".equalsIgnoreCase(role) || "MODERATOR".equalsIgnoreCase(role);
        if (!staff && !order.hasParticipant(userId)) {
            throw new SupportException(HttpStatus.FORBIDDEN, "Vous ne participez pas a cette commande");
        }
        return order;
    }
}
