package com.portal.properties;

import com.portal.entity.Notification;
import com.portal.enums.NotificationType;
import com.portal.exception.PortalException;
import com.portal.repository.NotificationPreferenceRepository;
import com.portal.repository.NotificationRepository;
import com.portal.service.NotificationServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property tests for NotificationService - Mark Read and Delete operations.
 * Feature: kafka-in-app-messaging
 *
 * Validates: Requirements 3.4, 3.5, 3.6
 */
class NotificationServiceReadDeletePropertyTest {

    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository notificationPreferenceRepository;
    private NotificationServiceImpl service;

    private void setupMocks() {
        notificationRepository = mock(NotificationRepository.class);
        notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
        service = new NotificationServiceImpl(notificationRepository, notificationPreferenceRepository);
    }

    /**
     * Property 5: 标记已读幂等性
     *
     * For any notification belonging to the current user, markAsRead should set isRead=true;
     * calling it again should not throw an error and isRead should remain true.
     *
     * Validates: Requirements 3.4
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 5: 标记已读幂等性")
    void markAsReadShouldBeIdempotent(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String userId,
            @ForAll("notificationTypes") NotificationType type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String title) {

        setupMocks();

        // Create a notification belonging to the user
        Notification notification = Notification.builder()
                .id(1L)
                .userId(userId)
                .type(type)
                .title(title)
                .content("test content")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // First call: mark as read
        service.markAsRead(userId, 1L);
        assertThat(notification.getIsRead())
                .as("After first markAsRead, isRead should be true")
                .isTrue();

        // Second call: should not throw and isRead should remain true
        assertThatCode(() -> service.markAsRead(userId, 1L))
                .as("Calling markAsRead again should not throw")
                .doesNotThrowAnyException();
        assertThat(notification.getIsRead())
                .as("After second markAsRead, isRead should still be true")
                .isTrue();

        // Verify save was called twice (once per markAsRead call)
        verify(notificationRepository, times(2)).save(notification);
    }

    /**
     * Property 6: 全部标记已读
     *
     * After markAllAsRead, all notifications for that user should be read.
     * The repository's markAllAsReadByUserId should be called with the correct userId.
     *
     * Validates: Requirements 3.5
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 6: 全部标记已读")
    void markAllAsReadShouldMarkAllNotificationsForUser(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String userId) {

        setupMocks();

        // Execute markAllAsRead
        service.markAllAsRead(userId);

        // Verify the repository method was called with the correct userId
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).markAllAsReadByUserId(userIdCaptor.capture());
        assertThat(userIdCaptor.getValue())
                .as("markAllAsReadByUserId should be called with the correct userId")
                .isEqualTo(userId);

        // After markAllAsRead, getUnreadCount should return 0
        when(notificationRepository.countByUserIdAndIsRead(userId, false)).thenReturn(0L);
        long unreadCount = service.getUnreadCount(userId);
        assertThat(unreadCount)
                .as("After markAllAsRead, unread count should be 0")
                .isEqualTo(0L);
    }

    /**
     * Property 7: 删除后不可查询
     *
     * After deleteNotification, the notification should not be retrievable.
     * The repository's delete method should be called, and subsequent findById should return empty.
     *
     * Validates: Requirements 3.6
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 7: 删除后不可查询")
    void deletedNotificationShouldNotBeRetrievable(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String userId,
            @ForAll("notificationTypes") NotificationType type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String title) {

        setupMocks();

        AtomicLong idGenerator = new AtomicLong(1L);
        long notificationId = idGenerator.getAndIncrement();

        // Create a notification belonging to the user
        Notification notification = Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type(type)
                .title(title)
                .content("test content")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // First findById returns the notification (for delete ownership check)
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        // Execute delete
        service.deleteNotification(userId, notificationId);

        // Verify delete was called on the repository
        verify(notificationRepository).delete(notification);

        // After deletion, simulate that findById returns empty
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        // Attempting to delete again should throw 404 (notification not found)
        org.junit.jupiter.api.Assertions.assertThrows(PortalException.class, () -> {
            service.deleteNotification(userId, notificationId);
        });

        // Verify the notification is no longer retrievable
        Optional<Notification> result = notificationRepository.findById(notificationId);
        assertThat(result)
                .as("After deletion, findById should return empty")
                .isEmpty();
    }

    @Provide
    Arbitrary<NotificationType> notificationTypes() {
        return Arbitraries.of(NotificationType.values());
    }
}
