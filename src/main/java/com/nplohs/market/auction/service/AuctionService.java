package com.nplohs.market.auction.service;

import com.nplohs.market.auction.dto.AuctionDto;
import com.nplohs.market.auction.entity.Auction;
import com.nplohs.market.auction.entity.AuctionStatus;
import com.nplohs.market.auction.entity.Bid;
import com.nplohs.market.auction.repository.AuctionRepository;
import com.nplohs.market.auction.repository.BidRepository;
import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.entity.ProductStatus;
import com.nplohs.market.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.nplohs.market.trade.repository.TradeRepository tradeRepository;
    private final com.nplohs.market.notification.service.NotificationService notificationService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── 입찰 목록 ────────────────────────────────────────────────
    public List<AuctionDto.BidResponse> getBids(Long productId) {
        Auction auction = getByProductId(productId);
        return bidRepository.findByAuction_IdOrderByCreatedAtDesc(auction.getId())
            .stream().map(AuctionDto.BidResponse::from).toList();
    }

    // ── 입찰하기 ─────────────────────────────────────────────────
    @Transactional
    public AuctionDto.BidResponse placeBid(Long productId, String userEmail, Long bidAmount) {
        User bidder = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Auction auction = auctionRepository.findByProduct_Id(productId)
            .orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다."));

        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new IllegalStateException("진행 중인 경매가 아닙니다.");
        }

        if (auction.getEndAt().isBefore(LocalDateTime.now())) {
            auction.setStatus(AuctionStatus.CLOSED);
            auctionRepository.save(auction);
            throw new IllegalStateException("이미 종료된 경매입니다.");
        }

        BigDecimal bidAmt = BigDecimal.valueOf(bidAmount);
        if (bidAmount > 1_000_000_000L) {
            throw new IllegalArgumentException("입찰 금액은 1,000,000,000원을 초과할 수 없습니다.");
        }

        BigDecimal minNext = auction.getCurrentPrice().add(auction.getMinBidIncrement());
        if (bidAmt.compareTo(minNext) < 0) {
            throw new IllegalArgumentException("최소 입찰가는 " + minNext.longValue() + "원 이상이어야 합니다.");
        }

        if (auction.getProduct().getSeller().getId().equals(bidder.getId())) {
            throw new IllegalArgumentException("자신의 상품에는 입찰할 수 없습니다.");
        }

        Bid topBid = bidRepository.findFirstByAuction_IdOrderByAmountDesc(auction.getId()).orElse(null);

        Bid bid = new Bid(auction, bidder, bidAmt);
        bidRepository.save(bid);

        auction.updateCurrentPrice(bidAmt, bidder);
        auctionRepository.save(auction);

        // 이전 최고 입찰자에게 상위 입찰 알림 발송
        if (topBid != null && !topBid.getBidder().getId().equals(bidder.getId())) {
            notificationService.createNotification(
                topBid.getBidder(),
                com.nplohs.market.notification.entity.NotificationType.OUTBID,
                "누군가 '" + auction.getProduct().getTitle() + "' 상품에 더 높은 금액을 입찰했습니다!",
                "/products/" + productId
            );
        }

        // STOMP 브로드캐스트
        long remaining = java.time.Duration.between(LocalDateTime.now(), auction.getEndAt()).toMillis();
        AuctionDto.AuctionUpdateMessage msg = AuctionDto.AuctionUpdateMessage.builder()
            .productId(productId)
            .currentBid(bidAmount.longValue())
            .bidCount(auction.getBidCount())
            .lastBidderNickname(bidder.getNickname())
            .remainingMs(Math.max(0, remaining))
            .status(auction.getStatus().name())
            .build();
        messagingTemplate.convertAndSend("/topic/product/" + productId + "/auction", msg);

        return AuctionDto.BidResponse.from(bid);
    }

    // ── 내 입찰 내역 ─────────────────────────────────────────────
    public List<AuctionDto.MyBidResponse> myBids(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Page<Bid> bids = bidRepository.findHighestBidsByBidderId(user.getId(), PageRequest.of(page, size));

        return bids.stream().map(bid -> {
            Auction auction = bid.getAuction();
            Product product = auction.getProduct();

            Bid topBid = bidRepository.findFirstByAuction_IdOrderByAmountDesc(auction.getId()).orElse(null);
            long topAmount = topBid != null ? topBid.getAmount().longValue() : auction.getStartPrice().longValue();
            boolean isWinning = topBid != null && topBid.getBidder().getId().equals(user.getId());

            String thumbnailUrl = product.getImages().isEmpty()
                ? null
                : product.getImages().get(0).getImageUrl();

            return AuctionDto.MyBidResponse.builder()
                .id(bid.getId())
                .amount(bid.getAmount().longValue())
                .createdAt(bid.getCreatedAt().format(FMT))
                .currentHighestBid(topAmount)
                .isWinning(isWinning)
                .product(AuctionDto.MyBidResponse.MyBidProduct.builder()
                    .id(product.getId())
                    .title(product.getTitle())
                    .thumbnailUrl(thumbnailUrl)
                    .status(product.getStatus().name())
                    .auctionEndAt(auction.getEndAt() != null ? auction.getEndAt().format(FMT) : null)
                    .isDeleted(product.isDeleted())
                    .build())
                .build();
        }).toList();
    }

    @Transactional
    public void reopen(Long productId, String userEmail, LocalDateTime newEndAt) {
        Auction auction = getByProductId(productId);
        if (!auction.getProduct().getSeller().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("판매자만 경매를 다시 시작할 수 있습니다.");
        }
        
        bidRepository.deleteAllByAuctionId(auction.getId());
        
        auction.reopen(newEndAt);
        auction.getProduct().changeStatus(com.nplohs.market.product.entity.ProductStatus.SALE);
        
        // 경매 재오픈 알림
        AuctionDto.AuctionUpdateMessage msg = AuctionDto.AuctionUpdateMessage.builder()
            .productId(productId)
            .status("ACTIVE")
            .currentBid(auction.getCurrentPrice().longValue())
            .bidCount(auction.getBidCount())
            .message("경매가 다시 시작되었습니다.")
            .build();
        messagingTemplate.convertAndSend("/topic/product/" + productId + "/auction", msg);
    }

    @Transactional
    public void extendTime(Long productId, String userEmail, LocalDateTime newEndAt) {
        Auction auction = getByProductId(productId);
        if (!auction.getProduct().getSeller().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("판매자만 마감시간을 연장할 수 있습니다.");
        }
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new IllegalArgumentException("진행 중인 경매만 시간 연장이 가능합니다.");
        }
        if (newEndAt.isBefore(auction.getEndAt())) {
            throw new IllegalArgumentException("기존 마감시간보다 앞당길 수 없습니다.");
        }
        
        auction.extendTime(newEndAt);
        
        AuctionDto.AuctionUpdateMessage msg = AuctionDto.AuctionUpdateMessage.builder()
            .productId(productId)
            .status("ACTIVE")
            .currentBid(auction.getCurrentPrice().longValue())
            .bidCount(auction.getBidCount())
            .message("경매 마감시간이 연장되었습니다.")
            .build();
        messagingTemplate.convertAndSend("/topic/product/" + productId + "/auction", msg);
    }

    @Transactional
    public void closeEarly(Long productId, String userEmail) {
        Auction auction = getByProductId(productId);
        
        if (!auction.getProduct().getSeller().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("판매자만 조기 낙찰을 할 수 있습니다.");
        }
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new IllegalArgumentException("현재 경매가 진행 중이지 않습니다.");
        }
        
        Bid topBid = bidRepository.findFirstByAuction_IdOrderByAmountDesc(auction.getId()).orElse(null);
        if (topBid == null) {
            throw new IllegalArgumentException("입찰자가 없어 즉시 낙찰을 진행할 수 없습니다.");
        }
        
        // 낙찰 처리
        auction.close(topBid.getBidder());
        auction.getProduct().changeStatus(ProductStatus.RESERVED);

        // 경매 종료 알림 (웹소켓)
        AuctionDto.AuctionUpdateMessage msg = AuctionDto.AuctionUpdateMessage.builder()
            .productId(productId)
            .status("CLOSED")
            .currentBid(auction.getCurrentPrice().longValue())
            .bidCount(auction.getBidCount())
            .lastBidderNickname(topBid.getBidder().getNickname())
            .message("판매자가 경매를 조기 낙찰했습니다.")
            .build();
        messagingTemplate.convertAndSend("/topic/product/" + productId + "/auction", msg);
        
        // 낙찰자에게 알림 발송
        notificationService.createNotification(
            topBid.getBidder(),
            com.nplohs.market.notification.entity.NotificationType.AUCTION_CLOSED,
            "축하합니다! '" + auction.getProduct().getTitle() + "' 경매에 최종 낙찰되었습니다.",
            "/products/" + productId
        );
    }

    // ── 만료 경매 자동 마감 (@Scheduled 60초마다) ─────────────────
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void closeExpiredAuctions() {
        List<Auction> expired = auctionRepository
            .findByEndAtBeforeAndStatus(LocalDateTime.now(), AuctionStatus.ACTIVE);

        for (Auction auction : expired) {
            Bid top = bidRepository
                .findFirstByAuction_IdOrderByAmountDesc(auction.getId())
                .orElse(null);

            if (top != null) {
                auction.close(top.getBidder());
                auction.getProduct().changeStatus(ProductStatus.RESERVED);
                
                // 낙찰자에게 알림 발송
                notificationService.createNotification(
                    top.getBidder(),
                    com.nplohs.market.notification.entity.NotificationType.AUCTION_CLOSED,
                    "축하합니다! '" + auction.getProduct().getTitle() + "' 경매에 최종 낙찰되었습니다.",
                    "/products/" + auction.getProduct().getId()
                );
                
                // 판매자에게 알림 발송
                notificationService.createNotification(
                    auction.getProduct().getSeller(),
                    com.nplohs.market.notification.entity.NotificationType.AUCTION_CLOSED,
                    "경매가 성공적으로 종료되었습니다. 낙찰자와 거래를 시작해보세요.",
                    "/products/" + auction.getProduct().getId()
                );
            } else {
                auction.cancel();
                auction.getProduct().changeStatus(ProductStatus.SALE);
            }
            auctionRepository.save(auction);

            // 마감 STOMP 알림
            AuctionDto.AuctionUpdateMessage msg = AuctionDto.AuctionUpdateMessage.builder()
                .productId(auction.getProduct().getId())
                .currentBid(auction.getCurrentPrice().longValue())
                .bidCount(auction.getBidCount())
                .lastBidderNickname(top != null ? top.getBidder().getNickname() : "")
                .remainingMs(0)
                .status(auction.getStatus().name())
                .build();
            messagingTemplate.convertAndSend(
                "/topic/product/" + auction.getProduct().getId() + "/auction", msg);
        }
        if (!expired.isEmpty()) {
            log.info("closeExpiredAuctions: {} auctions closed", expired.size());
        }
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────
    private Auction getByProductId(Long productId) {
        return auctionRepository.findByProduct_Id(productId)
            .orElseThrow(() -> new IllegalArgumentException("경매 상품이 아닙니다."));
    }

    @Transactional
    public void completeTrade(Long productId, String sellerEmail) {
        Auction auction = auctionRepository.findByProduct_Id(productId)
            .orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다."));

        Product product = auction.getProduct();
        if (!product.getSeller().getEmail().equals(sellerEmail)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        if (product.getStatus() != ProductStatus.RESERVED) {
            throw new IllegalArgumentException("예약 상태의 상품만 거래 완료할 수 있습니다.");
        }

        User buyer = auction.getWinner();
        if (buyer == null) {
            throw new IllegalArgumentException("낙찰자가 설정되지 않았습니다.");
        }

        product.changeStatus(ProductStatus.SOLD);
        com.nplohs.market.trade.entity.Trade trade = new com.nplohs.market.trade.entity.Trade(
            product, product.getSeller(), buyer);
        tradeRepository.save(trade);
    }
}
