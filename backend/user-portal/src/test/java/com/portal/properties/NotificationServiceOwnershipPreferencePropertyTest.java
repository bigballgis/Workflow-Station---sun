package com.portal.properties;

import com.platform.messaging.event.NotificationEvent;
import com.portal.entity.Notification;
import com.portal.entity.NotificationPreference;
import com.portal.enums.NotificationType;
import com.portal.exception.PortalException;
import com.portal.repository.NotificationPreferenceRepository;
import com.portal.repository.NotificationRepository;
import com.portal.service.NotificationServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property tests for NotificationService - Ownership and Preference filtering.
 * Feature: kafka-in-app-messaging
 *
 * Validates: Requirements 3.7, 8.1, 8.2
 */
class NotificationServiceOwnershipPreferencePropertyTest {

    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository notificationPreferenceRepository;
    private NotificationServiceImpl service;

    private void setupMocks() {
        notificationRepository = mock(NotificationRepository.class);
        notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
        service = new NotificationServiceImpl(notificationRepository, notificationPreferenceRepository);
    }

    /**
     * Property 8: 通知所有权校验
     *
     * For any notification belonging to userA, when userB (different from userA) executes
     * markAsRead or deleteNotification, a 403 exception should be thrown.
     *
     * Validates: Requirements 3.7
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 8: 通知所有权校验")
    void ownershipCheckShouldReject403ForDifferentUser(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String ownerUserId,
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String otherUserId,
            @ForAll("notificationTypes") NotificationType type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String title) {

        // Ensure the two users are different
        Assume.that(!ownerUserId.equals(otherUserId));

        setupMocks();

        // Create a notification belonging to ownerUserId
        Notification notification = Notification.builder()
                .id(1L)
                .userId(ownerUserId)
                .type(type)
                .title(title)
                .content("test content")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        // markAsRead by a different user should throw 403
        assertThatThrownBy(() -> service.markAsRead(otherUserId, 1L))
                .isInstanceOf(PortalException.class)
                .satisfies(ex -> assertThat(((PortalException) ex).getCode()).isEqualTo("403"));

        // deleteNotification by a different user should throw 403
        assertThatThrownBy(() -> service.deleteNotification(otherUserId, 1L))
                .isInstanceOf(PortalException.class)
                .satisfies(ex -> assertThat(((PortalException) ex).getCode()).isEqualTo("403"));

        // Verify that save and delete were never called (operation was rejected)
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(notificationRepository, never()).delete(any(Notification.class));
    }

    /**
     * Property 9: 通知偏好过滤
     *
     * For any NotificationEvent, when the target user has inAppEnabled=false for that
     * notification type, createFromEvent should NOT persist the notification (returns null
     * and does not call notificationRepository.save()).
     *
     * Validates: Requirements 8.1, 8.2
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 9: 通知偏好过滤")
    void preferenceFilteringShouldSkipWhenInAppDisabled(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String targetUserId,
            @ForAll("notificationTypeStrings") String notificationType,
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String title,
            @ForAll @AlphaChars @StringLength(min = 1, max = 200) String content) {

        setupMocks();

        // Create a preference with inAppEnabled = false
        NotificationPreference preference = NotificationPreference.builder()
                .id(1L)
                .userId(targetUserId)
                .notificationType(notificationType)
                .inAppEnabled(false)
                .build();

        when(notificationPreferenceRepository.findByUserIdAndNotificationType(targetUserId, notificationType))
                .thenReturn(Optional.of(preference));

        // Build the event
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .targetUserId(targetUserId)
                .notificationType(notificationType)
                .title(title)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        // Execute
        Notification result = service.createFromEvent(event);

        // Should return null (notification not created)
        assertThat(result)
                .as("createFromEvent should return null when inAppEnabled is false")
                .isNull();

        // Verify that save was never called
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Provide
    Arbitrary<NotificationType> notificationTypes() {
        return Arbitraries.of(NotificationType.values());
    }

    @Provide
    Arbitrary<String> notificationTypeStrings() {
        return Arbitraries.of("TASK", "PROCESS", "SYSTEM", "REMINDER");
    }
}
