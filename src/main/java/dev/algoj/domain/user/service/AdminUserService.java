package dev.algoj.domain.user.service;

import dev.algoj.domain.user.dto.AdminResetPasswordRequest;
import dev.algoj.domain.user.dto.AdminResetPasswordResponse;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    // Unambiguous alphabet — excludes 0/O, 1/l/I to avoid confusion when relayed.
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private static final int TEMP_PASSWORD_LENGTH = 10;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AdminResetPasswordResponse resetPassword(AdminResetPasswordRequest req) {
        User user = userRepository.findByUsername(req.usernameOrEmail())
                .or(() -> userRepository.findByEmail(req.usernameOrEmail()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String temporaryPassword = generateTempPassword();
        user.changePassword(passwordEncoder.encode(temporaryPassword));

        return new AdminResetPasswordResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                temporaryPassword
        );
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
