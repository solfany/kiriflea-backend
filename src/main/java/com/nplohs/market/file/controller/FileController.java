package com.nplohs.market.file.controller;

import com.nplohs.market.common.response.ApiResponse;
import com.nplohs.market.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * POST /api/images/upload  (프론트 호출 경로)
     * POST /api/upload         (하위 호환)
     * form-data: file
     * Returns: { id, url }
     */
    @PostMapping(value = {"/api/images/upload", "/api/upload"}, consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestParam("file") MultipartFile file) throws IOException {
        Map<String, String> result = fileService.uploadFile(file);
        // 프론트 기대값: { id: number, url: string }
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "id",  0,
                "url", result.getOrDefault("publicUrl", result.getOrDefault("url", ""))
        )));
    }

    /**
     * DELETE /api/upload?key=products/xxx.jpg
     */
    @DeleteMapping("/api/upload")
    public ResponseEntity<ApiResponse<Void>> delete(@RequestParam String key) {
        fileService.delete(key);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
