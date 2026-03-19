package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.response.NotificationResult;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Notification Manager Component
 * 
 * Handles real-time event pushing and WebSocket notifications.
 * Supports publishing and subscribing to process lifecycle events.
 * Integrates Kafka message queue for event-driven architecture.
 * Supports email, in-app messages, WebSocket push and other notification channels.
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationManagerComponent {

    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    
    // WebSocket session management
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    
    // Event subscription management
    private final Map<String, List<EventSubscription>> eventSubscriptions = new ConcurrentHashMap<>();
    
    // Notification history
    private final List<NotificationRecord> notificationHistory = new CopyOnWriteArrayList<>();
    
    // Kafka message handlers (simulated)
    private final Map<String, List<Consumer<WorkflowEvent>>> kafkaConsumers = new ConcurrentHashMap<>();
    
    // Notification templates
    private final Map<String, NotificationTemplate> notificationTemplates = new ConcurrentHashMap<>();
    
    // User notification preferences
    private final Map<String, UserNotificationPreference> userPreferences = new ConcurrentHashMap<>();
    
    // Cache key prefix
    private static final String NOTIFICATION_PREFIX = "notification:";
    private static final String KAFKA_TOPIC_PREFIX = "workflow:";

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

    /**
     * Register a WebSocket session
     * 
     * @param sessionId session ID
     * @param userId user ID
     * @return registration result
     */
    @Transactional
    public NotificationResult registerWebSocketSession(String sessionId, String userId) {
        log.info("Registering WebSocket session: sessionId={}, userId={}", sessionId, userId);
        
        try {
            // Validate parameters
            validateSessionParameters(sessionId, userId);
            
            // Create session
            WebSocketSession session = new WebSocketSession(sessionId, userId);
            activeSessions.put(sessionId, session);
            
            // Publish session connected event
            WorkflowEvent event = new WorkflowEvent(
                    "SESSION_CONNECTED",
                    sessionId,
                    "WEBSOCKET_SESSION",
                    Map.of("userId", userId, "connectedTime", session.getConnectedTime())
            );
            
            publishEvent(event);
            
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
     * 
     * @param sessionId session ID
     * @return unregistration result
     */
    @Transactional
    public NotificationResult unregisterWebSocketSession(String sessionId) {
        log.info("Unregistering WebSocket session: sessionId={}", sessionId);
        
        try {
            WebSocketSession session = activeSessions.remove(sessionId);
            
            if (session != null) {
                session.setActive(false);
                
                // Publish session disconnected event
                WorkflowEvent event = new WorkflowEvent(
                        "SESSION_DISCONNECTED",
                        sessionId,
                        "WEBSOCKET_SESSION",
                        Map.of("userId", session.getUserId(), "disconnectedTime", LocalDateTime.now())
                );
                
                publishEvent(event);
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
     * 
     * @param eventType event type
     * @param userId user ID
     * @param filters filter conditions
     * @return subscription result
     */
    @Transactional
    public NotificationResult subscribeEvent(String eventType, String userId, Map<String, Object> filters) {
        log.info("Subscribing to event: eventType={}, userId={}, filters={}", eventType, userId, filters);
        
        try {
            // Validate parameters
            validateSubscriptionParameters(eventType, userId);
            
            // Create subscription
            String subscriptionId = UUID.randomUUID().toString();
            EventSubscription subscription = new EventSubscription(subscriptionId, eventType, userId, filters);
            
            eventSubscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscription);
            
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
     * 
     * @param subscriptionId subscription ID
     * @return unsubscription result
     */
    @Transactional
    public NotificationResult unsubscribeEvent(String subscriptionId) {
        log.info("Unsubscribing from event: subscriptionId={}", subscriptionId);
        
        try {
            boolean removed = false;
            
            for (List<EventSubscription> subscriptions : eventSubscriptions.values()) {
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
            
            return publishEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to publish process started event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish process started event: " + e.getMessage());
        }
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
            
            return publishEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to publish process completed event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish process completed event: " + e.getMessage());
        }
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
            
            return publishEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to publish task assignment event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish task assignment event: " + e.getMessage());
        }
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
            
            return publishEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to publish task completion event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish task completion event: " + e.getMessage());
        }
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
            
            return publishEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to publish task timeout event: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "Failed to publish task timeout event: " + e.getMessage());
        }
    }

    /**
     * Get active session list
     * 
     * @param userId user ID (optional)
     * @return session list
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveSessions(String userId) {
        log.info("Getting active sessions: userId={}", userId);
        
        try {
            List<Map<String, Object>> sessions = new ArrayList<>();
            
            for (WebSocketSession session : activeSessions.values()) {
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
     * 
     * @param userId user ID (optional)
     * @param eventType event type (optional)
     * @return subscription list
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEventSubscriptions(String userId, String eventType) {
        log.info("Getting event subscriptions: userId={}, eventType={}", userId, eventType);
        
        try {
            List<Map<String, Object>> subscriptions = new ArrayList<>();
            
            for (Map.Entry<String, List<EventSubscription>> entry : eventSubscriptions.entrySet()) {
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
     * 
     * @param userId user ID (optional)
     * @param limit result limit
     * @return notification history
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getNotificationHistory(String userId, Integer limit) {
        log.info("Getting notification history: userId={}, limit={}", userId, limit);
        
        try {
            List<Map<String, Object>> history = new ArrayList<>();
            
            int count = 0;
            int maxLimit = limit != null ? limit : 100;
            
            // Return in reverse chronological order
            for (int i = notificationHistory.size() - 1; i >= 0 && count < maxLimit; i--) {
                NotificationRecord record = notificationHistory.get(i);
                
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

    // ==================== Private helper methods ====================

    /**
     * Publish an event
     */
    private NotificationResult publishEvent(WorkflowEvent event) {
        log.info("Publishing event: eventType={}, sourceId={}", event.getEventType(), event.getSourceId());
        
        try {
            // Publish Spring event (if eventPublisher is not null)
            if (eventPublisher != null) {
                eventPublisher.publishEvent(event);
            }
            
            // Process event subscriptions
            List<EventSubscription> subscriptions = eventSubscriptions.get(event.getEventType());
            if (subscriptions != null) {
                for (EventSubscription subscription : subscriptions) {
                    if (subscription.matchesEvent(event)) {
                        sendNotificationToUser(event, subscription);
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
    private void sendNotificationToUser(WorkflowEvent event, EventSubscription subscription) {
        try {
            String message = buildNotificationMessage(event);
            
            // Create notification record
            NotificationRecord record = new NotificationRecord(
                    event.getEventId(),
                    subscription.getUserId(),
                    "WEBSOCKET",
                    message
            );
            
            notificationHistory.add(record);
            
            // Send WebSocket notification
            boolean delivered = sendWebSocketNotification(subscription.getUserId(), message);
            record.setDelivered(delivered);
            
            log.info("Sent notification to user: userId={}, eventType={}, delivered={}", 
                    subscription.getUserId(), event.getEventType(), delivered);
                    
        } catch (Exception e) {
            log.error("Failed to send notification to user: {}", e.getMessage(), e);
        }
    }

    /**
     * Send WebSocket notification
     */
    private boolean sendWebSocketNotification(String userId, String message) {
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
    private String buildNotificationMessage(WorkflowEvent event) {
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
        log.info("Sending Kafka message: topic={}, key={}, eventType={}", topic, key, event.getEventType());
        
        try {
            KafkaMessage message = new KafkaMessage(KAFKA_TOPIC_PREFIX + topic, key, event);
            
            // Simulated Kafka sending (should use KafkaTemplate in production)
            String messageJson = objectMapper.writeValueAsString(message);
            
            // Store in Redis to simulate Kafka queue
            String queueKey = NOTIFICATION_PREFIX + "kafka:" + topic;
            stringRedisTemplate.opsForList().rightPush(queueKey, messageJson);
            stringRedisTemplate.expire(queueKey, Duration.ofDays(7));
            
            // Trigger consumers
            triggerKafkaConsumers(topic, event);
            
            log.info("Kafka message sent successfully: messageId={}", message.getMessageId());
            
            return NotificationResult.builder()
                    .success(true)
                    .message("Kafka message sent successfully")
                    .eventId(message.getMessageId())
                    .build();
                    
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka message: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("KAFKA_SEND_FAILED", "Failed to serialize Kafka message: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to send Kafka message: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("KAFKA_SEND_FAILED", "Failed to send Kafka message: " + e.getMessage());
        }
    }

    /**
     * Register a Kafka consumer
     * 
     * @param topic topic
     * @param consumer consumer handler function
     * @return registration result
     */
    public NotificationResult registerKafkaConsumer(String topic, Consumer<WorkflowEvent> consumer) {
        log.info("Registering Kafka consumer: topic={}", topic);
        
        kafkaConsumers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(consumer);
        
        return NotificationResult.builder()
                .success(true)
                .message("Kafka consumer registered successfully")
                .build();
    }

    /**
     * Trigger Kafka consumers
     */
    private void triggerKafkaConsumers(String topic, WorkflowEvent event) {
        List<Consumer<WorkflowEvent>> consumers = kafkaConsumers.get(topic);
        if (consumers != null) {
            for (Consumer<WorkflowEvent> consumer : consumers) {
                try {
                    consumer.accept(event);
                } catch (Exception e) {
                    log.error("Kafka consumer processing failed: topic={}, error={}", topic, e.getMessage(), e);
                }
            }
        }
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
        log.info("Sending email notification: userId={}, email={}, subject={}", userId, email, subject);
        
        try {
            // Check user notification preferences
            if (!isChannelEnabled(userId, "EMAIL")) {
                log.info("User has disabled email notifications: userId={}", userId);
                return NotificationResult.builder()
                        .success(false)
                        .message("User has disabled email notifications")
                        .build();
            }
            
            // Simulated email sending (should use JavaMailSender in production)
            log.info("Email sent successfully: to={}, subject={}", email, subject);
            
            // Record notification
            NotificationRecord record = new NotificationRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    "EMAIL",
                    subject + ": " + body
            );
            record.setDelivered(true);
            notificationHistory.add(record);
            
            return NotificationResult.builder()
                    .success(true)
                    .message("Email sent successfully")
                    .notificationId(record.getNotificationId())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EMAIL_SEND_FAILED", "Failed to send email notification: " + e.getMessage());
        }
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
        log.info("Sending SMS notification: userId={}, phoneNumber={}", userId, phoneNumber);
        
        try {
            // Check user notification preferences
            if (!isChannelEnabled(userId, "SMS")) {
                log.info("User has disabled SMS notifications: userId={}", userId);
                return NotificationResult.builder()
                        .success(false)
                        .message("User has disabled SMS notifications")
                        .build();
            }
            
            // Simulated SMS sending (should use SMS service SDK in production)
            log.info("SMS sent successfully: to={}, message={}", phoneNumber, message);
            
            // Record notification
            NotificationRecord record = new NotificationRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    "SMS",
                    message
            );
            record.setDelivered(true);
            notificationHistory.add(record);
            
            return NotificationResult.builder()
                    .success(true)
                    .message("SMS sent successfully")
                    .notificationId(record.getNotificationId())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to send SMS notification: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("SMS_SEND_FAILED", "Failed to send SMS notification: " + e.getMessage());
        }
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
        log.info("Sending in-app notification: userId={}, title={}", userId, title);
        
        try {
            // Check user notification preferences
            if (!isChannelEnabled(userId, "IN_APP")) {
                log.info("User has disabled in-app notifications: userId={}", userId);
                return NotificationResult.builder()
                        .success(false)
                        .message("User has disabled in-app notifications")
                        .build();
            }
            
            // Store in-app message in Redis
            String messageId = UUID.randomUUID().toString();
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messageId", messageId);
            messageData.put("userId", userId);
            messageData.put("title", title);
            messageData.put("content", content);
            messageData.put("eventType", eventType);
            messageData.put("read", false);
            messageData.put("createdTime", LocalDateTime.now().toString());
            
            String messageKey = NOTIFICATION_PREFIX + "in_app:" + userId + ":" + messageId;
            String messageJson = objectMapper.writeValueAsString(messageData);
            stringRedisTemplate.opsForValue().set(messageKey, messageJson, Duration.ofDays(30));
            
            // Record notification
            NotificationRecord record = new NotificationRecord(
                    messageId,
                    userId,
                    "IN_APP",
                    title + ": " + content
            );
            record.setDelivered(true);
            notificationHistory.add(record);
            
            // Also send WebSocket notification
            sendWebSocketNotification(userId, title + ": " + content);
            
            return NotificationResult.builder()
                    .success(true)
                    .message("In-app notification sent successfully")
                    .notificationId(messageId)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to send in-app notification: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("IN_APP_SEND_FAILED", "Failed to send in-app notification: " + e.getMessage());
        }
    }

    /**
     * Send multi-channel notification
     * 
     * @param userId user ID
     * @param event workflow event
     * @return send result
     */
    public NotificationResult sendMultiChannelNotification(String userId, WorkflowEvent event) {
        log.info("Sending multi-channel notification: userId={}, eventType={}", userId, event.getEventType());
        
        try {
            UserNotificationPreference preference = getUserPreference(userId);
            NotificationTemplate template = getTemplateForEvent(event.getEventType());
            
            String message = buildNotificationMessage(event);
            String subject = template != null ? template.getSubject() : "Workflow Notification";
            
            int successCount = 0;
            int totalChannels = 0;
            
            Set<String> channels = preference != null ? preference.getEnabledChannels() : 
                    Set.of("WEBSOCKET", "IN_APP");
            
            for (String channel : channels) {
                totalChannels++;
                try {
                    switch (channel) {
                        case "WEBSOCKET":
                            if (sendWebSocketNotification(userId, message)) {
                                successCount++;
                            }
                            break;
                        case "IN_APP":
                            sendInAppNotification(userId, subject, message, event.getEventType());
                            successCount++;
                            break;
                        case "EMAIL":
                            log.info("Email notification requires user email address");
                            break;
                        case "SMS":
                            log.info("SMS notification requires user phone number");
                            break;
                    }
                } catch (Exception e) {
                    log.error("Failed to send {} notification: {}", channel, e.getMessage());
                }
            }
            
            return NotificationResult.builder()
                    .success(successCount > 0)
                    .message(String.format("Multi-channel notification completed: %d/%d succeeded", successCount, totalChannels))
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to send multi-channel notification: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("MULTI_CHANNEL_SEND_FAILED", "Failed to send multi-channel notification: " + e.getMessage());
        }
    }

    // ==================== Notification template management ====================

    /**
     * Define a notification template
     * 
     * @param template notification template
     * @return definition result
     */
    public NotificationResult defineNotificationTemplate(NotificationTemplate template) {
        log.info("Defining notification template: templateId={}, eventType={}", template.getTemplateId(), template.getEventType());
        
        try {
            if (template.getCreatedTime() == null) {
                template.setCreatedTime(LocalDateTime.now());
            }
            
            notificationTemplates.put(template.getTemplateId(), template);
            
            // Cache to Redis
            String cacheKey = NOTIFICATION_PREFIX + "template:" + template.getTemplateId();
            String templateJson = objectMapper.writeValueAsString(template);
            stringRedisTemplate.opsForValue().set(cacheKey, templateJson, Duration.ofDays(30));
            
            return NotificationResult.builder()
                    .success(true)
                    .message("Notification template defined successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to define notification template: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("TEMPLATE_DEFINE_FAILED", "Failed to define notification template: " + e.getMessage());
        }
    }

    /**
     * Get notification template for an event
     */
    private NotificationTemplate getTemplateForEvent(String eventType) {
        return notificationTemplates.values().stream()
                .filter(t -> t.isEnabled() && eventType.equals(t.getEventType()))
                .findFirst()
                .orElse(null);
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
        log.info("Rendering notification template: templateId={}, language={}", templateId, language);
        
        NotificationTemplate template = notificationTemplates.get(templateId);
        if (template == null) {
            throw new WorkflowBusinessException("TEMPLATE_NOT_FOUND", "Notification template not found: " + templateId);
        }
        
        // Get localized content
        String subject = template.getSubject();
        String body = template.getBodyTemplate();
        
        if (language != null && template.getLocalizedSubjects() != null) {
            subject = template.getLocalizedSubjects().getOrDefault(language, subject);
        }
        if (language != null && template.getLocalizedBodies() != null) {
            body = template.getLocalizedBodies().getOrDefault(language, body);
        }
        
        // Replace variables
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            subject = subject.replace(placeholder, value);
            body = body.replace(placeholder, value);
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("subject", subject);
        result.put("body", body);
        return result;
    }

    // ==================== User notification preference management ====================

    /**
     * Set user notification preference
     * 
     * @param preference user notification preference
     * @return set result
     */
    public NotificationResult setUserNotificationPreference(UserNotificationPreference preference) {
        log.info("Setting user notification preference: userId={}", preference.getUserId());
        
        try {
            preference.setUpdatedTime(LocalDateTime.now());
            userPreferences.put(preference.getUserId(), preference);
            
            // Cache to Redis
            String cacheKey = NOTIFICATION_PREFIX + "preference:" + preference.getUserId();
            String preferenceJson = objectMapper.writeValueAsString(preference);
            stringRedisTemplate.opsForValue().set(cacheKey, preferenceJson, Duration.ofDays(365));
            
            return NotificationResult.builder()
                    .success(true)
                    .message("User notification preference set successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to set user notification preference: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("PREFERENCE_SET_FAILED", "Failed to set user notification preference: " + e.getMessage());
        }
    }

    /**
     * Get user notification preference
     */
    private UserNotificationPreference getUserPreference(String userId) {
        return userPreferences.get(userId);
    }

    /**
     * Check if user has enabled a specified channel
     */
    private boolean isChannelEnabled(String userId, String channel) {
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

    /**
     * Get unread in-app messages for a user
     * 
     * @param userId user ID
     * @param limit result limit
     * @return unread message list
     */
    public List<Map<String, Object>> getUnreadInAppMessages(String userId, int limit) {
        log.info("Getting unread in-app messages: userId={}, limit={}", userId, limit);
        
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            String pattern = NOTIFICATION_PREFIX + "in_app:" + userId + ":*";
            Set<String> keys = stringRedisTemplate.keys(pattern);
            
            if (keys == null || keys.isEmpty()) {
                return messages;
            }
            
            for (String key : keys) {
                if (messages.size() >= limit) {
                    break;
                }
                
                String messageJson = stringRedisTemplate.opsForValue().get(key);
                if (messageJson != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = objectMapper.readValue(messageJson, Map.class);
                    if (Boolean.FALSE.equals(message.get("read"))) {
                        messages.add(message);
                    }
                }
            }
            
            return messages;
            
        } catch (Exception e) {
            log.error("Failed to get unread in-app messages: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("GET_MESSAGES_FAILED", "Failed to get unread in-app messages: " + e.getMessage());
        }
    }

    /**
     * Mark in-app message as read
     * 
     * @param userId user ID
     * @param messageId message ID
     * @return mark result
     */
    public NotificationResult markInAppMessageAsRead(String userId, String messageId) {
        log.info("Marking in-app message as read: userId={}, messageId={}", userId, messageId);
        
        try {
            String messageKey = NOTIFICATION_PREFIX + "in_app:" + userId + ":" + messageId;
            String messageJson = stringRedisTemplate.opsForValue().get(messageKey);
            
            if (messageJson == null) {
                return NotificationResult.builder()
                        .success(false)
                        .message("Message not found")
                        .build();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = objectMapper.readValue(messageJson, Map.class);
            message.put("read", true);
            message.put("readTime", LocalDateTime.now().toString());
            
            String updatedJson = objectMapper.writeValueAsString(message);
            stringRedisTemplate.opsForValue().set(messageKey, updatedJson, Duration.ofDays(30));
            
            return NotificationResult.builder()
                    .success(true)
                    .message("Message marked as read")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to mark message as read: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("MARK_READ_FAILED", "Failed to mark message as read: " + e.getMessage());
        }
    }

    /**
     * Initialize default notification templates
     */
    public void initializeDefaultTemplates() {
        log.info("Initializing default notification templates");
        
        // Task assignment template
        NotificationTemplate taskAssignedTemplate = new NotificationTemplate();
        taskAssignedTemplate.setTemplateId("TASK_ASSIGNED_DEFAULT");
        taskAssignedTemplate.setTemplateName("Task Assignment Notification");
        taskAssignedTemplate.setEventType("TASK_ASSIGNED");
        taskAssignedTemplate.setSubject("You have a new task to process");
        taskAssignedTemplate.setBodyTemplate("Task ${taskName} has been assigned to you, please process it promptly.");
        taskAssignedTemplate.setLocalizedSubjects(Map.of("en", "You have a new task"));
        taskAssignedTemplate.setLocalizedBodies(Map.of("en", "Task ${taskName} has been assigned to you."));
        taskAssignedTemplate.setChannels(Set.of("WEBSOCKET", "IN_APP", "EMAIL"));
        taskAssignedTemplate.setEnabled(true);
        defineNotificationTemplate(taskAssignedTemplate);
        
        // Task overdue template
        NotificationTemplate taskOverdueTemplate = new NotificationTemplate();
        taskOverdueTemplate.setTemplateId("TASK_OVERDUE_DEFAULT");
        taskOverdueTemplate.setTemplateName("Task Overdue Notification");
        taskOverdueTemplate.setEventType("TASK_OVERDUE");
        taskOverdueTemplate.setSubject("Task is overdue");
        taskOverdueTemplate.setBodyTemplate("Task ${taskName} is overdue, please process it as soon as possible.");
        taskOverdueTemplate.setLocalizedSubjects(Map.of("en", "Task overdue"));
        taskOverdueTemplate.setLocalizedBodies(Map.of("en", "Task ${taskName} is overdue."));
        taskOverdueTemplate.setChannels(Set.of("WEBSOCKET", "IN_APP", "EMAIL", "SMS"));
        taskOverdueTemplate.setEnabled(true);
        defineNotificationTemplate(taskOverdueTemplate);
        
        // Process completed template
        NotificationTemplate processCompletedTemplate = new NotificationTemplate();
        processCompletedTemplate.setTemplateId("PROCESS_COMPLETED_DEFAULT");
        processCompletedTemplate.setTemplateName("Process Completed Notification");
        processCompletedTemplate.setEventType("PROCESS_COMPLETED");
        processCompletedTemplate.setSubject("Process completed");
        processCompletedTemplate.setBodyTemplate("Process ${processDefinitionKey} (business key: ${businessKey}) has been completed.");
        processCompletedTemplate.setLocalizedSubjects(Map.of("en", "Process completed"));
        processCompletedTemplate.setLocalizedBodies(Map.of("en", "Process ${processDefinitionKey} (business key: ${businessKey}) has been completed."));
        processCompletedTemplate.setChannels(Set.of("WEBSOCKET", "IN_APP"));
        processCompletedTemplate.setEnabled(true);
        defineNotificationTemplate(processCompletedTemplate);
        
        log.info("Default notification templates initialized");
    }
}