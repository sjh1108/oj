package dev.algoj.domain.user.service;

import dev.algoj.domain.user.dto.ChangePasswordRequest;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import dev.algoj.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceChangePasswordTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    AuthService service;

    @Test
    void changePassword_withCorrectCurrent_replacesHash() {
        User user = user("OLD_HASH");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "OLD_HASH")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("NEW_HASH");

        service.changePassword(1L, new ChangePasswordRequest("current", "newpassword"));

        assertThat(user.getPassword()).isEqualTo("NEW_HASH");
    }

    @Test
    void changePassword_withWrongCurrent_throwsAndKeepsHash() {
        User user = user("OLD_HASH");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "OLD_HASH")).thenReturn(false);

        assertThatThrownBy(() ->
                service.changePassword(1L, new ChangePasswordRequest("wrong", "newpassword")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);

        verify(passwordEncoder, never()).encode("newpassword");
        assertThat(user.getPassword()).isEqualTo("OLD_HASH");
    }

    @Test
    void changePassword_whenUserMissing_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.changePassword(99L, new ChangePasswordRequest("current", "newpassword")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private User user(String passwordHash) {
        return User.builder()
                .username("alice")
                .email("alice@study.dev")
                .password(passwordHash)
                .role(User.Role.USER)
                .build();
    }
}
