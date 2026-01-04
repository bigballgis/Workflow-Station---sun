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
 * 通知管理组件
 * 
 * 负责实时事件推送和WebSocket通知
 * 支持流程生命周期事件的发布和订阅
 * 集成Kafka消息队列实现事件驱动架构
 * 支持邮件、站内消息、WebSocket推送等通知方式
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
    
    // WebSocket会话管理
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    
    // 事件订阅管理
    private final Map<String, List<EventSubscription>> eventSubscriptions = new ConcurrentHashMap<>();
    
    // 通知历史记录
    private final List<NotificationRecord> notificationHistory = new CopyOnWriteArrayList<>();
    
    // Kafka消息处理器（模拟）
    private final Map<String, List<Consumer<WorkflowEvent>>> kafkaConsumers = new ConcurrentHashMap<>();
    
    // 通知模板
    private final Map<String, NotificationTemplate> notificationTemplates = new ConcurrentHashMap<>();
    
    // 用户通知偏好
    private final Map<String, UserNotificationPreference> userPreferences = new ConcurrentHashMap<>();
    
    // 缓存键前缀
    private static final String NOTIFICATION_PREFIX = "notification:";
    private static final String KAFKA_TOPIC_PREFIX = "workflow:";

    /**
     * 简化的WebSocket会话类
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
                // 模拟WebSocket消息发送
                log.info("发送WebSocket消息到会话 {}: {}", sessionId, message);
            }
        }
    }
    
    /**
     * 事件订阅类
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
            
            // 检查过滤条件
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
     * 工作流事件类
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
     * 通知记录类
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
     * 通知模板类
     */
    public static class NotificationTemplate {
        private String templateId;
        private String templateName;
        private String eventType;
        private String subject;
        private String bodyTemplate;
        private Map<String, String> localizedSubjects; // 国际化标题
        private Map<String, String> localizedBodies; // 国际化内容
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
     * 用户通知偏好类
     */
    public static class UserNotificationPreference {
        private String userId;
        private Set<String> enabledChannels; // EMAIL, SMS, WEBSOCKET, IN_APP
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
     * Kafka消息类
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
     * 注册WebSocket会话
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 注册结果
     */
    @Transactional
    public NotificationResult registerWebSocketSession(String sessionId, String userId) {
        log.info("注册WebSocket会话: sessionId={}, userId={}", sessionId, userId);
        
        try {
            // 验证参数
            validateSessionParameters(sessionId, userId);
            
            // 创建会话
            WebSocketSession session = new WebSocketSession(sessionId, userId);
            activeSessions.put(sessionId, session);
            
            // 发布会话连接事件
            WorkflowEvent event = new WorkflowEvent(
                    "SESSION_CONNECTED",
                    sessionId,
                    "WEBSOCKET_SESSION",
                    Map.of("userId", userId, "connectedTime", session.getConnectedTime())
            );
            
            publishEvent(event);
            
            return NotificationResult.builder()
                    .success(true)
                    .message("WebSocket会话注册成功")
                    .sessionId(sessionId)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("注册WebSocket会话失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("SESSION_REGISTER_FAILED", "注册WebSocket会话失败: " + e.getMessage());
        }
    }

    /**
     * 注销WebSocket会话
     * 
     * @param sessionId 会话ID
     * @return 注销结果
     */
    @Transactional
    public NotificationResult unregisterWebSocketSession(String sessionId) {
        log.info("注销WebSocket会话: sessionId={}", sessionId);
        
        try {
            WebSocketSession session = activeSessions.remove(sessionId);
            
            if (session != null) {
                session.setActive(false);
                
                // 发布会话断开事件
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
                    .message("WebSocket会话注销成功")
                    .sessionId(sessionId)
                    .build();
                    
        } catch (Exception e) {
            log.error("注销WebSocket会话失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("SESSION_UNREGISTER_FAILED", "注销WebSocket会话失败: " + e.getMessage());
        }
    }

    /**
     * 订阅事件
     * 
     * @param eventType 事件类型
     * @param userId 用户ID
     * @param filters 过滤条件
     * @return 订阅结果
     */
    @Transactional
    public NotificationResult subscribeEvent(String eventType, String userId, Map<String, Object> filters) {
        log.info("订阅事件: eventType={}, userId={}, filters={}", eventType, userId, filters);
        
        try {
            // 验证参数
            validateSubscriptionParameters(eventType, userId);
            
            // 创建订阅
            String subscriptionId = UUID.randomUUID().toString();
            EventSubscription subscription = new EventSubscription(subscriptionId, eventType, userId, filters);
            
            eventSubscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscription);
            
            return NotificationResult.builder()
                    .success(true)
                    .message("事件订阅成功")
                    .subscriptionId(subscriptionId)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("订阅事件失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_SUBSCRIBE_FAILED", "订阅事件失败: " + e.getMessage());
        }
    }

    /**
     * 取消订阅事件
     * 
     * @param subscriptionId 订阅ID
     * @return 取消订阅结果
     */
    @Transactional
    public NotificationResult unsubscribeEvent(String subscriptionId) {
        log.info("取消订阅事件: subscriptionId={}", subscriptionId);
        
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
                    .message(removed ? "取消订阅成功" : "订阅不存在")
                    .subscriptionId(subscriptionId)
                    .build();
                    
        } catch (Exception e) {
            log.error("取消订阅事件失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_UNSUBSCRIBE_FAILED", "取消订阅事件失败: " + e.getMessage());
        }
    }

    /**
     * 发布流程启动事件
     * 
     * @param processInstanceId 流程实例ID
     * @param processDefinitionKey 流程定义键
     * @param businessKey 业务键
     * @param startUserId 启动用户ID
     * @return 发布结果
     */
    @Transactional
    public NotificationResult publishProcessStartedEvent(String processInstanceId, String processDefinitionKey, 
                                                       String businessKey, String startUserId) {
        log.info("发布流程启动事件: processInstanceId={}, processDefinitionKey={}", processInstanceId, processDefinitionKey);
        
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
            log.error("发布流程启动事件失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "发布流程启动事件失败: " + e.getMessage());
        }
    }

    /**
     * 发布流程完成事件
     * 
     * @param processInstanceId 流程实例ID
     * @param processDefinitionKey 流程定义键
     * @param businessKey 业务键
     * @param endUserId 结束用户ID
     * @return 发布结果
     */
    @Transactional
    public NotificationResult publishProcessCompletedEvent(String processInstanceId, String processDefinitionKey, 
                                                         String businessKey, String endUserId) {
        log.info("发布流程完成事件: processInstanceId={}, processDefinitionKey={}", processInstanceId, processDefinitionKey);
        
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
            log.error("发布流程完成事件失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "发布流程完成事件失败: " + e.getMessage());
        }
    }

    /**
     * 发布任务分配事件
     * 
     * @param taskId 任务ID
     * @param taskName 任务名称
     * @param assignee 分配人
     * @param processInstanceId 流程实例ID
     * @return 发布结果
     */
    @Transactional
    public NotificationResult publishTaskAssignedEvent(String taskId, String taskName, String assignee, String processInstanceId) {
        log.info("发布任务分配事件: taskId={}, assignee={}", taskId, assignee);
        
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
            log.error("发布任务分配事件失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "发布任务分配事件失败: " + e.getMessage());
        }
    }

    /**
     * 发布任务完成事件
     * 
     * @param taskId 任务ID
     * @param taskName 任务名称
     * @param assignee 完成人
     * @param processInstanceId 流程实例ID
     * @return 发布结果
     */
    @Transactional
    public NotificationResult publishTaskCompletedEvent(String taskId, String taskName, String assignee, String processInstanceId) {
        log.info("发布任务完成事件: taskId={}, assignee={}", taskId, assignee);
        
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
            log.error("发布任务完成事件失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "发布任务完成事件失败: " + e.getMessage());
        }
    }

    /**
     * 发布任务超时事件
     * 
     * @param taskId 任务ID
     * @param taskName 任务名称
     * @param assignee 分配人
     * @param processInstanceId 流程实例ID
     * @param dueDate 到期时间
     * @return 发布结果
     */
    @Transactional
    public NotificationResult publishTaskOverdueEvent(String taskId, String taskName, String assignee, 
                                                    String processInstanceId, LocalDateTime dueDate) {
        log.info("发布任务超时事件: taskId={}, assignee={}", taskId, assignee);
        
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
            log.error("发布任务超时事件失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "发布任务超时事件失败: " + e.getMessage());
        }
    }

    /**
     * 获取活跃会话列表
     * 
     * @param userId 用户ID（可选）
     * @return 会话列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveSessions(String userId) {
        log.info("获取活跃会话列表: userId={}", userId);
        
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
            log.error("获取活跃会话列表失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("GET_SESSIONS_FAILED", "获取活跃会话列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取事件订阅列表
     * 
     * @param userId 用户ID（可选）
     * @param eventType 事件类型（可选）
     * @return 订阅列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEventSubscriptions(String userId, String eventType) {
        log.info("获取事件订阅列表: userId={}, eventType={}", userId, eventType);
        
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
            log.error("获取事件订阅列表失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("GET_SUBSCRIPTIONS_FAILED", "获取事件订阅列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取通知历史记录
     * 
     * @param userId 用户ID（可选）
     * @param limit 限制数量
     * @return 通知历史
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getNotificationHistory(String userId, Integer limit) {
        log.info("获取通知历史记录: userId={}, limit={}", userId, limit);
        
        try {
            List<Map<String, Object>> history = new ArrayList<>();
            
            int count = 0;
            int maxLimit = limit != null ? limit : 100;
            
            // 按时间倒序返回
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
            log.error("获取通知历史记录失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("GET_HISTORY_FAILED", "获取通知历史记录失败: " + e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 发布事件
     */
    private NotificationResult publishEvent(WorkflowEvent event) {
        log.info("发布事件: eventType={}, sourceId={}", event.getEventType(), event.getSourceId());
        
        try {
            // 发布Spring事件（如果eventPublisher不为null）
            if (eventPublisher != null) {
                eventPublisher.publishEvent(event);
            }
            
            // 处理事件订阅
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
                    .message("事件发布成功")
                    .eventId(event.getEventId())
                    .build();
                    
        } catch (Exception e) {
            log.error("发布事件失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EVENT_PUBLISH_FAILED", "发布事件失败: " + e.getMessage());
        }
    }

    /**
     * 向用户发送通知
     */
    private void sendNotificationToUser(WorkflowEvent event, EventSubscription subscription) {
        try {
            String message = buildNotificationMessage(event);
            
            // 创建通知记录
            NotificationRecord record = new NotificationRecord(
                    event.getEventId(),
                    subscription.getUserId(),
                    "WEBSOCKET",
                    message
            );
            
            notificationHistory.add(record);
            
            // 发送WebSocket通知
            boolean delivered = sendWebSocketNotification(subscription.getUserId(), message);
            record.setDelivered(delivered);
            
            log.info("向用户发送通知: userId={}, eventType={}, delivered={}", 
                    subscription.getUserId(), event.getEventType(), delivered);
                    
        } catch (Exception e) {
            log.error("向用户发送通知失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送WebSocket通知
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
     * 构建通知消息
     */
    private String buildNotificationMessage(WorkflowEvent event) {
        Map<String, Object> data = event.getEventData();
        
        switch (event.getEventType()) {
            case "PROCESS_STARTED":
                return String.format("流程已启动: %s (业务键: %s)", 
                        data.get("processDefinitionKey"), data.get("businessKey"));
                        
            case "PROCESS_COMPLETED":
                return String.format("流程已完成: %s (业务键: %s)", 
                        data.get("processDefinitionKey"), data.get("businessKey"));
                        
            case "TASK_ASSIGNED":
                return String.format("任务已分配: %s (分配给: %s)", 
                        data.get("taskName"), data.get("assignee"));
                        
            case "TASK_COMPLETED":
                return String.format("任务已完成: %s (完成人: %s)", 
                        data.get("taskName"), data.get("assignee"));
                        
            case "TASK_OVERDUE":
                return String.format("任务已超时: %s (分配给: %s)", 
                        data.get("taskName"), data.get("assignee"));
                        
            default:
                return String.format("工作流事件: %s", event.getEventType());
        }
    }

    /**
     * 验证会话参数
     */
    private void validateSessionParameters(String sessionId, String userId) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(sessionId)) {
            errors.add(new WorkflowValidationException.ValidationError("sessionId", "会话ID不能为空", sessionId));
        }
        
        if (!StringUtils.hasText(userId)) {
            errors.add(new WorkflowValidationException.ValidationError("userId", "用户ID不能为空", userId));
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * 验证订阅参数
     */
    private void validateSubscriptionParameters(String eventType, String userId) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (!StringUtils.hasText(eventType)) {
            errors.add(new WorkflowValidationException.ValidationError("eventType", "事件类型不能为空", eventType));
        }
        
        if (!StringUtils.hasText(userId)) {
            errors.add(new WorkflowValidationException.ValidationError("userId", "用户ID不能为空", userId));
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    // ==================== Kafka消息队列集成 ====================

    /**
     * 发送Kafka消息
     * 
     * @param topic 主题
     * @param key 消息键
     * @param event 工作流事件
     * @return 发送结果
     */
    public NotificationResult sendKafkaMessage(String topic, String key, WorkflowEvent event) {
        log.info("发送Kafka消息: topic={}, key={}, eventType={}", topic, key, event.getEventType());
        
        try {
            KafkaMessage message = new KafkaMessage(KAFKA_TOPIC_PREFIX + topic, key, event);
            
            // 模拟Kafka发送（实际实现中应该使用KafkaTemplate）
            String messageJson = objectMapper.writeValueAsString(message);
            
            // 存储到Redis模拟Kafka队列
            String queueKey = NOTIFICATION_PREFIX + "kafka:" + topic;
            stringRedisTemplate.opsForList().rightPush(queueKey, messageJson);
            stringRedisTemplate.expire(queueKey, Duration.ofDays(7));
            
            // 触发消费者
            triggerKafkaConsumers(topic, event);
            
            log.info("Kafka消息发送成功: messageId={}", message.getMessageId());
            
            return NotificationResult.builder()
                    .success(true)
                    .message("Kafka消息发送成功")
                    .eventId(message.getMessageId())
                    .build();
                    
        } catch (JsonProcessingException e) {
            log.error("Kafka消息序列化失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("KAFKA_SEND_FAILED", "Kafka消息序列化失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("发送Kafka消息失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("KAFKA_SEND_FAILED", "发送Kafka消息失败: " + e.getMessage());
        }
    }

    /**
     * 注册Kafka消费者
     * 
     * @param topic 主题
     * @param consumer 消费者处理函数
     * @return 注册结果
     */
    public NotificationResult registerKafkaConsumer(String topic, Consumer<WorkflowEvent> consumer) {
        log.info("注册Kafka消费者: topic={}", topic);
        
        kafkaConsumers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(consumer);
        
        return NotificationResult.builder()
                .success(true)
                .message("Kafka消费者注册成功")
                .build();
    }

    /**
     * 触发Kafka消费者
     */
    private void triggerKafkaConsumers(String topic, WorkflowEvent event) {
        List<Consumer<WorkflowEvent>> consumers = kafkaConsumers.get(topic);
        if (consumers != null) {
            for (Consumer<WorkflowEvent> consumer : consumers) {
                try {
                    consumer.accept(event);
                } catch (Exception e) {
                    log.error("Kafka消费者处理失败: topic={}, error={}", topic, e.getMessage(), e);
                }
            }
        }
    }

    // ==================== 多渠道通知 ====================

    /**
     * 发送邮件通知
     * 
     * @param userId 用户ID
     * @param email 邮箱地址
     * @param subject 邮件主题
     * @param body 邮件内容
     * @return 发送结果
     */
    public NotificationResult sendEmailNotification(String userId, String email, String subject, String body) {
        log.info("发送邮件通知: userId={}, email={}, subject={}", userId, email, subject);
        
        try {
            // 检查用户通知偏好
            if (!isChannelEnabled(userId, "EMAIL")) {
                log.info("用户已禁用邮件通知: userId={}", userId);
                return NotificationResult.builder()
                        .success(false)
                        .message("用户已禁用邮件通知")
                        .build();
            }
            
            // 模拟邮件发送（实际实现中应该使用JavaMailSender）
            log.info("邮件发送成功: to={}, subject={}", email, subject);
            
            // 记录通知
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
                    .message("邮件发送成功")
                    .notificationId(record.getNotificationId())
                    .build();
                    
        } catch (Exception e) {
            log.error("发送邮件通知失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EMAIL_SEND_FAILED", "发送邮件通知失败: " + e.getMessage());
        }
    }

    /**
     * 发送短信通知
     * 
     * @param userId 用户ID
     * @param phoneNumber 手机号
     * @param message 短信内容
     * @return 发送结果
     */
    public NotificationResult sendSmsNotification(String userId, String phoneNumber, String message) {
        log.info("发送短信通知: userId={}, phoneNumber={}", userId, phoneNumber);
        
        try {
            // 检查用户通知偏好
            if (!isChannelEnabled(userId, "SMS")) {
                log.info("用户已禁用短信通知: userId={}", userId);
                return NotificationResult.builder()
                        .success(false)
                        .message("用户已禁用短信通知")
                        .build();
            }
            
            // 模拟短信发送（实际实现中应该使用短信服务SDK）
            log.info("短信发送成功: to={}, message={}", phoneNumber, message);
            
            // 记录通知
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
                    .message("短信发送成功")
                    .notificationId(record.getNotificationId())
                    .build();
                    
        } catch (Exception e) {
            log.error("发送短信通知失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("SMS_SEND_FAILED", "发送短信通知失败: " + e.getMessage());
        }
    }

    /**
     * 发送站内消息
     * 
     * @param userId 用户ID
     * @param title 消息标题
     * @param content 消息内容
     * @param eventType 事件类型
     * @return 发送结果
     */
    public NotificationResult sendInAppNotification(String userId, String title, String content, String eventType) {
        log.info("发送站内消息: userId={}, title={}", userId, title);
        
        try {
            // 检查用户通知偏好
            if (!isChannelEnabled(userId, "IN_APP")) {
                log.info("用户已禁用站内消息: userId={}", userId);
                return NotificationResult.builder()
                        .success(false)
                        .message("用户已禁用站内消息")
                        .build();
            }
            
            // 存储站内消息到Redis
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
            
            // 记录通知
            NotificationRecord record = new NotificationRecord(
                    messageId,
                    userId,
                    "IN_APP",
                    title + ": " + content
            );
            record.setDelivered(true);
            notificationHistory.add(record);
            
            // 同时发送WebSocket通知
            sendWebSocketNotification(userId, title + ": " + content);
            
            return NotificationResult.builder()
                    .success(true)
                    .message("站内消息发送成功")
                    .notificationId(messageId)
                    .build();
                    
        } catch (Exception e) {
            log.error("发送站内消息失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("IN_APP_SEND_FAILED", "发送站内消息失败: " + e.getMessage());
        }
    }

    /**
     * 批量发送通知（多渠道）
     * 
     * @param userId 用户ID
     * @param event 工作流事件
     * @return 发送结果
     */
    public NotificationResult sendMultiChannelNotification(String userId, WorkflowEvent event) {
        log.info("发送多渠道通知: userId={}, eventType={}", userId, event.getEventType());
        
        try {
            UserNotificationPreference preference = getUserPreference(userId);
            NotificationTemplate template = getTemplateForEvent(event.getEventType());
            
            String message = buildNotificationMessage(event);
            String subject = template != null ? template.getSubject() : "工作流通知";
            
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
                            // 需要用户邮箱地址
                            log.info("邮件通知需要用户邮箱地址");
                            break;
                        case "SMS":
                            // 需要用户手机号
                            log.info("短信通知需要用户手机号");
                            break;
                    }
                } catch (Exception e) {
                    log.error("发送{}通知失败: {}", channel, e.getMessage());
                }
            }
            
            return NotificationResult.builder()
                    .success(successCount > 0)
                    .message(String.format("多渠道通知发送完成: %d/%d 成功", successCount, totalChannels))
                    .build();
                    
        } catch (Exception e) {
            log.error("发送多渠道通知失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("MULTI_CHANNEL_SEND_FAILED", "发送多渠道通知失败: " + e.getMessage());
        }
    }

    // ==================== 通知模板管理 ====================

    /**
     * 定义通知模板
     * 
     * @param template 通知模板
     * @return 定义结果
     */
    public NotificationResult defineNotificationTemplate(NotificationTemplate template) {
        log.info("定义通知模板: templateId={}, eventType={}", template.getTemplateId(), template.getEventType());
        
        try {
            if (template.getCreatedTime() == null) {
                template.setCreatedTime(LocalDateTime.now());
            }
            
            notificationTemplates.put(template.getTemplateId(), template);
            
            // 缓存到Redis
            String cacheKey = NOTIFICATION_PREFIX + "template:" + template.getTemplateId();
            String templateJson = objectMapper.writeValueAsString(template);
            stringRedisTemplate.opsForValue().set(cacheKey, templateJson, Duration.ofDays(30));
            
            return NotificationResult.builder()
                    .success(true)
                    .message("通知模板定义成功")
                    .build();
                    
        } catch (Exception e) {
            log.error("定义通知模板失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("TEMPLATE_DEFINE_FAILED", "定义通知模板失败: " + e.getMessage());
        }
    }

    /**
     * 获取事件对应的通知模板
     */
    private NotificationTemplate getTemplateForEvent(String eventType) {
        return notificationTemplates.values().stream()
                .filter(t -> t.isEnabled() && eventType.equals(t.getEventType()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 使用模板渲染通知内容
     * 
     * @param templateId 模板ID
     * @param variables 变量
     * @param language 语言
     * @return 渲染后的内容
     */
    public Map<String, String> renderNotificationTemplate(String templateId, Map<String, Object> variables, String language) {
        log.info("渲染通知模板: templateId={}, language={}", templateId, language);
        
        NotificationTemplate template = notificationTemplates.get(templateId);
        if (template == null) {
            throw new WorkflowBusinessException("TEMPLATE_NOT_FOUND", "通知模板不存在: " + templateId);
        }
        
        // 获取本地化内容
        String subject = template.getSubject();
        String body = template.getBodyTemplate();
        
        if (language != null && template.getLocalizedSubjects() != null) {
            subject = template.getLocalizedSubjects().getOrDefault(language, subject);
        }
        if (language != null && template.getLocalizedBodies() != null) {
            body = template.getLocalizedBodies().getOrDefault(language, body);
        }
        
        // 替换变量
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

    // ==================== 用户通知偏好管理 ====================

    /**
     * 设置用户通知偏好
     * 
     * @param preference 用户通知偏好
     * @return 设置结果
     */
    public NotificationResult setUserNotificationPreference(UserNotificationPreference preference) {
        log.info("设置用户通知偏好: userId={}", preference.getUserId());
        
        try {
            preference.setUpdatedTime(LocalDateTime.now());
            userPreferences.put(preference.getUserId(), preference);
            
            // 缓存到Redis
            String cacheKey = NOTIFICATION_PREFIX + "preference:" + preference.getUserId();
            String preferenceJson = objectMapper.writeValueAsString(preference);
            stringRedisTemplate.opsForValue().set(cacheKey, preferenceJson, Duration.ofDays(365));
            
            return NotificationResult.builder()
                    .success(true)
                    .message("用户通知偏好设置成功")
                    .build();
                    
        } catch (Exception e) {
            log.error("设置用户通知偏好失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("PREFERENCE_SET_FAILED", "设置用户通知偏好失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户通知偏好
     */
    private UserNotificationPreference getUserPreference(String userId) {
        return userPreferences.get(userId);
    }

    /**
     * 检查用户是否启用了指定渠道
     */
    private boolean isChannelEnabled(String userId, String channel) {
        UserNotificationPreference preference = getUserPreference(userId);
        if (preference == null) {
            return true; // 默认启用所有渠道
        }
        
        // 检查免打扰模式
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
     * 获取用户未读站内消息
     * 
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 未读消息列表
     */
    public List<Map<String, Object>> getUnreadInAppMessages(String userId, int limit) {
        log.info("获取用户未读站内消息: userId={}, limit={}", userId, limit);
        
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
            log.error("获取未读站内消息失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("GET_MESSAGES_FAILED", "获取未读站内消息失败: " + e.getMessage());
        }
    }

    /**
     * 标记站内消息为已读
     * 
     * @param userId 用户ID
     * @param messageId 消息ID
     * @return 标记结果
     */
    public NotificationResult markInAppMessageAsRead(String userId, String messageId) {
        log.info("标记站内消息为已读: userId={}, messageId={}", userId, messageId);
        
        try {
            String messageKey = NOTIFICATION_PREFIX + "in_app:" + userId + ":" + messageId;
            String messageJson = stringRedisTemplate.opsForValue().get(messageKey);
            
            if (messageJson == null) {
                return NotificationResult.builder()
                        .success(false)
                        .message("消息不存在")
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
                    .message("消息已标记为已读")
                    .build();
                    
        } catch (Exception e) {
            log.error("标记消息为已读失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("MARK_READ_FAILED", "标记消息为已读失败: " + e.getMessage());
        }
    }

    /**
     * 初始化默认通知模板
     */
    public void initializeDefaultTemplates() {
        log.info("初始化默认通知模板");
        
        // 任务分配模板
        NotificationTemplate taskAssignedTemplate = new NotificationTemplate();
        taskAssignedTemplate.setTemplateId("TASK_ASSIGNED_DEFAULT");
        taskAssignedTemplate.setTemplateName("任务分配通知");
        taskAssignedTemplate.setEventType("TASK_ASSIGNED");
        taskAssignedTemplate.setSubject("您有新的任务待处理");
        taskAssignedTemplate.setBodyTemplate("任务 ${taskName} 已分配给您，请及时处理。");
        taskAssignedTemplate.setLocalizedSubjects(Map.of("en", "You have a new task"));
        taskAssignedTemplate.setLocalizedBodies(Map.of("en", "Task ${taskName} has been assigned to you."));
        taskAssignedTemplate.setChannels(Set.of("WEBSOCKET", "IN_APP", "EMAIL"));
        taskAssignedTemplate.setEnabled(true);
        defineNotificationTemplate(taskAssignedTemplate);
        
        // 任务超时模板
        NotificationTemplate taskOverdueTemplate = new NotificationTemplate();
        taskOverdueTemplate.setTemplateId("TASK_OVERDUE_DEFAULT");
        taskOverdueTemplate.setTemplateName("任务超时通知");
        taskOverdueTemplate.setEventType("TASK_OVERDUE");
        taskOverdueTemplate.setSubject("任务已超时");
        taskOverdueTemplate.setBodyTemplate("任务 ${taskName} 已超时，请尽快处理。");
        taskOverdueTemplate.setLocalizedSubjects(Map.of("en", "Task overdue"));
        taskOverdueTemplate.setLocalizedBodies(Map.of("en", "Task ${taskName} is overdue."));
        taskOverdueTemplate.setChannels(Set.of("WEBSOCKET", "IN_APP", "EMAIL", "SMS"));
        taskOverdueTemplate.setEnabled(true);
        defineNotificationTemplate(taskOverdueTemplate);
        
        // 流程完成模板
        NotificationTemplate processCompletedTemplate = new NotificationTemplate();
        processCompletedTemplate.setTemplateId("PROCESS_COMPLETED_DEFAULT");
        processCompletedTemplate.setTemplateName("流程完成通知");
        processCompletedTemplate.setEventType("PROCESS_COMPLETED");
        processCompletedTemplate.setSubject("流程已完成");
        processCompletedTemplate.setBodyTemplate("流程 ${processDefinitionKey} (业务键: ${businessKey}) 已完成。");
        processCompletedTemplate.setLocalizedSubjects(Map.of("en", "Process completed"));
        processCompletedTemplate.setLocalizedBodies(Map.of("en", "Process ${processDefinitionKey} (business key: ${businessKey}) has been completed."));
        processCompletedTemplate.setChannels(Set.of("WEBSOCKET", "IN_APP"));
        processCompletedTemplate.setEnabled(true);
        defineNotificationTemplate(processCompletedTemplate);
        
        log.info("默认通知模板初始化完成");
    }
}