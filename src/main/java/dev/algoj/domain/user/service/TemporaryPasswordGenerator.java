package dev.algoj.domain.user.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates short, human-relayable temporary passwords.
 * Shared by admin reset and Discord reset flows.
 */
@Component
public class TemporaryPasswordGenerator {

    // Unambiguous alphabet — excludes 0/O, 1/l/I to avoid confusion when relayed.
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private static final int LENGTH = 10;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
