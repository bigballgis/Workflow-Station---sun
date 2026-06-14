package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.component.NotificationManagerComponent.EventSubscription;
import com.workflow.component.NotificationManagerComponent.NotificationRecord;
import com.workflow.component.NotificationManagerComponent.NotificationTemplate;
import com.workflow.component.NotificationManagerComponent.UserNotificationPreference;
import com.workflow.component.NotificationManagerComponent.WebSocketSession;
import com.workflow.component.NotificationManagerComponent.WorkflowEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Shared in-memory state and cross-cutting helpers for the notification subsystem.
 *
 * <p>This holder is created and owned by {@link NotificationManagerComponent} and passed to
 * collaborator components on each call. Keeping the mutable state in a single instance guarantees
 * that all collaborators observe exactly the same sessions, subscriptions, history, consumers,
 * templates and preferences — identical to the behaviour of the original monolithic component, in
 * both Spring-managed and plain {@code new} (unit-test) scenarios.</p>
 *
 * <p>Package-private by design: it is an internal collaboration detail, not part of the public API.</p>
 */
@Slf4j
class NotificationContext {

    // Cache key prefix
    static final String NOTIFICATION_PREFIX = "notification:";
    static final String KAFKA_TOPIC_PREFIX = "workflow:";

    final ApplicationEventPublisher eventPublisher;
    final StringRedisTemplate stringRedisTemplate;
    final ObjectMapper objectMapper;

    // WebSocket session management
    final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // Event subscription management
    final Map<String, List<EventSubscription>> eventSubscriptions = new ConcurrentHashMap<>();

    // Notification history
    final List<NotificationRecord> notificationHistory = new CopyOnWriteArrayList<>();

    // Kafka message handlers (simulated)
    final Map<String, List<Consumer<WorkflowEvent>>> kafkaConsumers = new ConcurrentHashMap<>();

    // Notification templates
    final Map<String, NotificationTemplate> notificationTemplates = new ConcurrentHashMap<>();

    // User notification preferences
    final Map<String, UserNotificationPreference> userPreferences = new ConcurrentHashMap<>();

    NotificationContext(ApplicationEventPublisher eventPublisher,
                        StringRedisTemplate stringRedisTemplate,
                        ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Send WebSocket notification
     */
    boolean sendWebSocketNotification(String userId, String message) {
        boolean delivered = false;

        for (WebSocketSession session : activeSessions.values()) {
            if (session.isActive() && userId.equals(session.getUserId())) {
                session.sendMessage(message);
                delivered = true;
            }
        }

        return delivered;
    }

    /**
     * Build notification message
     */
    String buildNotificationMessage(WorkflowEvent event) {
        Map<String, Object> data = event.getEventData();

        switch (event.getEventType()) {
            case "PROCESS_STARTED":
                return String.format("Process started: %s (business key: %s)",
                        data.get("processDefinitionKey"), data.get("businessKey"));

            case "PROCESS_COMPLETED":
                return String.format("Process completed: %s (business key: %s)",
                        data.get("processDefinitionKey"), data.get("businessKey"));

            case "TASK_ASSIGNED":
                return String.format("Task assigned: %s (assigned to: %s)",
                        data.get("taskName"), data.get("assignee"));

            case "TASK_COMPLETED":
                return String.format("Task completed: %s (completed by: %s)",
                        data.get("taskName"), data.get("assignee"));

            case "TASK_OVERDUE":
                return String.format("Task overdue: %s (assigned to: %s)",
                        data.get("taskName"), data.get("assignee"));

            default:
                return String.format("Workflow event: %s", event.getEventType());
        }
    }

    /**
     * Get notification template for an event
     */
    NotificationTemplate getTemplateForEvent(String eventType) {
        return notificationTemplates.values().stream()
                .filter(t -> t.isEnabled() && eventType.equals(t.getEventType()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get user notification preference
     */
    UserNotificationPreference getUserPreference(String userId) {
        return userPreferences.get(userId);
    }

    /**
     * Check if user has enabled a specified channel
     */
    boolean isChannelEnabled(String userId, String channel) {
        UserNotificationPreference preference = getUserPreference(userId);
        if (preference == null) {
            return true; // All channels enabled by default
        }

        // Check do-not-disturb mode
        if (preference.isDoNotDisturb()) {
            LocalDateTime now = LocalDateTime.now();
            if (preference.getDoNotDisturbStart() != null && preference.getDoNotDisturbEnd() != null) {
                if (now.isAfter(preference.getDoNotDisturbStart()) && now.isBefore(preference.getDoNotDisturbEnd())) {
                    return false;
                }
            }
        }

        return preference.getEnabledChannels() == null || preference.getEnabledChannels().contains(channel);
    }
}
