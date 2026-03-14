package com.portal.properties;

import com.platform.messaging.event.NotificationEvent;
import com.portal.entity.Notification;
import com.portal.entity.NotificationPreference;
import com.portal.enums.NotificationType;
import com.portal.repository.NotificationPreferenceRepository;
import com.portal.repository.NotificationRepository;
import com.portal.service.NotificationServiceImpl;
import com.portal.service.WebSocketNotificationService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property tests for NotificationService.
 * Feature: kafka-in-app-messaging, Property 1: 事件到通知的映射完整性
 *
 * Validates: Requirements 1.1, 2.1, 2.2
 */
class NotificationServicePropertyTest {

    /**
     * Property 1: 事件到通知的映射完整性
     *
     * For any valid NotificationEvent (with targetUserId, notificationType, title, content),
     * when the user's inAppEnabled is true, createFromEvent should produce a Notification
     * with userId = event.targetUserId, type = event.notificationType, title = event.title,
     * content = event.content, link = event.link, and isRead = false.
     *
     * Validates: Requirements 1.1, 2.1, 2.2
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 1: 事件到通知的映射完整性")
    void eventToNotificationMappingShouldBeComplete(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String targetUserId,
            @ForAll("notificationTypes") String notificationType,
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String title,
            @ForAll @AlphaChars @StringLength(min = 1, max = 200) String content,
            @ForAll("optionalLinks") String link) {

        // Setup mocks
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationPreferenceRepository notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
        WebSocketNotificationService webSocketNotificationService = mock(WebSocketNotificationService.class);

        // Create service with mocked dependencies
        NotificationServiceImpl service = new NotificationServiceImpl(
                notificationRepository,
                notificationPreferenceRepository
        );

        // Preference with inAppEnabled = true (no preference found means default enabled)
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Mock save to return the notification with an ID assigned
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // Build the event
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .targetUserId(targetUserId)
                .notificationType(notificationType)
                .title(title)
                .content(content)
                .link(link)
                .timestamp(LocalDateTime.now())
                .build();

        // Execute
        Notification result = service.createFromEvent(event);

        // Verify the mapping completeness
        assertThat(result).isNotNull();

        // Capture what was saved to the repository
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        // Verify all field mappings
        assertThat(saved.getUserId())
                .as("userId should map from event.targetUserId")
                .isEqualTo(targetUserId);
        assertThat(saved.getType())
                .as("type should map from event.notificationType")
                .isEqualTo(NotificationType.valueOf(notificationType));
        assertThat(saved.getTitle())
                .as("title should map from event.title")
                .isEqualTo(title);
        assertThat(saved.getContent())
                .as("content should map from event.content")
                .isEqualTo(content);
        assertThat(saved.getLink())
                .as("link should map from event.link")
                .isEqualTo(link);
        assertThat(saved.getIsRead())
                .as("isRead should default to false")
                .isFalse();
    }

    @Provide
    Arbitrary<String> notificationTypes() {
        return Arbitraries.of("TASK", "PROCESS", "SYSTEM", "REMINDER");
    }

    @Provide
    Arbitrary<String> optionalLinks() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100)
                        .map(s -> "/portal/" + s)
        );
    }
}
