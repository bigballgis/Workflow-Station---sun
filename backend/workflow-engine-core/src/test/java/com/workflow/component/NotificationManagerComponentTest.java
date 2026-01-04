package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.workflow.dto.response.NotificationResult;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 通知管理组件单元测试
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("通知管理组件测试")
class NotificationManagerComponentTest {

    @Mock(lenient = true)
    private ApplicationEventPublisher eventPublisher;
    
    @Mock(lenient = true)
    private StringRedisTemplate stringRedisTemplate;
    
    @Mock(lenient = true)
    private ValueOperations<String, String> valueOperations;
    
    @Mock(lenient = true)
    private ListOperations<String, String> listOperations;

    private ObjectMapper objectMapper;
    private NotificationManagerComponent notificationManager;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        
        notificationManager = new NotificationManagerComponent(eventPublisher, stringRedisTemplate, objectMapper);
    }

    @Test
    @DisplayName("注册WebSocket会话 - 成功")
    void registerWebSocketSession_Success() {
        // Given
        String sessionId = "session-123";
        String userId = "user-456";

        // When
        NotificationResult result = notificationManager.registerWebSocketSession(sessionId, userId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("WebSocket会话注册成功");
        assertThat(result.getSessionId()).isEqualTo(sessionId);
        
        // 验证事件发布
        verify(eventPublisher).publishEvent(any(NotificationManagerComponent.WorkflowEvent.class));
        
        // 验证会话已注册
        List<Map<String, Object>> sessions = notificationManager.getActiveSessions(userId);
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).get("sessionId")).isEqualTo(sessionId);
        assertThat(sessions.get(0).get("userId")).isEqualTo(userId);
    }

    @Test
    @DisplayName("注册WebSocket会话 - 会话ID为空")
    void registerWebSocketSession_EmptySessionId() {
        // Given
        String sessionId = "";
        String userId = "user-456";

        // When & Then
        assertThatThrownBy(() -> notificationManager.registerWebSocketSession(sessionId, userId))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("会话ID不能为空");
    }

    @Test
    @DisplayName("注册WebSocket会话 - 用户ID为空")
    void registerWebSocketSession_EmptyUserId() {
        // Given
        String sessionId = "session-123";
        String userId = null;

        // When & Then
        assertThatThrownBy(() -> notificationManager.registerWebSocketSession(sessionId, userId))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("用户ID不能为空");
    }

    @Test
    @DisplayName("注销WebSocket会话 - 成功")
    void unregisterWebSocketSession_Success() {
        // Given
        String sessionId = "session-123";
        String userId = "user-456";
        
        // 先注册会话
        notificationManager.registerWebSocketSession(sessionId, userId);
        
        // When
        NotificationResult result = notificationManager.unregisterWebSocketSession(sessionId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("WebSocket会话注销成功");
        assertThat(result.getSessionId()).isEqualTo(sessionId);
        
        // 验证会话已注销
        List<Map<String, Object>> sessions = notificationManager.getActiveSessions(userId);
        assertThat(sessions).isEmpty();
    }

    @Test
    @DisplayName("注销WebSocket会话 - 会话不存在")
    void unregisterWebSocketSession_SessionNotExists() {
        // Given
        String sessionId = "non-existent-session";

        // When
        NotificationResult result = notificationManager.unregisterWebSocketSession(sessionId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("WebSocket会话注销成功");
    }

    @Test
    @DisplayName("订阅事件 - 成功")
    void subscribeEvent_Success() {
        // Given
        String eventType = "PROCESS_STARTED";
        String userId = "user-456";
        Map<String, Object> filters = Map.of("processDefinitionKey", "test-process");

        // When
        NotificationResult result = notificationManager.subscribeEvent(eventType, userId, filters);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("事件订阅成功");
        assertThat(result.getSubscriptionId()).isNotNull();
        
        // 验证订阅已创建
        List<Map<String, Object>> subscriptions = notificationManager.getEventSubscriptions(userId, eventType);
        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).get("eventType")).isEqualTo(eventType);
        assertThat(subscriptions.get(0).get("userId")).isEqualTo(userId);
    }

    @Test
    @DisplayName("订阅事件 - 事件类型为空")
    void subscribeEvent_EmptyEventType() {
        // Given
        String eventType = "";
        String userId = "user-456";
        Map<String, Object> filters = new HashMap<>();

        // When & Then
        assertThatThrownBy(() -> notificationManager.subscribeEvent(eventType, userId, filters))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("事件类型不能为空");
    }

    @Test
    @DisplayName("取消订阅事件 - 成功")
    void unsubscribeEvent_Success() {
        // Given
        String eventType = "PROCESS_STARTED";
        String userId = "user-456";
        
        // 先创建订阅
        NotificationResult subscribeResult = notificationManager.subscribeEvent(eventType, userId, null);
        String subscriptionId = subscribeResult.getSubscriptionId();

        // When
        NotificationResult result = notificationManager.unsubscribeEvent(subscriptionId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("取消订阅成功");
        
        // 验证订阅已删除
        List<Map<String, Object>> subscriptions = notificationManager.getEventSubscriptions(userId, eventType);
        assertThat(subscriptions).isEmpty();
    }

    @Test
    @DisplayName("取消订阅事件 - 订阅不存在")
    void unsubscribeEvent_SubscriptionNotExists() {
        // Given
        String subscriptionId = "non-existent-subscription";

        // When
        NotificationResult result = notificationManager.unsubscribeEvent(subscriptionId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("订阅不存在");
    }

    @Test
    @DisplayName("发布流程启动事件 - 成功")
    void publishProcessStartedEvent_Success() {
        // Given
        String processInstanceId = "proc-inst-123";
        String processDefinitionKey = "test-process";
        String businessKey = "business-456";
        String startUserId = "user-789";

        // When
        NotificationResult result = notificationManager.publishProcessStartedEvent(
                processInstanceId, processDefinitionKey, businessKey, startUserId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("事件发布成功");
        assertThat(result.getEventId()).isNotNull();
        
        // 验证Spring事件发布
        verify(eventPublisher).publishEvent(any(NotificationManagerComponent.WorkflowEvent.class));
    }

    @Test
    @DisplayName("发布流程完成事件 - 成功")
    void publishProcessCompletedEvent_Success() {
        // Given
        String processInstanceId = "proc-inst-123";
        String processDefinitionKey = "test-process";
        String businessKey = "business-456";
        String endUserId = "user-789";

        // When
        NotificationResult result = notificationManager.publishProcessCompletedEvent(
                processInstanceId, processDefinitionKey, businessKey, endUserId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("事件发布成功");
        assertThat(result.getEventId()).isNotNull();
        
        // 验证Spring事件发布
        verify(eventPublisher).publishEvent(any(NotificationManagerComponent.WorkflowEvent.class));
    }

    @Test
    @DisplayName("发布任务分配事件 - 成功")
    void publishTaskAssignedEvent_Success() {
        // Given
        String taskId = "task-123";
        String taskName = "审批任务";
        String assignee = "user-456";
        String processInstanceId = "proc-inst-789";

        // When
        NotificationResult result = notificationManager.publishTaskAssignedEvent(
                taskId, taskName, assignee, processInstanceId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("事件发布成功");
        assertThat(result.getEventId()).isNotNull();
        
        // 验证Spring事件发布
        verify(eventPublisher).publishEvent(any(NotificationManagerComponent.WorkflowEvent.class));
    }

    @Test
    @DisplayName("发布任务完成事件 - 成功")
    void publishTaskCompletedEvent_Success() {
        // Given
        String taskId = "task-123";
        String taskName = "审批任务";
        String assignee = "user-456";
        String processInstanceId = "proc-inst-789";

        // When
        NotificationResult result = notificationManager.publishTaskCompletedEvent(
                taskId, taskName, assignee, processInstanceId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("事件发布成功");
        assertThat(result.getEventId()).isNotNull();
        
        // 验证Spring事件发布
        verify(eventPublisher).publishEvent(any(NotificationManagerComponent.WorkflowEvent.class));
    }

    @Test
    @DisplayName("发布任务超时事件 - 成功")
    void publishTaskOverdueEvent_Success() {
        // Given
        String taskId = "task-123";
        String taskName = "审批任务";
        String assignee = "user-456";
        String processInstanceId = "proc-inst-789";
        LocalDateTime dueDate = LocalDateTime.now().minusHours(1);

        // When
        NotificationResult result = notificationManager.publishTaskOverdueEvent(
                taskId, taskName, assignee, processInstanceId, dueDate);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("事件发布成功");
        assertThat(result.getEventId()).isNotNull();
        
        // 验证Spring事件发布
        verify(eventPublisher).publishEvent(any(NotificationManagerComponent.WorkflowEvent.class));
    }

    @Test
    @DisplayName("事件订阅和通知 - 完整流程")
    void eventSubscriptionAndNotification_CompleteFlow() {
        // Given
        String sessionId = "session-123";
        String userId = "user-456";
        String eventType = "TASK_ASSIGNED";
        
        // 注册WebSocket会话
        notificationManager.registerWebSocketSession(sessionId, userId);
        
        // 订阅事件
        Map<String, Object> filters = Map.of("assignee", userId);
        NotificationResult subscribeResult = notificationManager.subscribeEvent(eventType, userId, filters);
        
        // When - 发布匹配的事件
        NotificationResult eventResult = notificationManager.publishTaskAssignedEvent(
                "task-123", "测试任务", userId, "proc-inst-789");

        // Then
        assertThat(subscribeResult.isSuccess()).isTrue();
        assertThat(eventResult.isSuccess()).isTrue();
        
        // 验证通知历史记录
        List<Map<String, Object>> history = notificationManager.getNotificationHistory(userId, 10);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("userId")).isEqualTo(userId);
        assertThat(history.get(0).get("notificationType")).isEqualTo("WEBSOCKET");
        assertThat(history.get(0).get("delivered")).isEqualTo(true);
    }

    @Test
    @DisplayName("事件过滤 - 不匹配的事件不会触发通知")
    void eventFiltering_NonMatchingEventDoesNotTriggerNotification() {
        // Given
        String sessionId = "session-123";
        String userId = "user-456";
        String eventType = "TASK_ASSIGNED";
        
        // 注册WebSocket会话
        notificationManager.registerWebSocketSession(sessionId, userId);
        
        // 订阅事件，只关注特定分配人
        Map<String, Object> filters = Map.of("assignee", "other-user");
        notificationManager.subscribeEvent(eventType, userId, filters);
        
        // When - 发布不匹配的事件
        notificationManager.publishTaskAssignedEvent(
                "task-123", "测试任务", userId, "proc-inst-789"); // assignee是userId，不匹配过滤条件

        // Then - 不应该有通知记录
        List<Map<String, Object>> history = notificationManager.getNotificationHistory(userId, 10);
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("获取活跃会话 - 按用户过滤")
    void getActiveSessions_FilterByUser() {
        // Given
        String user1 = "user-1";
        String user2 = "user-2";
        
        notificationManager.registerWebSocketSession("session-1", user1);
        notificationManager.registerWebSocketSession("session-2", user1);
        notificationManager.registerWebSocketSession("session-3", user2);

        // When
        List<Map<String, Object>> user1Sessions = notificationManager.getActiveSessions(user1);
        List<Map<String, Object>> user2Sessions = notificationManager.getActiveSessions(user2);
        List<Map<String, Object>> allSessions = notificationManager.getActiveSessions(null);

        // Then
        assertThat(user1Sessions).hasSize(2);
        assertThat(user2Sessions).hasSize(1);
        assertThat(allSessions).hasSize(3);
        
        assertThat(user1Sessions.stream().allMatch(s -> user1.equals(s.get("userId")))).isTrue();
        assertThat(user2Sessions.stream().allMatch(s -> user2.equals(s.get("userId")))).isTrue();
    }

    @Test
    @DisplayName("获取事件订阅 - 按用户和事件类型过滤")
    void getEventSubscriptions_FilterByUserAndEventType() {
        // Given
        String user1 = "user-1";
        String user2 = "user-2";
        String eventType1 = "PROCESS_STARTED";
        String eventType2 = "TASK_ASSIGNED";
        
        notificationManager.subscribeEvent(eventType1, user1, null);
        notificationManager.subscribeEvent(eventType2, user1, null);
        notificationManager.subscribeEvent(eventType1, user2, null);

        // When
        List<Map<String, Object>> user1Subscriptions = notificationManager.getEventSubscriptions(user1, null);
        List<Map<String, Object>> processStartedSubscriptions = notificationManager.getEventSubscriptions(null, eventType1);
        List<Map<String, Object>> user1ProcessStarted = notificationManager.getEventSubscriptions(user1, eventType1);

        // Then
        assertThat(user1Subscriptions).hasSize(2);
        assertThat(processStartedSubscriptions).hasSize(2);
        assertThat(user1ProcessStarted).hasSize(1);
        
        assertThat(user1ProcessStarted.get(0).get("userId")).isEqualTo(user1);
        assertThat(user1ProcessStarted.get(0).get("eventType")).isEqualTo(eventType1);
    }

    @Test
    @DisplayName("获取通知历史 - 限制数量")
    void getNotificationHistory_WithLimit() {
        // Given
        String userId = "user-456";
        String sessionId = "session-123";
        
        // 注册会话和订阅
        notificationManager.registerWebSocketSession(sessionId, userId);
        notificationManager.subscribeEvent("TASK_ASSIGNED", userId, null);
        
        // 发布多个事件
        for (int i = 0; i < 5; i++) {
            notificationManager.publishTaskAssignedEvent(
                    "task-" + i, "任务" + i, userId, "proc-inst-" + i);
        }

        // When
        List<Map<String, Object>> limitedHistory = notificationManager.getNotificationHistory(userId, 3);
        List<Map<String, Object>> allHistory = notificationManager.getNotificationHistory(userId, null);

        // Then
        assertThat(limitedHistory).hasSize(3);
        assertThat(allHistory).hasSize(5);
        
        // 验证按时间倒序返回（最新的在前面）
        assertThat(limitedHistory.get(0).get("sentTime")).isNotNull();
    }

    @Test
    @DisplayName("多用户并发通知")
    void multiUserConcurrentNotification() {
        // Given
        String[] users = {"user-1", "user-2", "user-3"};
        String eventType = "PROCESS_STARTED";
        
        // 为每个用户注册会话和订阅
        for (String user : users) {
            notificationManager.registerWebSocketSession("session-" + user, user);
            notificationManager.subscribeEvent(eventType, user, null);
        }

        // When - 发布事件
        notificationManager.publishProcessStartedEvent(
                "proc-inst-123", "test-process", "business-key", "starter-user");

        // Then - 每个用户都应该收到通知
        for (String user : users) {
            List<Map<String, Object>> history = notificationManager.getNotificationHistory(user, 10);
            assertThat(history).hasSize(1);
            assertThat(history.get(0).get("delivered")).isEqualTo(true);
        }
    }

    @Test
    @DisplayName("WebSocket会话断开后不接收通知")
    void noNotificationAfterSessionDisconnected() {
        // Given
        String sessionId = "session-123";
        String userId = "user-456";
        
        // 注册会话和订阅
        notificationManager.registerWebSocketSession(sessionId, userId);
        notificationManager.subscribeEvent("TASK_ASSIGNED", userId, null);
        
        // 断开会话
        notificationManager.unregisterWebSocketSession(sessionId);

        // When - 发布事件
        notificationManager.publishTaskAssignedEvent(
                "task-123", "测试任务", userId, "proc-inst-789");

        // Then - 应该有通知记录但未送达
        List<Map<String, Object>> history = notificationManager.getNotificationHistory(userId, 10);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("delivered")).isEqualTo(false);
    }

    @Test
    @DisplayName("事件数据完整性验证")
    void eventDataIntegrityValidation() {
        // Given
        String userId = "user-456";
        String sessionId = "session-123";
        
        notificationManager.registerWebSocketSession(sessionId, userId);
        notificationManager.subscribeEvent("PROCESS_COMPLETED", userId, null);

        // When
        String processInstanceId = "proc-inst-123";
        String processDefinitionKey = "test-process";
        String businessKey = "business-456";
        String endUserId = "end-user-789";
        
        notificationManager.publishProcessCompletedEvent(
                processInstanceId, processDefinitionKey, businessKey, endUserId);

        // Then
        List<Map<String, Object>> history = notificationManager.getNotificationHistory(userId, 1);
        assertThat(history).hasSize(1);
        
        Map<String, Object> notification = history.get(0);
        assertThat(notification.get("userId")).isEqualTo(userId);
        assertThat(notification.get("notificationType")).isEqualTo("WEBSOCKET");
        String message = (String) notification.get("message");
        assertThat(message).contains("流程已完成");
        assertThat(message).contains(processDefinitionKey);
        assertThat(message).contains(businessKey);
    }

    @Test
    @DisplayName("异常处理 - ApplicationEventPublisher抛出异常")
    void exceptionHandling_EventPublisherThrowsException() {
        // Given - 创建新的组件实例以避免Mockito严格模式冲突
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        NotificationManagerComponent testManager = new NotificationManagerComponent(mockPublisher, stringRedisTemplate, objectMapper);
        
        doThrow(new RuntimeException("Event publisher error"))
                .when(mockPublisher).publishEvent(any(NotificationManagerComponent.WorkflowEvent.class));

        // When & Then
        assertThatThrownBy(() -> testManager.publishProcessStartedEvent(
                "proc-inst-123", "test-process", "business-key", "user-456"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("发布流程启动事件失败");
    }

    // ==================== 新增测试：Kafka、多渠道通知、模板 ====================

    @Nested
    @DisplayName("Kafka消息测试")
    class KafkaMessageTests {

        @Test
        @DisplayName("发送Kafka消息成功")
        void sendKafkaMessage_Success() {
            // Given
            NotificationManagerComponent.WorkflowEvent event = new NotificationManagerComponent.WorkflowEvent(
                    "TEST_EVENT", "source-001", "TEST", Map.of("key", "value"));

            // When
            NotificationResult result = notificationManager.sendKafkaMessage("test-topic", "key-001", event);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("Kafka消息发送成功");
            verify(listOperations).rightPush(anyString(), anyString());
        }

        @Test
        @DisplayName("注册Kafka消费者成功")
        void registerKafkaConsumer_Success() {
            // When
            NotificationResult result = notificationManager.registerKafkaConsumer("test-topic", event -> {
                // 消费者处理逻辑
            });

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("Kafka消费者注册成功");
        }
    }

    @Nested
    @DisplayName("多渠道通知测试")
    class MultiChannelNotificationTests {

        @Test
        @DisplayName("发送邮件通知成功")
        void sendEmailNotification_Success() {
            // When
            NotificationResult result = notificationManager.sendEmailNotification(
                    "user-001", "test@example.com", "测试主题", "测试内容");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("邮件发送成功");
        }

        @Test
        @DisplayName("发送短信通知成功")
        void sendSmsNotification_Success() {
            // When
            NotificationResult result = notificationManager.sendSmsNotification(
                    "user-001", "13800138000", "测试短信内容");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("短信发送成功");
        }

        @Test
        @DisplayName("发送站内消息成功")
        void sendInAppNotification_Success() {
            // When
            NotificationResult result = notificationManager.sendInAppNotification(
                    "user-001", "测试标题", "测试内容", "TEST_EVENT");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("站内消息发送成功");
            assertThat(result.getNotificationId()).isNotNull();
        }

        @Test
        @DisplayName("发送多渠道通知成功")
        void sendMultiChannelNotification_Success() {
            // Given
            String userId = "user-001";
            notificationManager.registerWebSocketSession("session-001", userId);
            
            NotificationManagerComponent.WorkflowEvent event = new NotificationManagerComponent.WorkflowEvent(
                    "TASK_ASSIGNED", "task-001", "TASK", 
                    Map.of("taskName", "测试任务", "assignee", userId));

            // When
            NotificationResult result = notificationManager.sendMultiChannelNotification(userId, event);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("成功");
        }
    }

    @Nested
    @DisplayName("通知模板测试")
    class NotificationTemplateTests {

        @Test
        @DisplayName("定义通知模板成功")
        void defineNotificationTemplate_Success() {
            // Given
            NotificationManagerComponent.NotificationTemplate template = new NotificationManagerComponent.NotificationTemplate();
            template.setTemplateId("template-001");
            template.setTemplateName("测试模板");
            template.setEventType("TASK_ASSIGNED");
            template.setSubject("任务通知");
            template.setBodyTemplate("任务 ${taskName} 已分配给您");
            template.setChannels(Set.of("EMAIL", "WEBSOCKET"));
            template.setEnabled(true);

            // When
            NotificationResult result = notificationManager.defineNotificationTemplate(template);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("通知模板定义成功");
        }

        @Test
        @DisplayName("渲染通知模板成功")
        void renderNotificationTemplate_Success() {
            // Given
            NotificationManagerComponent.NotificationTemplate template = new NotificationManagerComponent.NotificationTemplate();
            template.setTemplateId("template-001");
            template.setSubject("任务: ${taskName}");
            template.setBodyTemplate("任务 ${taskName} 已分配给 ${assignee}");
            template.setLocalizedSubjects(Map.of("en", "Task: ${taskName}"));
            template.setLocalizedBodies(Map.of("en", "Task ${taskName} assigned to ${assignee}"));
            template.setEnabled(true);
            notificationManager.defineNotificationTemplate(template);

            Map<String, Object> variables = Map.of("taskName", "审批任务", "assignee", "张三");

            // When - 中文
            Map<String, String> zhResult = notificationManager.renderNotificationTemplate("template-001", variables, null);

            // Then
            assertThat(zhResult.get("subject")).isEqualTo("任务: 审批任务");
            assertThat(zhResult.get("body")).isEqualTo("任务 审批任务 已分配给 张三");

            // When - 英文
            Map<String, String> enResult = notificationManager.renderNotificationTemplate("template-001", variables, "en");

            // Then
            assertThat(enResult.get("subject")).isEqualTo("Task: 审批任务");
            assertThat(enResult.get("body")).isEqualTo("Task 审批任务 assigned to 张三");
        }

        @Test
        @DisplayName("初始化默认模板成功")
        void initializeDefaultTemplates_Success() {
            // When
            notificationManager.initializeDefaultTemplates();

            // Then - 验证模板已创建（通过渲染测试）
            Map<String, Object> variables = Map.of("taskName", "测试任务");
            Map<String, String> result = notificationManager.renderNotificationTemplate(
                    "TASK_ASSIGNED_DEFAULT", variables, null);
            
            assertThat(result.get("subject")).isEqualTo("您有新的任务待处理");
            assertThat(result.get("body")).contains("测试任务");
        }
    }

    @Nested
    @DisplayName("用户通知偏好测试")
    class UserPreferenceTests {

        @Test
        @DisplayName("设置用户通知偏好成功")
        void setUserNotificationPreference_Success() {
            // Given
            NotificationManagerComponent.UserNotificationPreference preference = 
                    new NotificationManagerComponent.UserNotificationPreference();
            preference.setUserId("user-001");
            preference.setEnabledChannels(Set.of("EMAIL", "WEBSOCKET"));
            preference.setPreferredLanguage("zh");
            preference.setDoNotDisturb(false);

            // When
            NotificationResult result = notificationManager.setUserNotificationPreference(preference);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("用户通知偏好设置成功");
        }

        @Test
        @DisplayName("用户禁用渠道后不发送通知")
        void disabledChannelDoesNotSendNotification() {
            // Given
            NotificationManagerComponent.UserNotificationPreference preference = 
                    new NotificationManagerComponent.UserNotificationPreference();
            preference.setUserId("user-001");
            preference.setEnabledChannels(Set.of("WEBSOCKET")); // 只启用WebSocket，禁用EMAIL
            notificationManager.setUserNotificationPreference(preference);

            // When
            NotificationResult result = notificationManager.sendEmailNotification(
                    "user-001", "test@example.com", "测试", "内容");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("用户已禁用邮件通知");
        }
    }

    @Nested
    @DisplayName("站内消息管理测试")
    class InAppMessageTests {

        @Test
        @DisplayName("获取未读站内消息")
        void getUnreadInAppMessages_Success() {
            // Given
            when(stringRedisTemplate.keys(anyString())).thenReturn(Set.of());

            // When
            List<Map<String, Object>> messages = notificationManager.getUnreadInAppMessages("user-001", 10);

            // Then
            assertThat(messages).isEmpty();
        }

        @Test
        @DisplayName("标记站内消息为已读 - 消息不存在")
        void markInAppMessageAsRead_MessageNotExists() {
            // Given
            when(valueOperations.get(anyString())).thenReturn(null);

            // When
            NotificationResult result = notificationManager.markInAppMessageAsRead("user-001", "msg-001");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("消息不存在");
        }
    }
}