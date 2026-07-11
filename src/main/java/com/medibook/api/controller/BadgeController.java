package com.medibook.api.controller;

import com.medibook.api.dto.Badge.BadgeProgressSummaryDTO;
import com.medibook.api.dto.Badge.BadgesResponseDTO;
import com.medibook.api.service.BadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
@Slf4j
public class BadgeController {

    private final BadgeService badgeService;

    @GetMapping("/{userId}")
    @PreAuthorize("#userId.equals(authentication.principal.id) or hasRole('ADMIN')")
    public ResponseEntity<BadgesResponseDTO> getUserBadges(@PathVariable UUID userId) {
        BadgesResponseDTO badges = badgeService.getUserBadges(userId);
        return ResponseEntity.ok(badges);
    }

    @GetMapping("/my-badges")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<BadgesResponseDTO> getMyBadges(Authentication authentication) {
        UUID userId = ((com.medibook.api.entity.User) authentication.getPrincipal()).getId();
        BadgesResponseDTO badges = badgeService.getUserBadges(userId);
        return ResponseEntity.ok(badges);
    }

    @GetMapping("/{userId}/progress")
    @PreAuthorize("#userId.equals(authentication.principal.id) or hasRole('ADMIN')")
    public ResponseEntity<List<BadgeProgressSummaryDTO>> getUserBadgeProgress(@PathVariable UUID userId) {
        List<BadgeProgressSummaryDTO> progress = badgeService.getUserBadgeProgress(userId);
        return ResponseEntity.ok(progress);
    }

    @GetMapping("/my-progress")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<List<BadgeProgressSummaryDTO>> getMyBadgeProgress(Authentication authentication) {
        UUID userId = ((com.medibook.api.entity.User) authentication.getPrincipal()).getId();
        List<BadgeProgressSummaryDTO> progress = badgeService.getUserBadgeProgress(userId);
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/{userId}/evaluate")
    @PreAuthorize("#userId.equals(authentication.principal.id) or hasRole('ADMIN')")
    public ResponseEntity<Void> evaluateUserBadges(@PathVariable UUID userId) {
        badgeService.evaluateAllBadges(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<Void> evaluateMyBadges(Authentication authentication) {
        UUID userId = ((com.medibook.api.entity.User) authentication.getPrincipal()).getId();
        badgeService.evaluateAllBadges(userId);
        return ResponseEntity.ok().build();
    }
}