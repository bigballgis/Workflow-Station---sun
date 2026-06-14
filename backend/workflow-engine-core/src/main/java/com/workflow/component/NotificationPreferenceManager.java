package com.workflow.component;

import com.workflow.component.NotificationManagerComponent.UserNotificationPreference;
import com.workflow.dto.response.NotificationResult;
import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * User notification-preference management for the notification subsystem.
 *
 * <p>Extracted from {@link NotificationManagerComponent}; behaviour is preserved verbatim. Stateless:
 * reads/writes the shared {@link NotificationContext} passed on each call. Preference-driven channel
 * gating (do-not-disturb / enabled channels) lives in {@link NotificationContext} since several
 * collaborators depend on it.</p>
 */
@Slf4j
@Component
class NotificationPreferenceManager {

    /**
     * Set user notification preference
     */
    NotificationResult setUserNotificationPreference(NotificationContext ctx, UserNotificationPreference preference) {
        log.info("Setting user notification preference: userId={}", preference.getUserId());

        try {
            preference.setUpdatedTime(LocalDateTime.now());
            ctx.userPreferences.put(preference.getUserId(), preference);

            // Cache to Redis
            String cacheKey = NotificationContext.NOTIFICATION_PREFIX + "preference:" + preference.getUserId();
            String preferenceJson = ctx.objectMapper.writeValueAsString(preference);
            ctx.stringRedisTemplate.opsForValue().set(cacheKey, preferenceJson, Duration.ofDays(365));

            return NotificationResult.builder()
                    .success(true)
                    .message("User notification preference set successfully")
                    .build();

        } catch (Exception e) {
            log.error("Failed to set user notification preference: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("PREFERENCE_SET_FAILED", "Failed to set user notification preference: " + e.getMessage());
        }
    }
}
