package com.kametude.support_service.service;

import com.kametude.support_service.entity.Notification;
import com.kametude.support_service.enums.NotificationType;
import com.kametude.support_service.exception.SupportException;
import com.kametude.support_service.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    public NotificationService(NotificationRepository repository) { this.repository = repository; }

    @Transactional
    public Notification create(UUID userId, String title, String message, NotificationType type, String link) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setLink(link);
        notification.setRead(false);
        return repository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getAllForUser(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadForUser(UUID userId) {
        return repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Notification markAsRead(UUID notificationId, UUID userId) {
        Notification notification = owned(notificationId, userId);
        notification.setRead(true);
        return repository.save(notification);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        List<Notification> values = repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        values.forEach(value -> value.setRead(true));
        repository.saveAll(values);
    }

    @Transactional
    public void clear(UUID userId) {
        repository.deleteByUserId(userId);
    }

    private Notification owned(UUID id, UUID userId) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new SupportException(HttpStatus.NOT_FOUND, "Notification introuvable"));
        if (!notification.getUserId().equals(userId)) {
            throw new SupportException(HttpStatus.FORBIDDEN, "Notification d'un autre utilisateur");
        }
        return notification;
    }
}
