package com.nplohs.market.notification.controller;

import com.nplohs.market.common.response.ApiResponse;
import com.nplohs.market.notification.dto.NotificationDto;
import com.nplohs.market.notification.service.NotificationService;
import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<NotificationDto> list = notificationService.getNotifications(user.getId());
        int unreadCount = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("items", list, "unreadCount", unreadCount)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.markAsRead(id, getUser(userDetails).getId());
        return ResponseEntity.ok(ApiResponse.ok("읽음 처리 완료"));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@AuthenticationPrincipal UserDetails userDetails) {
        notificationService.markAllAsRead(getUser(userDetails).getId());
        return ResponseEntity.ok(ApiResponse.ok("모두 읽음 처리 완료"));
    }

    @DeleteMapping("/all")
    public ResponseEntity<ApiResponse<Void>> deleteAll(@AuthenticationPrincipal UserDetails userDetails) {
        notificationService.deleteAll(getUser(userDetails).getId());
        return ResponseEntity.ok(ApiResponse.ok("모든 알림 삭제 완료"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.deleteNotification(id, getUser(userDetails).getId());
        return ResponseEntity.ok(ApiResponse.ok("알림 삭제 완료"));
    }
}
