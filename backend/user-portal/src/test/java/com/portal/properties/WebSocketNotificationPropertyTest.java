package com.portal.properties;

import com.platform.messaging.event.NotificationEvent;
import com.portal.dto.NotificationDto;
import com.portal.entity.Notification;
import com.portal.entity.NotificationPreference;
import com.portal.enums.NotificationType;
import com.portal.repository.NotificationPreferenceRepository;
import com.portal.repository.NotificationRepository;
import com.portal.service.NotificationServiceImpl;
import com.portal.service.WebSocketNotificationService;
import com.portal.service.WebSocketNotificationServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property tests for WebSocket notification push.
 * Feature: kafka-in-app-messaging
 *
 * Validates: Requirements 4.3, 8.3
 */
class WebSocketNotificationPropertyTest {

    /**
     * Property 10: WebSocket 推送目标正确性
     *
     * For any newly created Notification (outside quiet hours), the WebSocket push target
     * userId equals Notification.userId, and the message contains id, type, title.
     *
     * Validates: Requirements 4.3
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 10: WebSocket 推送目标正确性")
    void webSocketPushShouldTargetCorrectUser(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String targetUserId,
            @ForAll("notificationTypes") String notificationType,
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String title,
            @ForAll @AlphaChars @StringLength(min = 1, max = 200) String content,
            @ForAll("optionalLinks") String link) {

        // Setup mock SimpMessagingTemplate
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

        // Create the WebSocket service with mocked template
        WebSocketNotificationServiceImpl wsService = new WebSocketNotificationServiceImpl(messagingTemplate);

        // Build a notification
        Notification notification = Notification.builder()
                .id(1L)
                .userId(targetUserId)
                .type(NotificationType.valueOf(notificationType))
                .title(title)
                .content(content)
                .link(link)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Execute push
        wsService.pushNotification(notification);

        // Capture the arguments passed to convertAndSendToUser
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationDto> dtoCaptor = ArgumentCaptor.forClass(NotificationDto.class);

        verify(messagingTemplate).convertAndSendToUser(
                userIdCaptor.capture(),
                destinationCaptor.capture(),
                dtoCaptor.capture()
        );

        // Verify push targets the correct userId
        assertThat(userIdCaptor.getValue())
                .as("WebSocket push should target the notification's userId")
                .isEqualTo(targetUserId);

        // Verify destination
        assertThat(destinationCaptor.getValue())
                .as("WebSocket push destination should be /queue/notifications")
                .isEqualTo("/queue/notifications");

        // Verify the DTO contains id, type, title
        NotificationDto pushedDto = dtoCaptor.getValue();
        assertThat(pushedDto.getId())
                .as("Pushed DTO should contain notification id")
                .isEqualTo(notification.getId());
        assertThat(pushedDto.getType())
                .as("Pushed DTO should contain notification type")
                .isEqualTo(notificationType);
        assertThat(pushedDto.getTitle())
                .as("Pushed DTO should contain notification title")
                .isEqualTo(title);
    }

    /**
     * Property 11: 免打扰时段抑制推送
     *
     * For any NotificationEvent, if the current time is within the user's quiet hours,
     * the notification should be persisted but NOT pushed via WebSocket.
     *
     * Validates: Requirements 8.3
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 11: 免打扰时段抑制推送")
    void quietHoursShouldSuppressWebSocketPush(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String targetUserId,
            @ForAll("notificationTypes") String notificationType,
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String title,
            @ForAll @AlphaChars @StringLength(min = 1, max = 200) String content) {

        // Setup mocks
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationPreferenceRepository notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
        WebSocketNotificationService webSocketNotificationService = mock(WebSocketNotificationService.class);

        NotificationServiceImpl service = new NotificationServiceImpl(
                notificationRepository,
                notificationPreferenceRepository
        );

        // Inject the WebSocket service via reflection (it's @Autowired(required=false))
        injectWebSocketService(service, webSocketNotificationService);

        // Create quiet hours that cover the current time
        // Use a wide window around now to ensure we're always in quiet hours
        LocalTime now = LocalTime.now();
        LocalTime quietStart = now.minusHours(1);
        LocalTime quietEnd = now.plusHours(1);

        // Create preference with inAppEnabled=true but with quiet hours covering now
        NotificationPreference preference = NotificationPreference.builder()
                .id(1L)
                .userId(targetUserId)
                .notificationType(notificationType)
                .inAppEnabled(true)
                .quietStartTime(quietStart)
                .quietEndTime(quietEnd)
                .build();

        when(notificationPreferenceRepository.findByUserIdAndNotificationType(targetUserId, notificationType))
                .thenReturn(Optional.of(preference));

        // Mock save to return the notification with an ID
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId(1L);
            saved.setCreatedAt(LocalDateTime.now());
            saved.setUpdatedAt(LocalDateTime.now());
            return saved;
        });

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

        // Notification should be persisted
        assertThat(result)
                .as("Notification should be persisted even during quiet hours")
                .isNotNull();
        verify(notificationRepository).save(any(Notification.class));

        // WebSocket push should NOT be called during quiet hours
        verify(webSocketNotificationService, never()).pushNotification(any(Notification.class));
    }

    /**
     * Injects the WebSocketNotificationService into NotificationServiceImpl
     * via reflection since it uses @Autowired(required = false).
     */
    private void injectWebSocketService(NotificationServiceImpl service, WebSocketNotificationService wsService) {
        try {
            Field field = NotificationServiceImpl.class.getDeclaredField("webSocketNotificationService");
            field.setAccessible(true);
            field.set(service, wsService);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject WebSocketNotificationService", e);
        }
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
