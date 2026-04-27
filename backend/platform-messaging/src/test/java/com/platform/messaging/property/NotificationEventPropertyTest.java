package com.platform.messaging.property;

import com.platform.messaging.event.NotificationEvent;
import com.platform.messaging.config.KafkaTopics;
import com.platform.messaging.service.impl.KafkaEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property tests for NotificationEvent.
 * Feature: kafka-in-app-messaging, Property 12: Kafka Key is targetUserId
 *
 * Validates: Requirements 7.5
 */
class NotificationEventPropertyTest {

    /**
     * Property 12: Kafka Key is targetUserId
     *
     * For any NotificationEvent with a targetUserId, the KafkaEventPublisher
     * should use targetUserId as the Kafka message key, ensuring ordered
     * delivery per user.
     *
     * Validates: Requirements 7.5
     */
    @Property(tries = 100)
    @Label("Feature: kafka-in-app-messaging, Property 12: Kafka Key is targetUserId")
    void kafkaKeyShouldBeTargetUserId(
            @ForAll @AlphaChars @Size(min = 1, max = 50) String targetUserId,
            @ForAll("notificationTypes") String notificationType,
            @ForAll @AlphaChars @Size(min = 1, max = 100) String title,
            @ForAll @AlphaChars @Size(min = 1, max = 200) String content) {

        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .targetUserId(targetUserId)
                .notificationType(notificationType)
                .title(title)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        // Use reflection to invoke the private getEventKey method
        // by creating a KafkaEventPublisher with mocked dependencies
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> mockTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        KafkaEventPublisher publisher = new KafkaEventPublisher(mockTemplate, objectMapper);

        // We verify the key by calling publish and capturing the key via the template,
        // but since getEventKey is private, we test the observable behavior:
        // The event's targetUserId should match what getEventKey returns.
        // We use reflection to access the private method.
        try {
            var method = KafkaEventPublisher.class.getDeclaredMethod("getEventKey",
                    com.platform.messaging.event.BaseEvent.class);
            method.setAccessible(true);
            String key = (String) method.invoke(publisher, event);

            assertThat(key)
                    .as("Kafka key should be the targetUserId for NotificationEvent")
                    .isEqualTo(targetUserId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke getEventKey", e);
        }
    }

    @Provide
    Arbitrary<String> notificationTypes() {
        return Arbitraries.of("TASK", "PROCESS", "SYSTEM", "REMINDER");
    }
}
