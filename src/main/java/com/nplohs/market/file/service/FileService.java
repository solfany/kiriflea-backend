package com.nplohs.market.file.service;

import com.nplohs.market.user.repository.UserRepository;
import com.nplohs.market.product.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * 자체 호스팅 서버용 파일 저장 서비스.
 * 파일을 로컬 디스크에 저장하고 정적 URL로 응답합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${file.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20MB

    /**
     * 파일을 로컬 디스크에 저장하고 접근 URL을 반환합니다.
     * Content-Type 헤더는 클라이언트가 임의로 지정할 수 있으므로 신뢰하지 않고,
     * 실제 파일 바이트(매직 넘버)를 검사해 형식/확장자를 직접 결정한다.
     */
    public Map<String, String> uploadFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("파일 크기는 20MB 이하여야 합니다.");
        }

        byte[] header = readHeader(file);
        String ext = detectImageExtension(header);
        if (ext == null) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다. (JPEG/PNG/WebP/GIF만 가능)");
        }

        String filename = UUID.randomUUID() + ext;
        String subDir = "products";
        String key = subDir + "/" + filename;

        Path dir = resolveWithinUploadDir(subDir);
        Files.createDirectories(dir);

        Path dest = dir.resolve(filename);
        try (var in = file.getInputStream()) {
            Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("파일 저장 완료: {}", dest);

        String publicUrl = baseUrl + "/uploads/" + key;
        return Map.of(
                "publicUrl", publicUrl,
                "key", key
        );
    }

    /**
     * 파일 삭제. 이 엔드포인트는 상품 이미지뿐 아니라 프로필 이미지 업로드에도 재사용되므로,
     * 두 테이블 모두에서 소유자를 확인한다. (아직 어떤 상품/프로필에도 연결되지 않은, 업로드
     * 직후의 임시 이미지는 소유자 판단이 불가능하므로 허용)
     */
    public void delete(String key, Long requesterId) {
        Long productOwnerId = productImageRepository.findOwnerIdByKey(key).orElse(null);
        if (productOwnerId != null && !productOwnerId.equals(requesterId)) {
            throw new org.springframework.security.access.AccessDeniedException("본인이 등록한 이미지만 삭제할 수 있습니다.");
        }

        Long profileOwnerId = userRepository.findIdByProfileImageKey(key).orElse(null);
        if (profileOwnerId != null && !profileOwnerId.equals(requesterId)) {
            throw new org.springframework.security.access.AccessDeniedException("본인의 프로필 이미지만 삭제할 수 있습니다.");
        }

        Path target;
        try {
            target = resolveWithinUploadDir(key);
        } catch (IllegalArgumentException e) {
            log.warn("파일 삭제 거부 (경로 이탈 시도): {}", key);
            throw e;
        }

        File file = target.toFile();
        if (file.exists()) {
            boolean deleted = file.delete();
            log.info("파일 삭제 {}: {}", deleted ? "성공" : "실패", key);
        }
    }

    /**
     * uploadDir 하위로 정규화된 경로만 반환한다. "../" 등으로 uploadDir 밖을 가리키면 예외를 던진다.
     */
    private Path resolveWithinUploadDir(String relativePath) {
        Path base = new File(uploadDir).toPath().toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("잘못된 경로입니다.");
        }
        return resolved;
    }

    private byte[] readHeader(MultipartFile file) throws IOException {
        byte[] buf = new byte[16];
        try (var in = file.getInputStream()) {
            int read = in.readNBytes(buf, 0, buf.length);
            if (read < buf.length) {
                buf = java.util.Arrays.copyOf(buf, read);
            }
        }
        return buf;
    }

    /** 파일의 매직 넘버를 검사해 허용된 이미지 형식이면 확장자를, 아니면 null을 반환한다. */
    private String detectImageExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return ".jpg"; // JPEG: FF D8 FF
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return ".png"; // PNG: 89 50 4E 47 0D 0A 1A 0A
        }
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
            return ".gif"; // GIF87a / GIF89a
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return ".webp"; // RIFF....WEBP
        }
        return null;
    }
}
