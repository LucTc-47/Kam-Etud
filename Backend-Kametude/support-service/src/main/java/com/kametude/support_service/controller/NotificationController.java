package com.kametude.support_service.controller;

import com.kametude.support_service.dto.CreateNotificationRequest;
import com.kametude.support_service.entity.Notification;
import com.kametude.support_service.exception.SupportException;
import com.kametude.support_service.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final String internalToken;

    public NotificationController(NotificationService service,
                                  @Value("${services.internal-token:change-this-internal-token}") String token) {
        this.service = service;
        this.internalToken = token;
    }

    @GetMapping("/me")
    public ResponseEntity<List<Notification>> getAll(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(service.getAllForUser(userId));
    }

    @GetMapping("/me/unread")
    public ResponseEntity<List<Notification>> getUnread(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(service.getUnreadForUser(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable UUID id,
                                                    @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(service.markAsRead(id, userId));
    }

    @PatchMapping("/me/read-all")
    public ResponseEntity<Void> markAll(@RequestHeader("X-User-Id") UUID userId) {
        service.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> clear(@RequestHeader("X-User-Id") UUID userId) {
        service.clear(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal")
    public ResponseEntity<Notification> create(@RequestHeader("X-Internal-Service-Token") String token,
                                                @Valid @RequestBody CreateNotificationRequest request) {
        if (!internalToken.equals(token)) throw new SupportException(HttpStatus.FORBIDDEN, "Appel inter-service refuse");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request.getUserId(), request.getTitle(),
                request.getMessage(), request.getType(), request.getLink()));
    }

    /* Ancien POST public /api/notifications commente : il permettait de creer
       une notification pour n'importe quel utilisateur depuis le navigateur. */
}
