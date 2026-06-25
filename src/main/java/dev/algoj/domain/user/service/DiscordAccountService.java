package dev.algoj.domain.user.service;

import dev.algoj.domain.user.dto.DiscordLinkCodeResponse;
import dev.algoj.domain.user.dto.DiscordLinkRequest;
import dev.algoj.domain.user.dto.DiscordLinkResult;
import dev.algoj.domain.user.dto.DiscordResetPasswordRequest;
import dev.algoj.domain.user.dto.DiscordResetPasswordResult;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscordAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final DiscordLinkCodeStore linkCodeStore;

    /** Web (authenticated): issue a one-time code the user types in Discord. */
    @Transactional(readOnly = true)
    public DiscordLinkCodeResponse issueLinkCode(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        DiscordLinkCodeStore.IssuedCode issued = linkCodeStore.issue(userId);
        return new DiscordLinkCodeResponse(issued.code(), issued.expiresInSeconds());
    }

    /** Bot: bind a Discord user to the OJ account that issued the code. */
    @Transactional
    public DiscordLinkResult link(DiscordLinkRequest req) {
        Long userId = linkCodeStore.consume(req.code())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LINK_CODE));

        userRepository.findByDiscordUserId(req.discordUserId()).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.DISCORD_ALREADY_LINKED);
            }
        });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.linkDiscord(req.discordUserId());
        return new DiscordLinkResult(user.getUsername());
    }

    /** Bot: reset the password of the account linked to this Discord user. */
    @Transactional
    public DiscordResetPasswordResult resetPassword(DiscordResetPasswordRequest req) {
        User user = userRepository.findByDiscordUserId(req.discordUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DISCORD_NOT_LINKED));

        String temporaryPassword = temporaryPasswordGenerator.generate();
        user.changePassword(passwordEncoder.encode(temporaryPassword));

        return new DiscordResetPasswordResult(user.getUsername(), temporaryPassword);
    }
}
