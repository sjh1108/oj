package dev.algoj.domain.user.dto;

import dev.algoj.domain.user.entity.UserPreferences;

public record UserPreferencesResponse(
        boolean showTags
) {
    public static UserPreferencesResponse from(UserPreferences preferences) {
        return new UserPreferencesResponse(preferences.isShowTags());
    }

    // 아직 설정을 한 번도 바꾸지 않은 유저(행 없음)에게 주는 값.
    public static UserPreferencesResponse defaults() {
        return new UserPreferencesResponse(UserPreferences.DEFAULT_SHOW_TAGS);
    }
}
