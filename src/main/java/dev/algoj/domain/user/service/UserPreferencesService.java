package dev.algoj.domain.user.service;

import dev.algoj.domain.user.dto.UpdateUserPreferencesRequest;
import dev.algoj.domain.user.dto.UserPreferencesResponse;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.entity.UserPreferences;
import dev.algoj.domain.user.repository.UserPreferencesRepository;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferencesService {

    private final UserPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;

    /** 행이 없으면(설정을 한 번도 안 바꾼 유저) 기본값을 돌려준다. */
    @Transactional(readOnly = true)
    public UserPreferencesResponse get(Long userId) {
        return preferencesRepository.findById(userId)
                .map(UserPreferencesResponse::from)
                .orElseGet(UserPreferencesResponse::defaults);
    }

    /** 없으면 기본값 행을 만들어 저장한다(upsert). */
    @Transactional
    public UserPreferencesResponse update(Long userId, UpdateUserPreferencesRequest request) {
        UserPreferences preferences = preferencesRepository.findById(userId)
                .orElseGet(() -> UserPreferences.defaultsFor(findUser(userId)));
        preferences.updateShowTags(request.showTags());
        return UserPreferencesResponse.from(preferencesRepository.save(preferences));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
