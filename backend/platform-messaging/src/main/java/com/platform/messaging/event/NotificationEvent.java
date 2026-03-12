package com.platform.messaging.event;

import com.platform.messaging.config.KafkaTopics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event for in-app notification delivery.
 * Validates: Requirements 1.6, 7.2
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationEvent extends BaseEvent {

    public static final String TOPIC = KafkaTopics.NOTIFICATION_EVENTS;

    private String targetUserId;
    private String notificationType;  // TASK, PROCESS, SYSTEM, REMINDER
    private String title;
    private String content;
    private String link;

    @Override
    public String getTopic() {
        return TOPIC;
    }
}
