package dev.algoj.domain.user.controller;

import dev.algoj.domain.user.dto.DiscordLinkRequest;
import dev.algoj.domain.user.dto.DiscordLinkResult;
import dev.algoj.domain.user.dto.DiscordResetPasswordRequest;
import dev.algoj.domain.user.dto.DiscordResetPasswordResult;
import dev.algoj.domain.user.service.DiscordAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bot-facing endpoints. Not behind JWT — protected by the bot API key
 * (see BotApiKeyFilter) which is required for any /api/internal/** path.
 */
@RestController
@RequestMapping("/api/internal/discord")
@RequiredArgsConstructor
public class DiscordInternalController {

    private final DiscordAccountService discordAccountService;

    @PostMapping("/link")
    public ResponseEntity<DiscordLinkResult> link(@Valid @RequestBody DiscordLinkRequest request) {
        return ResponseEntity.ok(discordAccountService.link(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<DiscordResetPasswordResult> resetPassword(
            @Valid @RequestBody DiscordResetPasswordRequest request) {
        return ResponseEntity.ok(discordAccountService.resetPassword(request));
    }
}
