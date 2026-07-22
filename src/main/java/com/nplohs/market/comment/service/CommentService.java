package com.nplohs.market.comment.service;

import com.nplohs.market.user.entity.User;
import com.nplohs.market.user.repository.UserRepository;
import com.nplohs.market.comment.entity.Comment;
import com.nplohs.market.comment.repository.CommentRepository;
import com.nplohs.market.common.ratelimit.RateLimiter;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository    userRepository;
    private final RateLimiter       rateLimiter;

    private static final int    COMMENT_MAX_PER_WINDOW = 10;
    private static final Duration COMMENT_WINDOW = Duration.ofMinutes(1);
    private final ProductRepository productRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long productId, String viewerEmail) {
        User viewer = viewerEmail != null
                ? userRepository.findByEmail(viewerEmail).orElse(null)
                : null;

        return commentRepository.findTopLevelByProductId(productId).stream()
                .map(c -> toResponse(c, viewer))
                .toList();
    }

    @Transactional
    public CommentResponse create(Long productId, String authorEmail, CreateRequest req) {
        if (!rateLimiter.tryAcquire("comment:create:" + authorEmail, COMMENT_MAX_PER_WINDOW, COMMENT_WINDOW)) {
            throw new IllegalStateException("댓글을 너무 빠르게 작성하고 있습니다. 잠시 후 다시 시도해주세요.");
        }

        User    author  = userRepository.findByEmail(authorEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        if (product.isDeleted()) {
            throw new IllegalStateException("삭제된 상품에는 댓글을 작성할 수 없습니다.");
        }

        Comment parent = null;
        if (req.parentId() != null) {
            parent = commentRepository.findById(req.parentId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent comment not found"));
        }

        Comment saved = commentRepository.save(
                new Comment(product, author, req.content(), req.isPrivate() != null && req.isPrivate(), parent));
        return toResponse(saved, author);
    }

    @Transactional
    public void delete(Long commentId, String requesterEmail) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        boolean isAuthor = comment.getAuthor().getEmail().equals(requesterEmail);
        if (!isAuthor) {
            throw new SecurityException("삭제 권한이 없습니다");
        }
        commentRepository.delete(comment);
    }

    @Transactional
    public CommentResponse edit(Long commentId, String requesterEmail, EditRequest req) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        
        boolean isAuthor = comment.getAuthor().getEmail().equals(requesterEmail);
        if (!isAuthor) {
            throw new SecurityException("수정 권한이 없습니다");
        }

        if (req.content() != null && req.content().isBlank()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }

        boolean secret = req.isPrivate() != null ? req.isPrivate() : comment.isSecret();
        comment.update(req.content() != null ? req.content() : comment.getContent(), secret);

        User author = userRepository.findByEmail(requesterEmail).orElseThrow();
        return toResponse(comment, author);
    }

    private CommentResponse toResponse(Comment c, User viewer) {
        boolean canSee = !c.isSecret()
                || (viewer != null && (c.getAuthor().getId().equals(viewer.getId())
                    || c.getProduct().getSeller().getId().equals(viewer.getId())));

        String content = canSee ? c.getContent() : "(비공개 댓글입니다)";

        List<CommentResponse> replies = c.getReplies().stream()
                .map(r -> toResponse(r, viewer))
                .toList();

        Map<String, Object> authorMap = new java.util.HashMap<>();
        authorMap.put("id", c.getAuthor().getId());
        authorMap.put("nickname", c.getAuthor().getNickname());
        authorMap.put("profileImage", c.getAuthor().getProfileImage());

        return new CommentResponse(
                c.getId(),
                authorMap,
                content,
                c.isSecret(),
                c.getParent() != null ? c.getParent().getId() : null,
                replies,
                c.getCreatedAt().format(FMT)
        );
    }

    public record CreateRequest(
            @NotBlank(message = "댓글 내용을 입력해주세요.") @Size(max = 1000, message = "댓글은 1000자 이하로 작성해주세요.") String content,
            Boolean isPrivate,
            Long parentId
    ) {}

    // content가 null이면 기존 내용을 유지(비공개 여부만 변경)하는 부분 수정이 허용되므로 @NotBlank는 걸지 않는다.
    public record EditRequest(
            @Size(max = 1000, message = "댓글은 1000자 이하로 작성해주세요.") String content,
            Boolean isPrivate
    ) {}

    public record CommentResponse(
            Long id,
            Map<String, Object> author,
            String content,
            boolean isPrivate,
            Long parentId,
            List<CommentResponse> replies,
            String createdAt
    ) {}
}
