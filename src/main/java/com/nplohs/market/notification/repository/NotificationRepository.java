package com.nplohs.market.notification.repository;

import com.nplohs.market.notification.entity.Notification;
import com.nplohs.market.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUser_IdOrderByIdDesc(Long userId);
    int countByUser_IdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.user.id = :userId")
    int markAsReadById(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :date")
    void deleteByCreatedAtBefore(@Param("date") java.time.LocalDateTime date);
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.user.id = :userId")
    void deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(n) > 0 FROM Notification n WHERE n.user.id = :userId AND n.type = :type AND n.linkUrl = :linkUrl AND n.message LIKE CONCAT(:nickname, '%') ESCAPE '\\'")
    boolean existsLikeNotification(@Param("userId") Long userId, @Param("type") NotificationType type, @Param("linkUrl") String linkUrl, @Param("nickname") String nickname);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.type = :type AND n.linkUrl = :linkUrl AND n.message LIKE CONCAT(:nickname, '%') ESCAPE '\\'")
    void deleteLikeNotification(@Param("userId") Long userId, @Param("type") NotificationType type, @Param("linkUrl") String linkUrl, @Param("nickname") String nickname);
}
