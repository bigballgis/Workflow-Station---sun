package com.workflow.component;

import com.workflow.component.NotificationManagerComponent.EventSubscription;
import com.workflow.component.NotificationManagerComponent.NotificationRecord;
import com.workflow.component.NotificationManagerComponent.WebSocketSession;
import com.workflow.component.NotificationManagerComponent.WorkflowEvent;
import com.workflow.dto.response.NotificationResult;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket session / event subscription / event publishing logic for the notification subsystem.
 *
 * <p>Extracted from {@link NotificationManagerComponent}; behaviour is preserved verbatim. State is
 * read from and written to the shared {@link NotificationContext} supplied on each call, so this
 * component is stateless and safe to share.</p>
 */
@Slf4j
@Component
class NotificationEventCoordinator {

    /**
     * Register a WebSocket session
     */
    NotificationResult registerWebSocketSession(NotificationContext ctx, String sessionId, String userId) {
        log.info("Registering WebSocket session: sessionId={}, userId={}", sessionId, userId);

        try {
            // Validate parameters
            validateSessionParameters(sessionId, userId);

            // Create session
            WebSocketSession session = new WebSocketSession(sessionId, userId);
            ctx.activeSessions.put(sessionId, session);

            // Publish session connected event
            WorkflowEvent event = new WorkflowEvent(
                    "SESSION_CONNECTED",
                    sessionId,
                    "WEBSOCKET_SESSION",
                    Map.of("userId", userId, "connectedTime", session.getConnectedTime())
            );

            publishEvent(ctx, event);

            return NotificationResult.builder()
                    .success(true)
                    .message("WebSocket session registered successfully")
                    .sessionId(sessionId)
                    .build();

        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to register WebSocket session: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("SESSION_REGISTER_FAILED", "Failed to register WebSocket session: " + e.getMessage());
        }
    }

    /**
     * Unregister a WebSocket session
     */
    NotificationResult unregisterWebSocketSession(NotificationContext ctx, String sessionId) {
        log.info("Unregistering WebSocket session: sessionId={}", sessionId);

        try {
            WebSocketSession session = ctx.activeSessions.remove(sessionId);

            if (session != null) {
                session.setActive(false);

                // Publish session disconnected event
                WorkflowEvent event = new WorkflowEvent(
                        "SESSION_DISCONNECTED",
                        sessionId,
                        "WEBSOCKET_SESSION",
                        Map.of("userId", session.getUserId(), "disconnectedTime", LocalDateTime.now())
                );

                publishEvent(ctx, event);
            }

            return NotificationResult.builder()
                    .success(true)
                    .message("WebSocket session unregistered successfully")
                    .sessionId(sessionId)
                    .build();

        } catch (Exception e) {
            log.error("Failed to unregister WebSocket session: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("SESSION_UNREGISTER_FAILED", "Failed to unregister WebSocket session: " + e.getMessage());
        }
    }

    /**
     * Subscribe to an event
     */
    NotificationResult subscribeEvent(NotificationContext ctx, String eventType, String userId, Map<String, Object> filters) {
        log.info("Subscribing to event: eventType={}, userId={}, filters={}", eventType, userId, filters);

        try {
            // Validate parameters
            validateSubscriptionParameters(eventType, userId);

            // Create subscription
            String subscriptionId = UUID.randomUUID().toString();
            EventSubscription subscription = new EventSubscription(subscriptionId, eventType, userId, filters);

            ctx.eventSubscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscription);

            return NotificationResult.builder()
                    .success(true)
                    .message("Event subscribed successfully")
                    .subscriptionId(subscriptionId)
                    .build();

        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to subscribe to event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_SUBSCRIBE_FAILED", "Failed to subscribe to event: " + e.getMessage());
        }
    }

    /**
     * Unsubscribe from an event
     */
    NotificationResult unsubscribeEvent(NotificationContext ctx, String subscriptionId) {
        log.info("Unsubscribing from event: subscriptionId={}", subscriptionId);

        try {
            boolean removed = false;

            for (List<EventSubscription> subscriptions : ctx.eventSubscriptions.values()) {
                removed = subscriptions.removeIf(sub -> subscriptionId.equals(sub.getSubscriptionId()));
                if (removed) {
                    break;
                }
            }

            return NotificationResult.builder()
                    .success(true)
                    .message(removed ? "Unsubscribed successfully" : "Subscription not found")
                    .subscriptionId(subscriptionId)
                    .build();

        } catch (Exception e) {
            log.error("Failed to unsubscribe from event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_UNSUBSCRIBE_FAILED", "Failed to unsubscribe from event: " + e.getMessage());
        }
    }

    /**
     * Publish a process started event
     */
    NotificationResult publishProcessStartedEvent(NotificationContext ctx, String processInstanceId, String processDefinitionKey,
                                                  String businessKey, String startUserId) {
        log.info("Publishing process started event: processInstanceId={}, processDefinitionKey={}", processInstanceId, processDefinitionKey);

        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("processInstanceId", processInstanceId);
            eventData.put("processDefinitionKey", processDefinitionKey);
            eventData.put("businessKey", businessKey);
            eventData.put("startUserId", startUserId);
            eventData.put("startTime", LocalDateTime.now());

            WorkflowEvent event = new WorkflowEvent(
                    "PROCESS_STARTED",
                    processInstanceId,
                    "PROCESS_INSTANCE",
                    eventData
            );

            return publishEvent(ctx, event);

        } catch (Exception e) {
            log.error("Failed to publish process started event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish process started event: " + e.getMessage());
        }
    }

    /**
     * Publish a process completed event
     */
    NotificationResult publishProcessCompletedEvent(NotificationContext ctx, String processInstanceId, String processDefinitionKey,
                                                    String businessKey, String endUserId) {
        log.info("Publishing process completed event: processInstanceId={}, processDefinitionKey={}", processInstanceId, processDefinitionKey);

        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("processInstanceId", processInstanceId);
            eventData.put("processDefinitionKey", processDefinitionKey);
            eventData.put("businessKey", businessKey);
            eventData.put("endUserId", endUserId);
            eventData.put("endTime", LocalDateTime.now());

            WorkflowEvent event = new WorkflowEvent(
                    "PROCESS_COMPLETED",
                    processInstanceId,
                    "PROCESS_INSTANCE",
                    eventData
            );

            return publishEvent(ctx, event);

        } catch (Exception e) {
            log.error("Failed to publish process completed event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish process completed event: " + e.getMessage());
        }
    }

    /**
     * Publish a task assigned event
     */
    NotificationResult publishTaskAssignedEvent(NotificationContext ctx, String taskId, String taskName, String assignee, String processInstanceId) {
        log.info("Publishing task assigned event: taskId={}, assignee={}", taskId, assignee);

        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("taskId", taskId);
            eventData.put("taskName", taskName);
            eventData.put("assignee", assignee);
            eventData.put("processInstanceId", processInstanceId);
            eventData.put("assignTime", LocalDateTime.now());

            WorkflowEvent event = new WorkflowEvent(
                    "TASK_ASSIGNED",
                    taskId,
                    "TASK",
                    eventData
            );

            return publishEvent(ctx, event);

        } catch (Exception e) {
            log.error("Failed to publish task assignment event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish task assignment event: " + e.getMessage());
        }
    }

    /**
     * Publish a task completed event
     */
    NotificationResult publishTaskCompletedEvent(NotificationContext ctx, String taskId, String taskName, String assignee, String processInstanceId) {
        log.info("Publishing task completed event: taskId={}, assignee={}", taskId, assignee);

        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("taskId", taskId);
            eventData.put("taskName", taskName);
            eventData.put("assignee", assignee);
            eventData.put("processInstanceId", processInstanceId);
            eventData.put("completeTime", LocalDateTime.now());

            WorkflowEvent event = new WorkflowEvent(
                    "TASK_COMPLETED",
                    taskId,
                    "TASK",
                    eventData
            );

            return publishEvent(ctx, event);

        } catch (Exception e) {
            log.error("Failed to publish task completion event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish task completion event: " + e.getMessage());
        }
    }

    /**
     * Publish a task overdue event
     */
    NotificationResult publishTaskOverdueEvent(NotificationContext ctx, String taskId, String taskName, String assignee,
                                               String processInstanceId, LocalDateTime dueDate) {
        log.info("Publishing task overdue event: taskId={}, assignee={}", taskId, assignee);

        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("taskId", taskId);
            eventData.put("taskName", taskName);
            eventData.put("assignee", assignee);
            eventData.put("processInstanceId", processInstanceId);
            eventData.put("dueDate", dueDate);
            eventData.put("overdueTime", LocalDateTime.now());

            WorkflowEvent event = new WorkflowEvent(
                    "TASK_OVERDUE",
                    taskId,
                    "TASK",
                    eventData
            );

            return publishEvent(ctx, event);

        } catch (Exception e) {
            log.error("Failed to publish task timeout event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish task timeout event: " + e.getMessage());
        }
    }

    /**
     * Get active session list
     */
    List<Map<String, Object>> getActiveSessions(NotificationContext ctx, String userId) {
        log.info("Getting active sessions: userId={}", userId);

        try {
            List<Map<String, Object>> sessions = new ArrayList<>();

            for (WebSocketSession session : ctx.activeSessions.values()) {
                if (session.isActive() && (userId == null || userId.equals(session.getUserId()))) {
                    Map<String, Object> sessionInfo = new HashMap<>();
                    sessionInfo.put("sessionId", session.getSessionId());
                    sessionInfo.put("userId", session.getUserId());
                    sessionInfo.put("connectedTime", session.getConnectedTime());
                    sessionInfo.put("active", session.isActive());
                    sessions.add(sessionInfo);
                }
            }

            return sessions;

        } catch (Exception e) {
            log.error("Failed to get active sessions: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("GET_SESSIONS_FAILED", "Failed to get active sessions: " + e.getMessage());
        }
    }

    /**
     * Get event subscription list
     */
    List<Map<String, Object>> getEventSubscriptions(NotificationContext ctx, String userId, String eventType) {
        log.info("Getting event subscriptions: userId={}, eventType={}", userId, eventType);

        try {
            List<Map<String, Object>> subscriptions = new ArrayList<>();

            for (Map.Entry<String, List<EventSubscription>> entry : ctx.eventSubscriptions.entrySet()) {
                if (eventType == null || eventType.equals(entry.getKey())) {
                    for (EventSubscription subscription : entry.getValue()) {
                        if (userId == null || userId.equals(subscription.getUserId())) {
                            Map<String, Object> subInfo = new HashMap<>();
                            subInfo.put("subscriptionId", subscription.getSubscriptionId());
                            subInfo.put("eventType", subscription.getEventType());
                            subInfo.put("userId", subscription.getUserId());
                            subInfo.put("filters", subscription.getFilters());
                            subInfo.put("createdTime", subscription.getCreatedTime());
                            subscriptions.add(subInfo);
                        }
                    }
                }
            }

            return subscriptions;

        } catch (Exception e) {
            log.error("Failed to get event subscriptions: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("GET_SUBSCRIPTIONS_FAILED", "Failed to get event subscriptions: " + e.getMessage());
        }
    }

    /**
     * Get notification history
     */
    List<Map<String, Object>> getNotificationHistory(NotificationContext ctx, String userId, Integer limit) {
        log.info("Getting notification history: userId={}, limit={}", userId, limit);

        try {
            List<Map<String, Object>> history = new ArrayList<>();

            int count = 0;
            int maxLimit = limit != null ? limit : 100;

            // Return in reverse chronological order
            for (int i = ctx.notificationHistory.size() - 1; i >= 0 && count < maxLimit; i--) {
                NotificationRecord record = ctx.notificationHistory.get(i);

                if (userId == null || userId.equals(record.getUserId())) {
                    Map<String, Object> recordInfo = new HashMap<>();
                    recordInfo.put("notificationId", record.getNotificationId());
                    recordInfo.put("eventId", record.getEventId());
                    recordInfo.put("userId", record.getUserId());
                    recordInfo.put("notificationType", record.getNotificationType());
                    recordInfo.put("message", record.getMessage());
                    recordInfo.put("sentTime", record.getSentTime());
                    recordInfo.put("delivered", record.isDelivered());
                    history.add(recordInfo);
                    count++;
                }
            }

            return history;

        } catch (Exception e) {
            log.error("Failed to get notification history: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("GET_HISTORY_FAILED", "Failed to get notification history: " + e.getMessage());
        }
    }

    /**
     * Publish an event
     */
    NotificationResult publishEvent(NotificationContext ctx, WorkflowEvent event) {
        log.info("Publishing event: eventType={}, sourceId={}", event.getEventType(), event.getSourceId());

        try {
            // Publish Spring event (if eventPublisher is not null)
            if (ctx.eventPublisher != null) {
                ctx.eventPublisher.publishEvent(event);
            }

            // Process event subscriptions
            List<EventSubscription> subscriptions = ctx.eventSubscriptions.get(event.getEventType());
            if (subscriptions != null) {
                for (EventSubscription subscription : subscriptions) {
                    if (subscription.matchesEvent(event)) {
                        sendNotificationToUser(ctx, event, subscription);
                    }
                }
            }

            return NotificationResult.builder()
                    .success(true)
                    .message("Event published successfully")
                    .eventId(event.getEventId())
                    .build();

        } catch (Exception e) {
            log.error("Failed to publish event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish event: " + e.getMessage());
        }
    }

    /**
     * Send notification to user
     */
    private void sendNotificationToUser(NotificationContext ctx, WorkflowEvent event, EventSubscription subscription) {
        try {
            String message = ctx.buildNotificationMessage(event);

            // Create notification record
            NotificationRecord record = new NotificationRecord(
                    event.getEventId(),
                    subscription.getUserId(),
                    "WEBSOCKET",
                    message
            );

            ctx.notificationHistory.add(record);

            // Send WebSocket notification
            boolean delivered = ctx.sendWebSocketNotification(subscription.getUserId(), message);
            record.setDelivered(delivered);

            log.info("Sent notification to user: userId={}, eventType={}, delivered={}",
                    subscription.getUserId(), event.getEventType(), delivered);

        } catch (Exception e) {
            log.error("Failed to send notification to user: {}", e.getMessage(), e);
        }
    }

    /**
     * Validate session parameters
     */
    private void validateSessionParameters(String sessionId, String userId) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();

        if (!StringUtils.hasText(sessionId)) {
            errors.add(new WorkflowValidationException.ValidationError("sessionId", "Session ID must not be empty", sessionId));
        }

        if (!StringUtils.hasText(userId)) {
            errors.add(new WorkflowValidationException.ValidationError("userId", "User ID must not be empty", userId));
        }

        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * Validate subscription parameters
     */
    private void validateSubscriptionParameters(String eventType, String userId) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();

        if (!StringUtils.hasText(eventType)) {
            errors.add(new WorkflowValidationException.ValidationError("eventType", "Event type must not be empty", eventType));
        }

        if (!StringUtils.hasText(userId)) {
            errors.add(new WorkflowValidationException.ValidationError("userId", "User ID must not be empty", userId));
        }

        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }
}
