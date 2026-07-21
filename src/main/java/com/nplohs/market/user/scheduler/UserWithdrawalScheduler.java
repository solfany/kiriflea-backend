package com.nplohs.market.user.scheduler;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.product.repository.ProductRepository;
import com.nplohs.market.chat.repository.ChatRoomRepository;
import com.nplohs.market.auction.repository.BidRepository;
import com.nplohs.market.trade.repository.ReviewRepository;
import com.nplohs.market.trade.repository.TradeRepository;
import com.nplohs.market.wishlist.repository.WishlistRepository;
import com.nplohs.market.notification.repository.NotificationRepository;
import com.nplohs.market.comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawalScheduler {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final BidRepository bidRepository;
    private final ReviewRepository reviewRepository;
    private final TradeRepository tradeRepository;
    private final WishlistRepository wishlistRepository;
    private final NotificationRepository notificationRepository;
    private final CommentRepository commentRepository;

    // 테스트가 편리하도록 매 1분마다 동작 (실무 적용 시에는 "0 0 * * * *" 매 시간 정각 등으로 변경 추천)
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processWithdrawnUsers() {
        LocalDateTime targetTime = LocalDateTime.now().minusHours(24);
        List<User> targetUsers = userRepository.findByActiveFalseAndDeletedAtBefore(targetTime);

        if (targetUsers.isEmpty()) {
            return;
        }

        log.info("Starting UserWithdrawalScheduler... Target count: {}", targetUsers.size());
        int hardDeleted = 0;
        int softDeleted = 0;

        for (User user : targetUsers) {
            Long userId = user.getId();

            // 1. 단순 연관 데이터 하드 삭제
            wishlistRepository.deleteByUser_Id(userId);
            notificationRepository.deleteAllByUserId(userId);
            commentRepository.deleteByAuthor_Id(userId);

            // 2. 중요 연관 데이터 확인
            boolean hasProduct = productRepository.existsBySeller_Id(userId);
            boolean hasBid = bidRepository.existsByBidder_Id(userId);
            boolean hasReview = reviewRepository.existsByReviewer_IdOrReviewee_Id(userId, userId);
            boolean hasTrade = tradeRepository.existsBySeller_IdOrBuyer_Id(userId, userId);
            boolean hasChat = chatRoomRepository.existsByBuyer_IdOrSeller_Id(userId, userId);

            if (!hasProduct && !hasBid && !hasReview && !hasTrade && !hasChat) {
                // 중요 데이터가 전혀 없다면 사용자 완전 삭제 (Hard Delete)
                userRepository.delete(user);
                hardDeleted++;
                log.info("Hard deleted user ID: {}", userId);
            } else {
                // 하나라도 얽혀 있다면 유저 정보만 덮어씌움 (Soft Delete)
                user.scramblePersonalInfo(UUID.randomUUID().toString());
                user.clearDeletedAt(); // 스케줄러가 중복 처리하지 않도록 값 제거
                softDeleted++;
                log.info("Soft deleted user ID: {}", userId);
            }
        }
        log.info("UserWithdrawalScheduler finished. Hard deleted: {}, Soft deleted: {}", hardDeleted, softDeleted);
    }
}
