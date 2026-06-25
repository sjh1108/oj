package dev.algoj.domain.user.service;

import dev.algoj.domain.user.dto.AdminResetPasswordRequest;
import dev.algoj.domain.user.dto.AdminResetPasswordResponse;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    TemporaryPasswordGenerator temporaryPasswordGenerator;

    @InjectMocks
    AdminUserService service;

    @Test
    void resetPassword_byUsername_setsEncodedTempPassword_andReturnsPlaintext() {
        User user = user("alice", "alice@study.dev", "OLD_HASH");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(temporaryPasswordGenerator.generate()).thenReturn("TempPass23");
        when(passwordEncoder.encode("TempPass23")).thenReturn("ENCODED");

        AdminResetPasswordResponse res =
                service.resetPassword(new AdminResetPasswordRequest("alice"));

        // The plaintext we hand back is exactly what was hashed and stored.
        assertThat(res.temporaryPassword()).isEqualTo("TempPass23");
        assertThat(res.username()).isEqualTo("alice");
        assertThat(res.email()).isEqualTo("alice@study.dev");
        assertThat(user.getPassword()).isEqualTo("ENCODED");
    }

    @Test
    void resetPassword_fallsBackToEmailLookup() {
        User user = user("bob", "bob@study.dev", "OLD_HASH");
        when(userRepository.findByUsername("bob@study.dev")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("bob@study.dev")).thenReturn(Optional.of(user));
        when(temporaryPasswordGenerator.generate()).thenReturn("TempPass23");
        when(passwordEncoder.encode("TempPass23")).thenReturn("ENCODED");

        AdminResetPasswordResponse res =
                service.resetPassword(new AdminResetPasswordRequest("bob@study.dev"));

        assertThat(res.username()).isEqualTo("bob");
    }

    @Test
    void resetPassword_whenUserNotFound_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.resetPassword(new AdminResetPasswordRequest("ghost")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private User user(String username, String email, String password) {
        return User.builder()
                .username(username)
                .email(email)
                .password(password)
                .role(User.Role.USER)
                .build();
    }
}
