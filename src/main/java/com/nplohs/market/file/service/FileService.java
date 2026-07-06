package com.nplohs.market.file.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 자체 호스팅 서버용 파일 저장 서비스.
 * 파일을 로컬 디스크에 저장하고 정적 URL로 응답합니다.
 */
@Slf4j
@Service
public class FileService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${file.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20MB
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    /**
     * 파일을 로컬 디스크에 저장하고 접근 URL을 반환합니다.
     */
    public Map<String, String> uploadFile(MultipartFile file) throws IOException {
        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다. (JPEG/PNG/WebP/GIF만 가능)");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES)
            throw new IllegalArgumentException("파일 크기는 20MB 이하여야 합니다.");

        String ext = extractExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + ext;
        String subDir = "products";
        String key = subDir + "/" + filename;

        // 저장 디렉토리 생성
        File dir = new File(uploadDir + "/" + subDir);
        if (!dir.exists()) dir.mkdirs();

        // 파일 저장 (NIO Files.copy 방식이 더 안전함)
        File dest = new File(dir.getAbsolutePath(), filename);
        java.nio.file.Files.copy(file.getInputStream(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("파일 저장 완료: {}", dest.getAbsolutePath());

        String publicUrl = baseUrl + "/uploads/" + key;
        return Map.of(
                "publicUrl", publicUrl,
                "key", key
        );
    }

    /**
     * 파일 삭제
     */
    public void delete(String key) {
        File file = new File(uploadDir + "/" + key);
        if (file.exists()) {
            boolean deleted = file.delete();
            log.info("파일 삭제 {}: {}", deleted ? "성공" : "실패", key);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
