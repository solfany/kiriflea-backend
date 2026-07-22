package com.nplohs.market.notification.service;

import com.nplohs.market.notification.dto.NotificationDto;
import com.nplohs.market.notification.entity.Notification;
import com.nplohs.market.notification.entity.NotificationType;
import com.nplohs.market.notification.repository.NotificationRepository;
import com.nplohs.market.user.entity.User;
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

    @Transactional
    public void deleteLikeNotification(Long sellerId, String linkUrl, String nickname) {
        notificationRepository.deleteLikeNotification(sellerId, NotificationType.LIKE, linkUrl, escapeLike(nickname));
    }

    @Transactional(readOnly = true)
    public boolean existsLikeNotification(Long sellerId, String linkUrl, String nickname) {
        return notificationRepository.existsLikeNotification(sellerId, NotificationType.LIKE, linkUrl, escapeLike(nickname));
    }

    // 닉네임은 사용자가 자유롭게 정하므로 '%'/'_'가 들어가면 LIKE 패턴의 와일드카드로 해석되어
    // 알림 중복 제거가 엉뚱하게 동작할 수 있다. LIKE 특수문자를 이스케이프해 리터럴로 취급한다.
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(Long userId) {
        return notificationRepository.findByUser_IdOrderByIdDesc(userId)
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
