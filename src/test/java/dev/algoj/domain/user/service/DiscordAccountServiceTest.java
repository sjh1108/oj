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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscordAccountServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    TemporaryPasswordGenerator temporaryPasswordGenerator;
    @Mock
    DiscordLinkCodeStore linkCodeStore;

    @InjectMocks
    DiscordAccountService service;

    @Test
    void issueLinkCode_returnsCode() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(linkCodeStore.issue(1L)).thenReturn(new DiscordLinkCodeStore.IssuedCode("ABC123", 600));

        DiscordLinkCodeResponse res = service.issueLinkCode(1L);

        assertThat(res.code()).isEqualTo("ABC123");
        assertThat(res.expiresInSeconds()).isEqualTo(600);
    }

    @Test
    void link_validCode_bindsDiscordId() {
        User user = user(1L, "alice");
        when(linkCodeStore.consume("ABC123")).thenReturn(Optional.of(1L));
        when(userRepository.findByDiscordUserId("disc-1")).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        DiscordLinkResult res = service.link(new DiscordLinkRequest("disc-1", "ABC123"));

        assertThat(res.username()).isEqualTo("alice");
        assertThat(user.getDiscordUserId()).isEqualTo("disc-1");
    }

    @Test
    void link_invalidCode_throws() {
        when(linkCodeStore.consume("BAD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(new DiscordLinkRequest("disc-1", "BAD")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_LINK_CODE);
    }

    @Test
    void link_discordAlreadyOnAnotherAccount_throws() {
        User other = user(2L, "bob");
        when(linkCodeStore.consume("ABC123")).thenReturn(Optional.of(1L));
        when(userRepository.findByDiscordUserId("disc-1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.link(new DiscordLinkRequest("disc-1", "ABC123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DISCORD_ALREADY_LINKED);
    }

    @Test
    void resetPassword_linkedUser_returnsTempPassword() {
        User user = user(1L, "alice");
        ReflectionTestUtils.setField(user, "password", "OLD");
        when(userRepository.findByDiscordUserId("disc-1")).thenReturn(Optional.of(user));
        when(temporaryPasswordGenerator.generate()).thenReturn("TempPass23");
        when(passwordEncoder.encode("TempPass23")).thenReturn("ENC");

        DiscordResetPasswordResult res =
                service.resetPassword(new DiscordResetPasswordRequest("disc-1"));

        assertThat(res.username()).isEqualTo("alice");
        assertThat(res.temporaryPassword()).isEqualTo("TempPass23");
        assertThat(user.getPassword()).isEqualTo("ENC");
    }

    @Test
    void resetPassword_notLinked_throws() {
        when(userRepository.findByDiscordUserId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.resetPassword(new DiscordResetPasswordRequest("ghost")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DISCORD_NOT_LINKED);
    }

    private User user(Long id, String username) {
        User u = User.builder()
                .username(username)
                .email(username + "@study.dev")
                .password("HASH")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}
