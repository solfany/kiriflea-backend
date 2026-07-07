package com.nplohs.market.notification.service;

import com.nplohs.market.notification.dto.NotificationDto;
import com.nplohs.market.notification.entity.Notification;
import com.nplohs.market.notification.entity.NotificationType;
import com.nplohs.market.notification.repository.NotificationRepository;
import com.nplohs.market.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void createNotification(User user, NotificationType type, String message, String linkUrl) {
        if (user == null) return;
        Notification n = notificationRepository.save(new Notification(user, type, message, linkUrl));
        messagingTemplate.convertAndSend("/topic/user/" + user.getId() + "/notifications", new NotificationDto(n));
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(Long userId) {
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(NotificationDto::new).toList();
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return notificationRepository.countByUser_IdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long id, Long userId) {
        notificationRepository.markAsReadById(id, userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void deleteAll(Long userId) {
        notificationRepository.deleteAllByUserId(userId);
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void deleteOldNotifications() {
        notificationRepository.deleteByCreatedAtBefore(java.time.LocalDateTime.now().minusDays(7));
    }

    @Transactional
    public void deleteNotification(Long id, Long userId) {
        notificationRepository.deleteByIdAndUserId(id, userId);
    }
}
