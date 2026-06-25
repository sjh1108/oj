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

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;

    @Transactional
    public AdminResetPasswordResponse resetPassword(AdminResetPasswordRequest req) {
        User user = userRepository.findByUsername(req.usernameOrEmail())
                .or(() -> userRepository.findByEmail(req.usernameOrEmail()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String temporaryPassword = temporaryPasswordGenerator.generate();
        user.changePassword(passwordEncoder.encode(temporaryPassword));

        return new AdminResetPasswordResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                temporaryPassword
        );
    }
}
