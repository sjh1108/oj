package dev.algoj.domain.user.service;

import dev.algoj.domain.user.dto.UpdateUserPreferencesRequest;
import dev.algoj.domain.user.dto.UserPreferencesResponse;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.entity.UserPreferences;
import dev.algoj.domain.user.repository.UserPreferencesRepository;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    UserPreferencesRepository preferencesRepository;
    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserPreferencesService service;

    @Test
    void get_withoutRow_returnsDefaults() {
        when(preferencesRepository.findById(1L)).thenReturn(Optional.empty());

        UserPreferencesResponse response = service.get(1L);

        // 태그 기본값은 숨김 — 설정을 한 번도 안 바꾼 유저에게 스포일러가 노출되면 안 된다.
        assertThat(response.showTags()).isFalse();
    }

    @Test
    void get_withRow_returnsStoredValue() {
        UserPreferences preferences = UserPreferences.defaultsFor(user());
        preferences.updateShowTags(true);
        when(preferencesRepository.findById(1L)).thenReturn(Optional.of(preferences));

        assertThat(service.get(1L).showTags()).isTrue();
    }

    @Test
    void update_withoutRow_createsOneFromUser() {
        when(preferencesRepository.findById(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(preferencesRepository.save(any(UserPreferences.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesResponse response =
                service.update(1L, new UpdateUserPreferencesRequest(true));

        assertThat(response.showTags()).isTrue();
    }

    @Test
    void update_withRow_updatesInPlace() {
        UserPreferences preferences = UserPreferences.defaultsFor(user());
        preferences.updateShowTags(true);
        when(preferencesRepository.findById(1L)).thenReturn(Optional.of(preferences));
        when(preferencesRepository.save(preferences)).thenReturn(preferences);

        UserPreferencesResponse response =
                service.update(1L, new UpdateUserPreferencesRequest(false));

        assertThat(response.showTags()).isFalse();
        assertThat(preferences.isShowTags()).isFalse();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void update_withUnknownUser_throws() {
        when(preferencesRepository.findById(9L)).thenReturn(Optional.empty());
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, new UpdateUserPreferencesRequest(true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(preferencesRepository, never()).save(any());
    }

    private User user() {
        return User.builder()
                .username("tester")
                .email("tester@example.com")
                .password("HASH")
                .role(User.Role.USER)
                .build();
    }
}
