package com.kametude.support_service.dto;

import com.kametude.support_service.enums.NotificationType;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateNotificationRequest {

    @NotNull private UUID userId;
    @NotBlank private String title;
    @NotBlank private String message;
    @NotNull private NotificationType type;
    private String link;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }
}
