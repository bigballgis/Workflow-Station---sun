package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.response.NotificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Notification Manager Component
 *
 * Handles real-time event pushing and WebSocket notifications.
 * Supports publishing and subscribing to process lifecycle events.
 * Integrates Kafka message queue for event-driven architecture.
 * Supports email, in-app messages, WebSocket push and other notification channels.
 *
 * <p>This class is a thin facade: every public method signature and public nested type is preserved
 * verbatim, and the bodies delegate to dedicated same-package collaborator components
 * ({@link NotificationEventCoordinator}, {@link NotificationKafkaDispatcher},
 * {@link NotificationChannelDispatcher}, {@link NotificationTemplateManager},
 * {@link NotificationPreferenceManager}). All mutable in-memory state and cross-cutting helpers live
 * in a single {@link NotificationContext} owned by this facade, so behaviour is identical whether the
 * collaborators are Spring-injected or created on demand (plain {@code new} unit tests).</p>
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
public class NotificationManagerComponent {

    /**
     * Shared state + cross-cutting helpers, owned by this facade.
     */
    private final NotificationContext context;

    // Collaborators are injected lazily to avoid potential circular-dependency issues; when this
    // component is constructed directly (unit tests without Spring) they are null, in which case the
    // lazy accessors below create stateless instances on demand. State is never duplicated because
    // every collaborator operates on the single shared NotificationContext.
    @Lazy
    @Autowired(required = false)
    private NotificationEventCoordinator eventCoordinator;

    @Lazy
    @Autowired(required = false)
    private NotificationKafkaDispatcher kafkaDispatcher;

    @Lazy
    @Autowired(required = false)
    private NotificationChannelDispatcher channelDispatcher;

    @Lazy
    @Autowired(required = false)
    private NotificationTemplateManager templateManager;

    @Lazy
    @Autowired(required = false)
    private NotificationPreferenceManager preferenceManager;

    public NotificationManagerComponent(ApplicationEventPublisher eventPublisher,
                                        StringRedisTemplate stringRedisTemplate,
                                        ObjectMapper objectMapper) {
        this.context = new NotificationContext(eventPublisher, stringRedisTemplate, objectMapper);
    }

    // ==================== Lazy collaborator accessors ====================

    private NotificationEventCoordinator eventCoordinator() {
        if (eventCoordinator == null) {
            eventCoordinator = new NotificationEventCoordinator();
        }
        return eventCoordinator;
    }

    private NotificationKafkaDispatcher kafkaDispatcher() {
        if (kafkaDispatcher == null) {
            kafkaDispatcher = new NotificationKafkaDispatcher();
        }
        return kafkaDispatcher;
    }

    private NotificationChannelDispatcher channelDispatcher() {
        if (channelDispatcher == null) {
            channelDispatcher = new NotificationChannelDispatcher();
        }
        return channelDispatcher;
    }

    private NotificationTemplateManager templateManager() {
        if (templateManager == null) {
            templateManager = new NotificationTemplateManager();
        }
        return templateManager;
    }

    private NotificationPreferenceManager preferenceManager() {
        if (preferenceManager == null) {
            preferenceManager = new NotificationPreferenceManager();
        }
        return preferenceManager;
    }

    /**
     * Simplified WebSocket session class
     */
    public static class WebSocketSession {
        private final String sessionId;
        private final String userId;
        private final LocalDateTime connectedTime;
        private boolean active;

        public WebSocketSession(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.connectedTime = LocalDateTime.now();
            this.active = true;
        }

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public LocalDateTime getConnectedTime() { return connectedTime; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public void sendMessage(String message) {
            if (active) {
                // Simulated WebSocket message sending
                log.info("Sending WebSocket message to session {}: {}", sessionId, message);
            }
        }
    }

    /**
     * Event subscription class
     */
    public static class EventSubscription {
        private final String subscriptionId;
        private final String eventType;
        private final String userId;
        private final Map<String, Object> filters;
        private final LocalDateTime createdTime;

        public EventSubscription(String subscriptionId, String eventType, String userId, Map<String, Object> filters) {
            this.subscriptionId = subscriptionId;
            this.eventType = eventType;
            this.userId = userId;
            this.filters = filters != null ? filters : new HashMap<>();
            this.createdTime = LocalDateTime.now();
        }

        // Getters
        public String getSubscriptionId() { return subscriptionId; }
        public String getEventType() { return eventType; }
        public String getUserId() { return userId; }
        public Map<String, Object> getFilters() { return filters; }
        public LocalDateTime getCreatedTime() { return createdTime; }

        public boolean matchesEvent(WorkflowEvent event) {
            if (!eventType.equals(event.getEventType())) {
                return false;
            }

            // Check filter conditions
            for (Map.Entry<String, Object> filter : filters.entrySet()) {
                Object eventValue = event.getEventData().get(filter.getKey());
                if (!Objects.equals(eventValue, filter.getValue())) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Workflow event class
     */
    public static class WorkflowEvent {
        private final String eventId;
        private final String eventType;
        private final String sourceId;
        private final String sourceType;
        private final Map<String, Object> eventData;
        private final LocalDateTime timestamp;

        public WorkflowEvent(String eventType, String sourceId, String sourceType, Map<String, Object> eventData) {
            this.eventId = UUID.randomUUID().toString();
            this.eventType = eventType;
            this.sourceId = sourceId;
            this.sourceType = sourceType;
            this.eventData = eventData != null ? eventData : new HashMap<>();
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getEventId() { return eventId; }
        public String getEventType() { return eventType; }
        public String getSourceId() { return sourceId; }
        public String getSourceType() { return sourceType; }
        public Map<String, Object> getEventData() { return eventData; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * Notification record class
     */
    public static class NotificationRecord {
        private final String notificationId;
        private final String eventId;
        private final String userId;
        private final String notificationType;
        private final String message;
        private final LocalDateTime sentTime;
        private boolean delivered;

        public NotificationRecord(String eventId, String userId, String notificationType, String message) {
            this.notificationId = UUID.randomUUID().toString();
            this.eventId = eventId;
            this.userId = userId;
            this.notificationType = notificationType;
            this.message = message;
            this.sentTime = LocalDateTime.now();
            this.delivered = false;
        }

        // Getters and setters
        public String getNotificationId() { return notificationId; }
        public String getEventId() { return eventId; }
        public String getUserId() { return userId; }
        public String getNotificationType() { return notificationType; }
        public String getMessage() { return message; }
        public LocalDateTime getSentTime() { return sentTime; }
        public boolean isDelivered() { return delivered; }
        public void setDelivered(boolean delivered) { this.delivered = delivered; }
    }

    /**
     * Notification template class
     */
    public static class NotificationTemplate {
        private String templateId;
        private String templateName;
        private String eventType;
        private String subject;
        private String bodyTemplate;
        private Map<String, String> localizedSubjects; // Localized subjects
        private Map<String, String> localizedBodies; // Localized bodies
        private Set<String> channels; // EMAIL, SMS, WEBSOCKET, IN_APP
        private boolean enabled;
        private LocalDateTime createdTime;

        // Getters and Setters
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getBodyTemplate() { return bodyTemplate; }
        public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }
        public Map<String, String> getLocalizedSubjects() { return localizedSubjects; }
        public void setLocalizedSubjects(Map<String, String> localizedSubjects) { this.localizedSubjects = localizedSubjects; }
        public Map<String, String> getLocalizedBodies() { return localizedBodies; }
        public void setLocalizedBodies(Map<String, String> localizedBodies) { this.localizedBodies = localizedBodies; }
        public Set<String> getChannels() { return channels; }
        public void setChannels(Set<String> channels) { this.channels = channels; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    }

    /**
     * User notification preference class
     */
    public static class UserNotificationPreference {
        private String userId;
        private Set<String> enabledChannels;
        private Set<String> subscribedEventTypes;
        private String preferredLanguage;
        private boolean doNotDisturb;
        private LocalDateTime doNotDisturbStart;
        private LocalDateTime doNotDisturbEnd;
        private int maxNotificationsPerHour;
        private LocalDateTime updatedTime;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public Set<String> getEnabledChannels() { return enabledChannels; }
        public void setEnabledChannels(Set<String> enabledChannels) { this.enabledChannels = enabledChannels; }
        public Set<String> getSubscribedEventTypes() { return subscribedEventTypes; }
        public void setSubscribedEventTypes(Set<String> subscribedEventTypes) { this.subscribedEventTypes = subscribedEventTypes; }
        public String getPreferredLanguage() { return preferredLanguage; }
        public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
        public boolean isDoNotDisturb() { return doNotDisturb; }
        public void setDoNotDisturb(boolean doNotDisturb) { this.doNotDisturb = doNotDisturb; }
        public LocalDateTime getDoNotDisturbStart() { return doNotDisturbStart; }
        public void setDoNotDisturbStart(LocalDateTime doNotDisturbStart) { this.doNotDisturbStart = doNotDisturbStart; }
        public LocalDateTime getDoNotDisturbEnd() { return doNotDisturbEnd; }
        public void setDoNotDisturbEnd(LocalDateTime doNotDisturbEnd) { this.doNotDisturbEnd = doNotDisturbEnd; }
        public int getMaxNotificationsPerHour() { return maxNotificationsPerHour; }
        public void setMaxNotificationsPerHour(int maxNotificationsPerHour) { this.maxNotificationsPerHour = maxNotificationsPerHour; }
        public LocalDateTime getUpdatedTime() { return updatedTime; }
        public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    }

    /**
     * Kafka message class
     */
    public static class KafkaMessage {
        private String messageId;
        private String topic;
        private String key;
        private WorkflowEvent payload;
        private LocalDateTime timestamp;
        private int partition;
        private long offset;

        public KafkaMessage(String topic, String key, WorkflowEvent payload) {
            this.messageId = UUID.randomUUID().toString();
            this.topic = topic;
            this.key = key;
            this.payload = payload;
            this.timestamp = LocalDateTime.now();
        }

        // Getters and Setters
        public String getMessageId() { return messageId; }
        public String getTopic() { return topic; }
        public String getKey() { return key; }
        public WorkflowEvent getPayload() { return payload; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public int getPartition() { return partition; }
        public void setPartition(int partition) { this.partition = partition; }
        public long getOffset() { return offset; }
        public void setOffset(long offset) { this.offset = offset; }
    }

    // ==================== WebSocket sessions / event subscriptions / event publishing ====================

    /**
     * Register a WebSocket session
     *
     * @param sessionId session ID
     * @param userId user ID
     * @return registration result
     */
    @Transactional
    public NotificationResult registerWebSocketSession(String sessionId, String userId) {
        return eventCoordinator().registerWebSocketSession(context, sessionId, userId);
    }

    /**
     * Unregister a WebSocket session
     *
     * @param sessionId session ID
     * @return unregistration result
     */
    @Transactional
    public NotificationResult unregisterWebSocketSession(String sessionId) {
        return eventCoordinator().unregisterWebSocketSession(context, sessionId);
    }

    /**
     * Subscribe to an event
     *
     * @param eventType event type
     * @param userId user ID
     * @param filters filter conditions
     * @return subscription result
     */
    @Transactional
    public NotificationResult subscribeEvent(String eventType, String userId, Map<String, Object> filters) {
        return eventCoordinator().subscribeEvent(context, eventType, userId, filters);
    }

    /**
     * Unsubscribe from an event
     *
     * @param subscriptionId subscription ID
     * @return unsubscription result
     */
    @Transactional
    public NotificationResult unsubscribeEvent(String subscriptionId) {
        return eventCoordinator().unsubscribeEvent(context, subscriptionId);
    }

    /**
     * Publish a process started event
     *
     * @param processInstanceId process instance ID
     * @param processDefinitionKey process definition key
     * @param businessKey business key
     * @param startUserId start user ID
     * @return publish result
     */
    @Transactional
    public NotificationResult publishProcessStartedEvent(String processInstanceId, String processDefinitionKey,
                                                       String businessKey, String startUserId) {
        return eventCoordinator().publishProcessStartedEvent(context, processInstanceId, processDefinitionKey, businessKey, startUserId);
    }

    /**
     * Publish a process completed event
     *
     * @param processInstanceId process instance ID
     * @param processDefinitionKey process definition key
     * @param businessKey business key
     * @param endUserId end user ID
     * @return publish result
     */
    @Transactional
    public NotificationResult publishProcessCompletedEvent(String processInstanceId, String processDefinitionKey,
                                                         String businessKey, String endUserId) {
        return eventCoordinator().publishProcessCompletedEvent(context, processInstanceId, processDefinitionKey, businessKey, endUserId);
    }

    /**
     * Publish a task assigned event
     *
     * @param taskId task ID
     * @param taskName task name
     * @param assignee assignee
     * @param processInstanceId process instance ID
     * @return publish result
     */
    @Transactional
    public NotificationResult publishTaskAssignedEvent(String taskId, String taskName, String assignee, String processInstanceId) {
        return eventCoordinator().publishTaskAssignedEvent(context, taskId, taskName, assignee, processInstanceId);
    }

    /**
     * Publish a task completed event
     *
     * @param taskId task ID
     * @param taskName task name
     * @param assignee completer
     * @param processInstanceId process instance ID
     * @return publish result
     */
    @Transactional
    public NotificationResult publishTaskCompletedEvent(String taskId, String taskName, String assignee, String processInstanceId) {
        return eventCoordinator().publishTaskCompletedEvent(context, taskId, taskName, assignee, processInstanceId);
    }

    /**
     * Publish a task overdue event
     *
     * @param taskId task ID
     * @param taskName task name
     * @param assignee assignee
     * @param processInstanceId process instance ID
     * @param dueDate due date
     * @return publish result
     */
    @Transactional
    public NotificationResult publishTaskOverdueEvent(String taskId, String taskName, String assignee,
                                                    String processInstanceId, LocalDateTime dueDate) {
        return eventCoordinator().publishTaskOverdueEvent(context, taskId, taskName, assignee, processInstanceId, dueDate);
    }

    /**
     * Get active session list
     *
     * @param userId user ID (optional)
     * @return session list
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveSessions(String userId) {
        return eventCoordinator().getActiveSessions(context, userId);
    }

    /**
     * Get event subscription list
     *
     * @param userId user ID (optional)
     * @param eventType event type (optional)
     * @return subscription list
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEventSubscriptions(String userId, String eventType) {
        return eventCoordinator().getEventSubscriptions(context, userId, eventType);
    }

    /**
     * Get notification history
     *
     * @param userId user ID (optional)
     * @param limit result limit
     * @return notification history
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getNotificationHistory(String userId, Integer limit) {
        return eventCoordinator().getNotificationHistory(context, userId, limit);
    }

    // ==================== Kafka message queue integration ====================

    /**
     * Send a Kafka message
     *
     * @param topic topic
     * @param key message key
     * @param event workflow event
     * @return send result
     */
    public NotificationResult sendKafkaMessage(String topic, String key, WorkflowEvent event) {
        return kafkaDispatcher().sendKafkaMessage(context, topic, key, event);
    }

    /**
     * Register a Kafka consumer
     *
     * @param topic topic
     * @param consumer consumer handler function
     * @return registration result
     */
    public NotificationResult registerKafkaConsumer(String topic, Consumer<WorkflowEvent> consumer) {
        return kafkaDispatcher().registerKafkaConsumer(context, topic, consumer);
    }

    // ==================== Multi-channel notifications ====================

    /**
     * Send email notification
     *
     * @param userId user ID
     * @param email email address
     * @param subject email subject
     * @param body email body
     * @return send result
     */
    public NotificationResult sendEmailNotification(String userId, String email, String subject, String body) {
        return channelDispatcher().sendEmailNotification(context, userId, email, subject, body);
    }

    /**
     * Send SMS notification
     *
     * @param userId user ID
     * @param phoneNumber phone number
     * @param message SMS content
     * @return send result
     */
    public NotificationResult sendSmsNotification(String userId, String phoneNumber, String message) {
        return channelDispatcher().sendSmsNotification(context, userId, phoneNumber, message);
    }

    /**
     * Send in-app notification
     *
     * @param userId user ID
     * @param title message title
     * @param content message content
     * @param eventType event type
     * @return send result
     */
    public NotificationResult sendInAppNotification(String userId, String title, String content, String eventType) {
        return channelDispatcher().sendInAppNotification(context, userId, title, content, eventType);
    }

    /**
     * Send multi-channel notification
     *
     * @param userId user ID
     * @param event workflow event
     * @return send result
     */
    public NotificationResult sendMultiChannelNotification(String userId, WorkflowEvent event) {
        return channelDispatcher().sendMultiChannelNotification(context, userId, event);
    }

    // ==================== Notification template management ====================

    /**
     * Define a notification template
     *
     * @param template notification template
     * @return definition result
     */
    public NotificationResult defineNotificationTemplate(NotificationTemplate template) {
        return templateManager().defineNotificationTemplate(context, template);
    }

    /**
     * Render notification template content
     *
     * @param templateId template ID
     * @param variables variables
     * @param language language
     * @return rendered content
     */
    public Map<String, String> renderNotificationTemplate(String templateId, Map<String, Object> variables, String language) {
        return templateManager().renderNotificationTemplate(context, templateId, variables, language);
    }

    // ==================== User notification preference management ====================

    /**
     * Set user notification preference
     *
     * @param preference user notification preference
     * @return set result
     */
    public NotificationResult setUserNotificationPreference(UserNotificationPreference preference) {
        return preferenceManager().setUserNotificationPreference(context, preference);
    }

    /**
     * Get unread in-app messages for a user
     *
     * @param userId user ID
     * @param limit result limit
     * @return unread message list
     */
    public List<Map<String, Object>> getUnreadInAppMessages(String userId, int limit) {
        return channelDispatcher().getUnreadInAppMessages(context, userId, limit);
    }

    /**
     * Mark in-app message as read
     *
     * @param userId user ID
     * @param messageId message ID
     * @return mark result
     */
    public NotificationResult markInAppMessageAsRead(String userId, String messageId) {
        return channelDispatcher().markInAppMessageAsRead(context, userId, messageId);
    }

    /**
     * Initialize default notification templates
     */
    public void initializeDefaultTemplates() {
        templateManager().initializeDefaultTemplates(context);
    }
}
