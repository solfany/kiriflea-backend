package com.nplohs.market.notification.dto;

import com.nplohs.market.notification.entity.Notification;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
public class NotificationDto {
    private Long id;
    private String type;
    private String message;
    private String linkUrl;
    @JsonProperty("isRead")
    private boolean isRead;
    private String createdAt;

    public NotificationDto(Notification n) {
        this.id = n.getId();
        this.type = n.getType().name();
        this.message = n.getMessage();
        this.linkUrl = n.getLinkUrl();
        this.isRead = n.isRead();
        this.createdAt = n.getCreatedAt() != null ? n.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null;
    }
}
