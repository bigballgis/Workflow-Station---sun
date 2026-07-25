package com.developer.component;

import com.developer.entity.UserPreference;
import com.developer.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 用户 UI 偏好读写（按 userId + prefKey 唯一，upsert 语义）
 */
@Component
@RequiredArgsConstructor
public class UserPreferenceComponent {

    private final UserPreferenceRepository repository;

    @Transactional(readOnly = true)
    public Optional<String> get(String userId, String prefKey) {
        return repository.findByUserIdAndPrefKey(userId, prefKey)
                .map(UserPreference::getPrefValue);
    }

    @Transactional
    public void save(String userId, String prefKey, String value) {
        UserPreference pref = repository.findByUserIdAndPrefKey(userId, prefKey)
                .orElseGet(() -> UserPreference.builder()
                        .userId(userId)
                        .prefKey(prefKey)
                        .build());
        pref.setPrefValue(value);
        repository.save(pref);
    }
}
