package dev.algoj.domain.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived store mapping a one-time link code -> OJ userId. In-memory (single instance);
 * codes are intentionally ephemeral, so losing them on restart is acceptable. A member
 * generates a code in the web app and types it in Discord (/연동) to bind their account.
 */
@Component
public class DiscordLinkCodeStore {

    // 6 chars, unambiguous, uppercase — easy to read and type in Discord.
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Entry> codes = new ConcurrentHashMap<>();

    private final long ttlSeconds;

    public DiscordLinkCodeStore(@Value("${discord.link-code-ttl-seconds:600}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public record IssuedCode(String code, long expiresInSeconds) {}

    public IssuedCode issue(Long userId) {
        purgeExpired();
        String code = randomCode();
        codes.put(code, new Entry(userId, Instant.now().plusSeconds(ttlSeconds)));
        return new IssuedCode(code, ttlSeconds);
    }

    /** Consumes the code (single use). Returns the userId if valid and unexpired. */
    public Optional<Long> consume(String code) {
        if (code == null) return Optional.empty();
        Entry entry = codes.remove(code.trim().toUpperCase());
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        codes.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    public Duration ttl() {
        return Duration.ofSeconds(ttlSeconds);
    }

    private record Entry(Long userId, Instant expiresAt) {}
}
