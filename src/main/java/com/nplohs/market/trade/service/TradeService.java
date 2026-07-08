package com.nplohs.market.trade.service;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.entity.ProductStatus;
import com.nplohs.market.product.repository.ProductRepository;
import com.nplohs.market.trade.dto.TradeDto;
import com.nplohs.market.trade.entity.Review;
import com.nplohs.market.trade.entity.Trade;
import com.nplohs.market.trade.repository.ReviewRepository;
import com.nplohs.market.trade.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeService {

    private final TradeRepository tradeRepository;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final com.nplohs.market.notification.service.NotificationService notificationService;

    @Transactional
    public TradeDto.TradeResponse completeTrade(String sellerEmail, TradeDto.CreateTradeRequest request) {
        User seller = userRepository.findByEmail(sellerEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new IllegalArgumentException("상품의 판매자만 거래 완료를 처리할 수 있습니다.");
        }

        if (product.getStatus() == ProductStatus.SOLD) {
            throw new IllegalArgumentException("이미 판매 완료된 상품입니다.");
        }

        User buyer = userRepository.findById(request.getBuyerId())
            .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

        if (seller.getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("자신과 거래할 수 없습니다.");
        }

        product.changeStatus(ProductStatus.SOLD);
        productRepository.save(product);

        Trade trade = tradeRepository.save(new Trade(product, seller, buyer));

        // 구매자에게 후기 작성 알림 발송
        notificationService.createNotification(
            buyer,
            com.nplohs.market.notification.entity.NotificationType.REVIEW_REQUEST,
            "'" + product.getTitle() + "' 상품의 거래가 완료되었습니다. 후기를 남겨주세요!",
            "/my/purchases"
        );

        return TradeDto.TradeResponse.from(trade, false, false);
    }

    @Transactional
    public TradeDto.ReviewResponse leaveReview(String reviewerEmail, Long tradeId, TradeDto.CreateReviewRequest request) {
        User reviewer = userRepository.findByEmail(reviewerEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Trade trade = tradeRepository.findById(tradeId)
            .orElseThrow(() -> new IllegalArgumentException("Trade not found"));

        if (!trade.getSeller().getId().equals(reviewer.getId()) && !trade.getBuyer().getId().equals(reviewer.getId())) {
            throw new IllegalArgumentException("이 거래의 당사자만 리뷰를 남길 수 있습니다.");
        }

        Optional<Review> existing = reviewRepository.findByTrade_IdAndReviewer_Id(tradeId, reviewer.getId());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("이미 이 거래에 리뷰를 남겼습니다.");
        }

        User reviewee = trade.getSeller().getId().equals(reviewer.getId()) ? trade.getBuyer() : trade.getSeller();

        Review review = new Review(trade, reviewer, reviewee, request.getScore(), request.getComment());
        reviewRepository.save(review);

        // 매너온도 계산
        // 3점: 0, 4점: +0.5, 5점: +1.0, 2점: -0.5, 1점: -1.0
        double diff = (request.getScore() - 3) * 0.5;
        reviewee.addMannerScore(diff);
        userRepository.save(reviewee);

        return TradeDto.ReviewResponse.from(review);
    }

    public List<TradeDto.ReviewResponse> getMyReviews(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return reviewRepository.findByReviewee_IdAndIsHiddenFalseOrderByCreatedAtDesc(user.getId())
            .stream().map(TradeDto.ReviewResponse::from).toList();
    }

    public TradeDto.TradeResponse getTradeByProductId(Long productId) {
        return tradeRepository.findByProduct_Id(productId)
            .map(t -> {
                boolean sellerReviewed = reviewRepository.findByTrade_IdAndReviewer_Id(t.getId(), t.getSeller().getId()).isPresent();
                boolean buyerReviewed = reviewRepository.findByTrade_IdAndReviewer_Id(t.getId(), t.getBuyer().getId()).isPresent();
                return TradeDto.TradeResponse.from(t, buyerReviewed, sellerReviewed);
            })
            .orElse(null);
    }
}

