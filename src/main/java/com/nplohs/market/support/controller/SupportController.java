package com.nplohs.market.support.controller;

import com.nplohs.market.auth.service.EmailService;
import com.nplohs.market.support.dto.ContactRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final EmailService emailService;

    @PostMapping("/contact")
    public ResponseEntity<Void> submitContact(@RequestBody ContactRequest request) {
        if (request.getEmail() == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        emailService.sendDeveloperContactEmail(request.getEmail(), request.getContent());
        return ResponseEntity.ok().build();
    }
}
