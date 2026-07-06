package com.nplohs.market.notification.repository;

import com.nplohs.market.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId);
    int countByUser_IdAndIsReadFalse(Long userId);
}
