package com.nplohs.market.product.service;

import com.nplohs.market.auction.entity.Auction;
import com.nplohs.market.auction.entity.AuctionStatus;
import com.nplohs.market.auction.repository.AuctionRepository;
import com.nplohs.market.trade.repository.TradeRepository;
import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.product.dto.*;
import com.nplohs.market.product.entity.*;
import com.nplohs.market.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository    userRepository;
    private final AuctionRepository auctionRepository;
    private final TradeRepository   tradeRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int PAGE_SIZE = 20;

    // ── 상품 등록 ────────────────────────────────────────────────
    @Transactional
    public ProductResponse create(String sellerEmail, ProductCreateRequest request) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        ProductType effectiveType = request.getEffectiveType();

        Product product = new Product(
                seller,
                request.getTitle(),
                request.getDescription(),
                request.getPrice(),
                request.getCategory(),
                effectiveType
        );

        List<String> urls = request.getImageUrls() != null ? request.getImageUrls() : java.util.List.of();
        for (int i = 0; i < urls.size(); i++) {
            product.getImages().add(new ProductImage(product, urls.get(i), i));
        }

        Product saved = productRepository.save(product);
        Auction auction = null;
        if (effectiveType == ProductType.AUCTION && request.getAuctionStartPrice() != null) {
            String endAtStr = request.getAuctionEndAt();
            if (endAtStr != null && endAtStr.length() == 16) endAtStr += ":00";
            LocalDateTime endAt = LocalDateTime.parse(endAtStr, FMT);
            BigDecimal startPrice = BigDecimal.valueOf(request.getAuctionStartPrice());
            BigDecimal minIncrement = request.getAuctionMinBidIncrement() != null
                    ? BigDecimal.valueOf(request.getAuctionMinBidIncrement())
                    : BigDecimal.valueOf(1000);
            auction = auctionRepository.save(new Auction(saved, startPrice, minIncrement, endAt));
        }
        int listingCount = productRepository.countBySeller_Id(saved.getSeller().getId());
        return new ProductResponse(saved, auction, listingCount);
    }

    // ── 상품 목록 (커서 페이지네이션) ───────────────────────────
    @Transactional(readOnly = true)
    public ProductListResponse list(String categoryStr, String statusStr,
                                    String cursorCreatedAt, Long cursorId,
                                    String keyword, Long minPrice, Long maxPrice,
                                    String sort) {
        ProductCategory category = parseEnum(ProductCategory.class, categoryStr);
        ProductStatus   status   = parseEnum(ProductStatus.class, statusStr);
        LocalDateTime   cursor   = cursorCreatedAt != null ? LocalDateTime.parse(cursorCreatedAt, FMT) : null;
        String kw = (keyword != null && keyword.isBlank()) ? null : keyword;
        boolean popular = "POPULAR".equalsIgnoreCase(sort);

        List<Product> products;
        if (popular) {
            products = productRepository.findWithCursorPopular(
                    category, status, kw, minPrice, maxPrice,
                    PageRequest.of(0, PAGE_SIZE + 1)
            );
        } else {
            products = productRepository.findWithCursorLatest(
                    category, status, kw, minPrice, maxPrice, cursor, cursorId,
                    PageRequest.of(0, PAGE_SIZE + 1)
            );
        }

        boolean hasNext = products.size() > PAGE_SIZE;
        List<Product> page = hasNext ? products.subList(0, PAGE_SIZE) : products;

        List<Auction> auctions = auctionRepository.findByProductIn(page);
        java.util.Map<Long, Auction> auctionMap = auctions.stream()
                .collect(java.util.stream.Collectors.toMap(a -> a.getProduct().getId(), a -> a));

        String encodedCursor = null;
        if (hasNext && !popular) {
            Product last = page.get(page.size() - 1);
            String raw = last.getCreatedAt().format(FMT) + "|" + last.getId();
            encodedCursor = java.util.Base64.getEncoder().encodeToString(raw.getBytes());
        }

        return new ProductListResponse(
                page.stream().map(p -> new ProductResponse(p, auctionMap.get(p.getId()))).toList(),
                encodedCursor, hasNext
        );
    }

    // ── 상품 상세 ────────────────────────────────────────────────
    @Transactional
    public ProductResponse getById(Long id) {
        Product product = findOrThrow(id);
        if (!product.isDeleted()) {
            product.incrementViewCount();
        }
        Auction auction = auctionRepository.findByProduct_Id(id).orElse(null);
        Product saved = productRepository.save(product);
        int listingCount = productRepository.countBySeller_Id(saved.getSeller().getId());
        boolean hasTrade = tradeRepository.findByProduct_Id(id).isPresent();
        return new ProductResponse(saved, auction, listingCount, hasTrade);
    }

    // ── 상품 수정 ────────────────────────────────────────────────
    @Transactional
    public ProductResponse update(Long id, String sellerEmail, ProductCreateRequest request) {
        Product product = findOrThrow(id);
        checkOwner(product, sellerEmail);
        product.update(request.getTitle(), request.getDescription(), request.getPrice(), request.getCategory());

        product.getImages().clear();
        if (request.getImageUrls() != null) {
            for (int i = 0; i < request.getImageUrls().size(); i++) {
                product.getImages().add(new ProductImage(product, request.getImageUrls().get(i), i));
            }
        }

        Product saved = productRepository.save(product);
        int listingCount = productRepository.countBySeller_Id(saved.getSeller().getId());
        return new ProductResponse(saved, null, listingCount);
    }

    // ── 상태 변경 ────────────────────────────────────────────────
    @Transactional
    public ProductResponse changeStatus(Long id, String sellerEmail, String statusStr) {
        Product product = findOrThrow(id);
        checkOwner(product, sellerEmail);
        
        ProductStatus newStatus = ProductStatus.valueOf(statusStr.toUpperCase());
        if (product.getStatus() == ProductStatus.SOLD && newStatus != ProductStatus.SOLD) {
            if (tradeRepository.findByProduct_Id(product.getId()).isPresent()) {
                throw new IllegalStateException("거래 내역이 존재하는 상품은 판매 상태를 임의로 변경할 수 없습니다.");
            }
        }
        
        product.changeStatus(newStatus);
        Product saved = productRepository.save(product);
        int listingCount = productRepository.countBySeller_Id(saved.getSeller().getId());
        return new ProductResponse(saved, null, listingCount);
    }

    // ── 삭제 ────────────────────────────────────────────────────
    @Transactional
    public void delete(Long id, String sellerEmail) {
        Product product = findOrThrow(id);
        checkOwner(product, sellerEmail);
        product.delete();
        productRepository.save(product);
    }

    // ── 숨기기 ────────────────────────────────────────────────────
    @Transactional
    public void toggleHide(Long id, String sellerEmail) {
        Product product = findOrThrow(id);
        checkOwner(product, sellerEmail);
        if (product.isHidden()) {
            product.unhide();
        } else {
            product.hide();
        }
        productRepository.save(product);
    }

    // ── 급상승 Top 10 ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ProductResponse> trending() {
        List<Product> products = productRepository.findTrending(LocalDateTime.now().minusHours(24), PageRequest.of(0, 10));
        List<Auction> auctions = auctionRepository.findByProductIn(products);
        java.util.Map<Long, Auction> auctionMap = auctions.stream()
                .collect(java.util.stream.Collectors.toMap(a -> a.getProduct().getId(), a -> a));

        return products.stream()
                .map(p -> new ProductResponse(p, auctionMap.get(p.getId())))
                .toList();
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────
    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
    }

    private void checkOwner(Product product, String email) {
        if (!product.getSeller().getEmail().equals(email))
            throw new AccessDeniedException("수정 권한이 없습니다.");
    }

    private <E extends Enum<E>> E parseEnum(Class<E> clazz, String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(clazz, value.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
