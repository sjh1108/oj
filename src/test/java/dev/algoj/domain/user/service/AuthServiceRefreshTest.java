package dev.algoj.domain.user.service;

import dev.algoj.domain.user.dto.RefreshRequest;
import dev.algoj.domain.user.dto.TokenResponse;
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
class AuthServiceRefreshTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    AuthService service;

    @Test
    void refresh_withValidRefreshToken_issuesNewTokens() {
        User user = user();
        when(jwtTokenProvider.isRefreshToken("REFRESH")).thenReturn(true);
        when(jwtTokenProvider.getUserId("REFRESH")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("NEW_ACCESS");
        when(jwtTokenProvider.createRefreshToken(user)).thenReturn("NEW_REFRESH");
        when(jwtTokenProvider.getAccessTokenValidity()).thenReturn(3_600_000L);

        TokenResponse response = service.refresh(new RefreshRequest("REFRESH"));

        assertThat(response.accessToken()).isEqualTo("NEW_ACCESS");
        assertThat(response.refreshToken()).isEqualTo("NEW_REFRESH");
        assertThat(response.expiresIn()).isEqualTo(3_600L);
    }

    @Test
    void refresh_withAccessToken_throwsInvalidToken() {
        // An access token (or any non-refresh type) must be rejected at this endpoint.
        when(jwtTokenProvider.isRefreshToken("ACCESS")).thenReturn(false);

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("ACCESS")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void refresh_withExpiredToken_propagatesExpiredToken() {
        when(jwtTokenProvider.isRefreshToken("EXPIRED"))
                .thenThrow(new BusinessException(ErrorCode.EXPIRED_TOKEN));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("EXPIRED")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    void refresh_whenUserMissing_throwsUserNotFound() {
        when(jwtTokenProvider.isRefreshToken("REFRESH")).thenReturn(true);
        when(jwtTokenProvider.getUserId("REFRESH")).thenReturn(99L);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("REFRESH")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private User user() {
        return User.builder()
                .username("alice")
                .email("alice@study.dev")
                .password("HASH")
                .role(User.Role.USER)
                .build();
    }
}
