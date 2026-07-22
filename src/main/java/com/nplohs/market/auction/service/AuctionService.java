package com.nplohs.market.auction.service;

import com.nplohs.market.auction.dto.AuctionDto;
import com.nplohs.market.auction.entity.Auction;
import com.nplohs.market.auction.entity.AuctionStatus;
import com.nplohs.market.auction.entity.Bid;
import com.nplohs.market.auction.repository.AuctionRepository;
import com.nplohs.market.auction.repository.BidRepository;
import com.nplohs.market.user.entity.User;
import com.nplohs.market.user.repository.UserRepository;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.entity.ProductStatus;
import com.nplohs.market.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    // 같은 빈 내부에서 @Transactional 메서드를 직접 호출하면(self-invocation) 프록시를 안 거쳐서
    // 트랜잭션이 안 걸리므로, 낙관적 락 재시도를 위해 프록시를 통해 자기 자신을 다시 호출한다.
    private final ObjectProvider<AuctionService> selfProvider;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_BID_RETRIES = 3;

    // ── 입찰 목록 ────────────────────────────────────────────────
    public List<AuctionDto.BidResponse> getBids(Long productId) {
        Auction auction = getByProductId(productId);
        return bidRepository.findByAuction_IdOrderByCreatedAtDesc(auction.getId())
            .stream().map(AuctionDto.BidResponse::from).toList();
    }

    // ── 입찰하기 ─────────────────────────────────────────────────
    // 동시에 여러 입찰이 들어와 낙관적 락(@Version) 충돌이 나면, 최신 currentPrice 기준으로
    // 다시 검증하도록 짧게 재시도한다. (프록시를 거쳐야 @Transactional이 걸리므로 self 호출)
    // 클래스 기본값이 readOnly=true라서, 이 메서드 자체가 트랜잭션을 시작해버리면 그 안에서
    // 호출하는 placeBidTransactional까지 같은(읽기전용) 트랜잭션에 합류해 쓰기가 막히므로,
    // 여기서는 트랜잭션을 아예 띄우지 않고 매 재시도마다 새 트랜잭션을 열도록 한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuctionDto.BidResponse placeBid(Long productId, String userEmail, Long bidAmount) {
        AuctionService self = selfProvider.getObject();
        for (int attempt = 1; attempt <= MAX_BID_RETRIES; attempt++) {
            try {
                return self.placeBidTransactional(productId, userEmail, bidAmount);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_BID_RETRIES) {
                    throw new IllegalStateException("다른 입찰이 동시에 접수되었습니다. 다시 시도해주세요.");
                }
                log.info("입찰 낙관적 락 충돌, 재시도 {}/{} (productId={})", attempt, MAX_BID_RETRIES, productId);
            }
        }
        throw new IllegalStateException("입찰 처리에 실패했습니다.");
    }

    @Transactional
    public AuctionDto.BidResponse placeBidTransactional(Long productId, String userEmail, Long bidAmount) {
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
        
        // 팝콘 타임: 마감 5분 전 입찰 시 5분 자동 연장
        if (java.time.Duration.between(LocalDateTime.now(), auction.getEndAt()).toMinutes() < 5) {
            auction.extendEndAt(5);
        }
        
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
    public Page<AuctionDto.MyBidResponse> myBids(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Page<Bid> bids = bidRepository.findHighestBidsByBidderId(user.getId(), PageRequest.of(page, size));

        return bids.map(bid -> {
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
        });
    }

    @Transactional
    public void reopen(Long productId, String userEmail, LocalDateTime newEndAt) {
        Auction auction = getByProductId(productId);
        if (!auction.getProduct().getSeller().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("판매자만 경매를 다시 시작할 수 있습니다.");
        }
        // 최고 입찰자가 있는 상태에서 재오픈 시, 최고 입찰 내역만 삭제하고 차순위로 롤백
        Bid topBid = bidRepository.findFirstByAuction_IdOrderByAmountDesc(auction.getId()).orElse(null);
        if (topBid != null) {
            bidRepository.delete(topBid);
            bidRepository.flush(); // 바로 반영
            
            Bid secondBid = bidRepository.findFirstByAuction_IdOrderByAmountDesc(auction.getId()).orElse(null);
            BigDecimal newCurrentPrice = secondBid != null ? secondBid.getAmount() : auction.getStartPrice();
            int newBidCount = Math.max(0, auction.getBidCount() - 1);
            
            auction.rollbackToNextBid(newCurrentPrice, newBidCount, newEndAt);
        } else {
            auction.reopen(newEndAt);
        }
        
        auction.getProduct().changeStatus(com.nplohs.market.product.entity.ProductStatus.SALE);
        
        // 경매 재오픈 알림
        AuctionDto.AuctionUpdateMessage msg = AuctionDto.AuctionUpdateMessage.builder()
            .productId(productId)
            .status("ACTIVE")
            .currentBid(auction.getCurrentPrice().longValue())
            .bidCount(auction.getBidCount())
            .message("경매가 재개되었습니다.")
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

    @Transactional
    public void cancelEarlyClose(Long productId, String userEmail) {
        Auction auction = getByProductId(productId);
        if (!auction.getProduct().getSeller().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("판매자만 조기 낙찰을 취소할 수 있습니다.");
        }
        if (auction.getStatus() != AuctionStatus.CLOSED) {
            throw new IllegalArgumentException("조기 낙찰된 경매만 취소할 수 있습니다.");
        }
        
        if (auction.getEndAt().isBefore(java.time.LocalDateTime.now())) {
            auction.extendTime(java.time.LocalDateTime.now().plusHours(24));
        }
        
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.getProduct().changeStatus(com.nplohs.market.product.entity.ProductStatus.SALE);

        AuctionDto.AuctionUpdateMessage msg = AuctionDto.AuctionUpdateMessage.builder()
            .productId(productId)
            .status("ACTIVE")
            .currentBid(auction.getCurrentPrice().longValue())
            .bidCount(auction.getBidCount())
            .message("조기 낙찰이 취소되어 경매가 다시 진행됩니다.")
            .build();
        messagingTemplate.convertAndSend("/topic/product/" + productId + "/auction", msg);
    }

    @Transactional
    public void cancelTopBid(Long productId, String userEmail) {
        Auction auction = getByProductId(productId);
        if (!auction.getProduct().getSeller().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("판매자만 최고 입찰을 취소할 수 있습니다.");
        }
        
        Bid topBid = bidRepository.findFirstByAuction_IdOrderByAmountDesc(auction.getId()).orElse(null);
        if (topBid == null) {
            throw new IllegalArgumentException("취소할 입찰 내역이 없습니다.");
        }
        
        // 최고 입찰 삭제
        bidRepository.delete(topBid);
        bidRepository.flush();
        
        // 차순위 입찰 확인
        Bid secondBid = bidRepository.findFirstByAuction_IdOrderByAmountDesc(auction.getId()).orElse(null);
        BigDecimal newCurrentPrice = secondBid != null ? secondBid.getAmount() : auction.getStartPrice();
        int newBidCount = Math.max(0, auction.getBidCount() - 1);
        
        // 롤백 (시간은 연장하지 않음, 필요시 판매자가 직접 연장)
        // 만약 경매가 닫혔다면, 다시 ACTIVE 로 변경하여 차순위 입찰자 혹은 새로운 입찰을 받도록 함
        auction.rollbackToNextBid(newCurrentPrice, newBidCount, auction.getEndAt());
        auction.getProduct().changeStatus(ProductStatus.SALE); // 다시 판매 상태로 변경
        
        AuctionDto.AuctionUpdateMessage msg = AuctionDto.AuctionUpdateMessage.builder()
            .productId(productId)
            .status("ACTIVE")
            .currentBid(auction.getCurrentPrice().longValue())
            .bidCount(auction.getBidCount())
            .lastBidderNickname(secondBid != null ? secondBid.getBidder().getNickname() : "")
            .message("최고 입찰이 취소되어 차순위 입찰가로 변경되었습니다.")
            .build();
        messagingTemplate.convertAndSend("/topic/product/" + productId + "/auction", msg);
    }

    // ── 만료 경매 자동 마감 (@Scheduled 60초마다) ─────────────────
    // 경매 하나당 별도 트랜잭션으로 처리한다. 만료 처리 도중 누군가 그 경매에 입찰해서
    // @Version 충돌이 나도, 그 경매만 다음 주기로 미뤄질 뿐 같은 배치의 다른 경매들까지
    // 통째로 롤백되지 않도록 하기 위함이다 (예전에는 한 트랜잭션으로 묶여있어서, 하나만
    // 충돌해도 배치 전체의 마감 처리가 롤백됐다).
    @Scheduled(fixedDelay = 60_000)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void closeExpiredAuctions() {
        List<Long> expiredIds = auctionRepository
            .findByEndAtBeforeAndStatus(LocalDateTime.now(), AuctionStatus.ACTIVE)
            .stream().map(Auction::getId).toList();

        AuctionService self = selfProvider.getObject();
        int closedCount = 0;
        for (Long auctionId : expiredIds) {
            try {
                self.closeOneExpiredAuction(auctionId);
                closedCount++;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("closeExpiredAuctions: auctionId={}에 동시 갱신이 있어 이번 주기는 건너뜀, 다음 주기에 재시도", auctionId);
            }
        }
        if (closedCount > 0) {
            log.info("closeExpiredAuctions: {} auctions closed", closedCount);
        }
    }

    @Transactional
    public void closeOneExpiredAuction(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        // 그 사이 상태가 바뀌었거나(이미 처리됨) 마감 시간이 연장됐으면 이번엔 건너뛴다.
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE
                || auction.getEndAt().isAfter(LocalDateTime.now())) {
            return;
        }

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
