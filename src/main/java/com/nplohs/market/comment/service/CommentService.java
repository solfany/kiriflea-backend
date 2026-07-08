package com.nplohs.market.comment.service;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.comment.entity.Comment;
import com.nplohs.market.comment.repository.CommentRepository;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository    userRepository;
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

        if (req.content() != null && !req.content().isBlank()) {
            comment.setContent(req.content());
        }
        if (req.isPrivate() != null) {
            comment.setSecret(req.isPrivate());
        }

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

    public record CreateRequest(String content, Boolean isPrivate, Long parentId) {}

    public record EditRequest(String content, Boolean isPrivate) {}

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
