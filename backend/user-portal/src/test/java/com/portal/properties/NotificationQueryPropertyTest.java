package com.portal.properties;

import com.portal.dto.NotificationDto;
import com.portal.dto.PageResponse;
import com.portal.entity.Notification;
import com.portal.enums.NotificationType;
import com.portal.repository.NotificationPreferenceRepository;
import com.portal.repository.NotificationRepository;
import com.portal.service.NotificationServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property tests for notification query operations.
 * Feature: kafka-in-app-messaging
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 5.2
 */
class NotificationQueryPropertyTest {

    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository notificationPreferenceRepository;
    private NotificationServiceImpl service;

    private void setupMocks() {
        notificationRepository = mock(NotificationRepository.class);
        notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
        service = new NotificationServiceImpl(notificationRepository, notificationPreferenceRepository);
    }

    /**
     * Property 2: 通知查询过滤正确性
     *
     * When querying with a type filter, all returned notifications should have that type.
     * The service delegates filtering to the repository, so we mock the repository to return
     * a mixed set filtered by type, and verify the service correctly passes the filter through.
     *
     * Validates: Requirements 3.1, 5.2
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 2: 通知查询过滤正确性")
    void queryWithTypeFilterShouldReturnOnlyMatchingType(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String userId,
            @ForAll("notificationTypes") NotificationType filterType,
            @ForAll @IntRange(min = 1, max = 20) int notificationCount) {

        setupMocks();

        // Generate notifications that all match the filter type (simulating repository filtering)
        AtomicLong idGen = new AtomicLong(1L);
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 12, 0);
        List<Notification> matchingNotifications = new ArrayList<>();
        for (int i = 0; i < notificationCount; i++) {
            matchingNotifications.add(Notification.builder()
                    .id(idGen.getAndIncrement())
                    .userId(userId)
                    .type(filterType)
                    .title("Title " + i)
                    .content("Content " + i)
                    .isRead(i % 2 == 0)
                    .createdAt(baseTime.minusMinutes(i))
                    .updatedAt(baseTime.minusMinutes(i))
                    .build());
        }

        // Mock repository to return only matching notifications when type filter is applied
        Page<Notification> page = new PageImpl<>(matchingNotifications, PageRequest.of(0, 20), matchingNotifications.size());
        when(notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                eq(userId), eq(filterType), any(Pageable.class)))
                .thenReturn(page);

        // Execute query with type filter
        PageResponse<NotificationDto> result = service.getNotifications(userId, 0, 20, filterType.name(), null);

        // Verify all returned notifications have the correct type
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent())
                .as("All returned notifications should have type = %s", filterType.name())
                .allMatch(dto -> dto.getType().equals(filterType.name()));
    }

    /**
     * Property 3: 查询结果按时间降序
     *
     * Query results should be ordered by createdAt descending.
     * For any set of notifications returned, each notification's createdAt should be
     * >= the next notification's createdAt.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 3: 查询结果按时间降序")
    void queryResultsShouldBeOrderedByCreatedAtDescending(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String userId,
            @ForAll @IntRange(min = 2, max = 30) int notificationCount) {

        setupMocks();

        // Generate notifications with various timestamps, sorted descending (as repository would return)
        AtomicLong idGen = new AtomicLong(1L);
        LocalDateTime baseTime = LocalDateTime.of(2024, 6, 15, 10, 0);
        NotificationType[] types = NotificationType.values();
        List<Notification> notifications = new ArrayList<>();
        for (int i = 0; i < notificationCount; i++) {
            notifications.add(Notification.builder()
                    .id(idGen.getAndIncrement())
                    .userId(userId)
                    .type(types[i % types.length])
                    .title("Title " + i)
                    .content("Content " + i)
                    .isRead(false)
                    .createdAt(baseTime.minusMinutes(i * 5L))
                    .updatedAt(baseTime.minusMinutes(i * 5L))
                    .build());
        }

        // Sort descending by createdAt (simulating repository ORDER BY createdAt DESC)
        notifications.sort(Comparator.comparing(Notification::getCreatedAt).reversed());

        Page<Notification> page = new PageImpl<>(notifications, PageRequest.of(0, 50), notifications.size());
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        // Execute query
        PageResponse<NotificationDto> result = service.getNotifications(userId, 0, 50, null, null);

        // Verify ordering: each createdAt >= next createdAt
        List<NotificationDto> content = result.getContent();
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        for (int i = 0; i < content.size() - 1; i++) {
            assertThat(content.get(i).getCreatedAt())
                    .as("Notification at index %d should have createdAt >= notification at index %d", i, i + 1)
                    .isAfterOrEqualTo(content.get(i + 1).getCreatedAt());
        }
    }

    /**
     * Property 4: 未读数量准确性
     *
     * getUnreadCount should return the exact count of unread notifications for a user.
     * We mock the repository to return a specific count and verify the service returns it exactly.
     *
     * Validates: Requirements 3.3
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 4: 未读数量准确性")
    void getUnreadCountShouldReturnExactUnreadCount(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String userId,
            @ForAll @IntRange(min = 0, max = 10000) int expectedUnreadCount) {

        setupMocks();

        // Mock repository to return the expected unread count
        when(notificationRepository.countByUserIdAndIsRead(userId, false))
                .thenReturn((long) expectedUnreadCount);

        // Execute
        long actualCount = service.getUnreadCount(userId);

        // Verify exact match
        assertThat(actualCount)
                .as("getUnreadCount should return exactly %d for user %s", expectedUnreadCount, userId)
                .isEqualTo(expectedUnreadCount);
    }

    @Provide
    Arbitrary<NotificationType> notificationTypes() {
        return Arbitraries.of(NotificationType.values());
    }
}
