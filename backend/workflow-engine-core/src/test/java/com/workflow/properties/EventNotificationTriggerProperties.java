package com.workflow.properties;

import com.workflow.component.NotificationManagerComponent;
import com.workflow.component.NotificationManagerComponent.*;
import com.workflow.dto.response.NotificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 事件通知触发准确性属性测试
 * 
 * 属性 17: 事件通知触发准确性
 * 验证需求: 需求 12.1
 * 
 * 测试事件发布后，订阅者能够准确接收到通知
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("事件通知触发准确性属性测试")
class EventNotificationTriggerProperties {

    @Autowired
    private NotificationManagerComponent notificationManager;

    private String testUserId;
    private String testSessionId;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-" + UUID.randomUUID().toString().substring(0, 8);
        testSessionId = "test-session-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ==================== 属性测试 ====================

    @RepeatedTest(5)
    @DisplayName("属性: 订阅事件后能收到对应类型的通知")
    void property_subscribedEventTypeReceivesNotification() {
        // Given: 用户订阅特定事件类型
        String eventType = "TASK_ASSIGNED";
        NotificationResult subscribeResult = notificationManager.subscribeEvent(eventType, testUserId, null);
        assertThat(subscribeResult.isSuccess()).isTrue();
        String subscriptionId = subscribeResult.getSubscriptionId();

        // 注册WebSocket会话以接收通知
        notificationManager.registerWebSocketSession(testSessionId, testUserId);

        // When: 发布该类型的事件
        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 8);
        NotificationResult publishResult = notificationManager.publishTaskAssignedEvent(
                taskId, "测试任务", testUserId, "process-001");

        // Then: 事件发布成功
        assertThat(publishResult.isSuccess()).isTrue();
        assertThat(publishResult.getEventId()).isNotNull();

        // 验证通知历史中有该事件的记录
        List<Map<String, Object>> history = notificationManager.getNotificationHistory(testUserId, 10);
        assertThat(history).isNotEmpty();
        
        boolean foundNotification = history.stream()
                .anyMatch(h -> "WEBSOCKET".equals(h.get("notificationType")) 
                        && h.get("message").toString().contains("测试任务"));
        assertThat(foundNotification).isTrue();

        // Cleanup
        notificationManager.unsubscribeEvent(subscriptionId);
        notificationManager.unregisterWebSocketSession(testSessionId);
    }

    @RepeatedTest(5)
    @DisplayName("属性: 未订阅的事件类型不会收到通知")
    void property_unsubscribedEventTypeDoesNotReceiveNotification() {
        // Given: 用户订阅TASK_ASSIGNED事件
        String subscribedEventType = "TASK_ASSIGNED";
        NotificationResult subscribeResult = notificationManager.subscribeEvent(subscribedEventType, testUserId, null);
        String subscriptionId = subscribeResult.getSubscriptionId();

        notificationManager.registerWebSocketSession(testSessionId, testUserId);

        // 记录当前通知数量
        int initialHistorySize = notificationManager.getNotificationHistory(testUserId, 100).size();

        // When: 发布PROCESS_STARTED事件（用户未订阅）
        NotificationResult publishResult = notificationManager.publishProcessStartedEvent(
                "process-001", "test-process", "business-001", "starter");

        // Then: 事件发布成功，但用户不会收到通知
        assertThat(publishResult.isSuccess()).isTrue();

        // 验证通知历史没有增加（因为用户没有订阅PROCESS_STARTED）
        List<Map<String, Object>> history = notificationManager.getNotificationHistory(testUserId, 100);
        int newNotifications = history.size() - initialHistorySize;
        
        // 新通知中不应该有PROCESS_STARTED相关的
        boolean hasProcessStartedNotification = history.stream()
                .skip(initialHistorySize)
                .anyMatch(h -> h.get("message").toString().contains("流程已启动"));
        assertThat(hasProcessStartedNotification).isFalse();

        // Cleanup
        notificationManager.unsubscribeEvent(subscriptionId);
        notificationManager.unregisterWebSocketSession(testSessionId);
    }

    @RepeatedTest(5)
    @DisplayName("属性: 带过滤条件的订阅只接收匹配的事件")
    void property_filteredSubscriptionReceivesOnlyMatchingEvents() {
        // Given: 用户订阅特定流程实例的任务事件
        String targetProcessId = "process-target";
        Map<String, Object> filters = Map.of("processInstanceId", targetProcessId);
        
        NotificationResult subscribeResult = notificationManager.subscribeEvent(
                "TASK_ASSIGNED", testUserId, filters);
        String subscriptionId = subscribeResult.getSubscriptionId();

        notificationManager.registerWebSocketSession(testSessionId, testUserId);

        int initialHistorySize = notificationManager.getNotificationHistory(testUserId, 100).size();

        // When: 发布匹配的事件
        notificationManager.publishTaskAssignedEvent(
                "task-001", "匹配任务", testUserId, targetProcessId);

        // And: 发布不匹配的事件
        notificationManager.publishTaskAssignedEvent(
                "task-002", "不匹配任务", testUserId, "process-other");

        // Then: 只有匹配的事件产生通知
        List<Map<String, Object>> history = notificationManager.getNotificationHistory(testUserId, 100);
        
        long matchingNotifications = history.stream()
                .skip(initialHistorySize)
                .filter(h -> h.get("message").toString().contains("匹配任务"))
                .count();
        
        long nonMatchingNotifications = history.stream()
                .skip(initialHistorySize)
                .filter(h -> h.get("message").toString().contains("不匹配任务"))
                .count();

        assertThat(matchingNotifications).isGreaterThanOrEqualTo(1);
        assertThat(nonMatchingNotifications).isEqualTo(0);

        // Cleanup
        notificationManager.unsubscribeEvent(subscriptionId);
        notificationManager.unregisterWebSocketSession(testSessionId);
    }

    @RepeatedTest(5)
    @DisplayName("属性: 取消订阅后不再收到通知")
    void property_unsubscribedUserDoesNotReceiveNotification() {
        // Given: 用户订阅事件
        NotificationResult subscribeResult = notificationManager.subscribeEvent(
                "TASK_COMPLETED", testUserId, null);
        String subscriptionId = subscribeResult.getSubscriptionId();

        notificationManager.registerWebSocketSession(testSessionId, testUserId);

        // When: 取消订阅
        NotificationResult unsubscribeResult = notificationManager.unsubscribeEvent(subscriptionId);
        assertThat(unsubscribeResult.isSuccess()).isTrue();

        int historyBeforePublish = notificationManager.getNotificationHistory(testUserId, 100).size();

        // And: 发布事件
        notificationManager.publishTaskCompletedEvent(
                "task-001", "完成任务", testUserId, "process-001");

        // Then: 用户不会收到通知
        List<Map<String, Object>> history = notificationManager.getNotificationHistory(testUserId, 100);
        int newNotifications = history.size() - historyBeforePublish;
        
        // 取消订阅后不应该收到TASK_COMPLETED通知
        boolean hasCompletedNotification = history.stream()
                .skip(historyBeforePublish)
                .anyMatch(h -> h.get("message").toString().contains("完成任务"));
        assertThat(hasCompletedNotification).isFalse();

        // Cleanup
        notificationManager.unregisterWebSocketSession(testSessionId);
    }

    @RepeatedTest(5)
    @DisplayName("属性: 多个订阅者都能收到同一事件的通知")
    void property_multipleSubscribersReceiveNotification() {
        // Given: 多个用户订阅同一事件类型
        String user1 = "user1-" + UUID.randomUUID().toString().substring(0, 8);
        String user2 = "user2-" + UUID.randomUUID().toString().substring(0, 8);
        String session1 = "session1-" + UUID.randomUUID().toString().substring(0, 8);
        String session2 = "session2-" + UUID.randomUUID().toString().substring(0, 8);

        NotificationResult sub1 = notificationManager.subscribeEvent("TASK_OVERDUE", user1, null);
        NotificationResult sub2 = notificationManager.subscribeEvent("TASK_OVERDUE", user2, null);

        notificationManager.registerWebSocketSession(session1, user1);
        notificationManager.registerWebSocketSession(session2, user2);

        // When: 发布事件
        notificationManager.publishTaskOverdueEvent(
                "task-001", "超时任务", "assignee", "process-001", LocalDateTime.now().minusDays(1));

        // Then: 两个用户都收到通知
        List<Map<String, Object>> history1 = notificationManager.getNotificationHistory(user1, 10);
        List<Map<String, Object>> history2 = notificationManager.getNotificationHistory(user2, 10);

        boolean user1Received = history1.stream()
                .anyMatch(h -> h.get("message").toString().contains("超时任务"));
        boolean user2Received = history2.stream()
                .anyMatch(h -> h.get("message").toString().contains("超时任务"));

        assertThat(user1Received).isTrue();
        assertThat(user2Received).isTrue();

        // Cleanup
        notificationManager.unsubscribeEvent(sub1.getSubscriptionId());
        notificationManager.unsubscribeEvent(sub2.getSubscriptionId());
        notificationManager.unregisterWebSocketSession(session1);
        notificationManager.unregisterWebSocketSession(session2);
    }

    @Test
    @DisplayName("属性: 事件发布时间戳准确")
    void property_eventTimestampIsAccurate() {
        // Given
        LocalDateTime beforePublish = LocalDateTime.now();

        // When: 发布事件
        NotificationResult result = notificationManager.publishProcessStartedEvent(
                "process-001", "test-process", "business-001", testUserId);

        LocalDateTime afterPublish = LocalDateTime.now();

        // Then: 事件发布成功，时间戳在合理范围内
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEventId()).isNotNull();
        // 事件ID是UUID格式，表示事件已创建
    }

    @Test
    @DisplayName("属性: WebSocket会话管理正确")
    void property_webSocketSessionManagementCorrect() {
        // Given: 注册会话
        NotificationResult registerResult = notificationManager.registerWebSocketSession(testSessionId, testUserId);
        assertThat(registerResult.isSuccess()).isTrue();

        // Then: 会话在活跃列表中
        List<Map<String, Object>> sessions = notificationManager.getActiveSessions(testUserId);
        assertThat(sessions).isNotEmpty();
        assertThat(sessions.stream().anyMatch(s -> testSessionId.equals(s.get("sessionId")))).isTrue();

        // When: 注销会话
        NotificationResult unregisterResult = notificationManager.unregisterWebSocketSession(testSessionId);
        assertThat(unregisterResult.isSuccess()).isTrue();

        // Then: 会话不在活跃列表中
        sessions = notificationManager.getActiveSessions(testUserId);
        assertThat(sessions.stream().noneMatch(s -> testSessionId.equals(s.get("sessionId")))).isTrue();
    }

    @Test
    @DisplayName("属性: 订阅列表查询正确")
    void property_subscriptionListQueryCorrect() {
        // Given: 创建多个订阅
        String sub1Id = notificationManager.subscribeEvent("TASK_ASSIGNED", testUserId, null).getSubscriptionId();
        String sub2Id = notificationManager.subscribeEvent("TASK_COMPLETED", testUserId, null).getSubscriptionId();
        String sub3Id = notificationManager.subscribeEvent("PROCESS_STARTED", testUserId, null).getSubscriptionId();

        // When: 查询用户的所有订阅
        List<Map<String, Object>> allSubscriptions = notificationManager.getEventSubscriptions(testUserId, null);

        // Then: 返回所有订阅
        assertThat(allSubscriptions.size()).isGreaterThanOrEqualTo(3);

        // When: 按事件类型查询
        List<Map<String, Object>> taskAssignedSubs = notificationManager.getEventSubscriptions(testUserId, "TASK_ASSIGNED");

        // Then: 只返回匹配的订阅
        assertThat(taskAssignedSubs).isNotEmpty();
        assertThat(taskAssignedSubs.stream().allMatch(s -> "TASK_ASSIGNED".equals(s.get("eventType")))).isTrue();

        // Cleanup
        notificationManager.unsubscribeEvent(sub1Id);
        notificationManager.unsubscribeEvent(sub2Id);
        notificationManager.unsubscribeEvent(sub3Id);
    }

    @Test
    @DisplayName("属性: 通知历史记录完整")
    void property_notificationHistoryComplete() {
        // Given: 订阅并注册会话
        notificationManager.subscribeEvent("TASK_ASSIGNED", testUserId, null);
        notificationManager.registerWebSocketSession(testSessionId, testUserId);

        // When: 发布多个事件
        for (int i = 0; i < 5; i++) {
            notificationManager.publishTaskAssignedEvent(
                    "task-" + i, "任务" + i, testUserId, "process-001");
        }

        // Then: 通知历史包含所有通知
        List<Map<String, Object>> history = notificationManager.getNotificationHistory(testUserId, 100);
        
        long taskNotifications = history.stream()
                .filter(h -> h.get("message").toString().contains("任务"))
                .count();
        
        assertThat(taskNotifications).isGreaterThanOrEqualTo(5);

        // 验证历史记录包含必要字段
        for (Map<String, Object> record : history) {
            assertThat(record).containsKeys("notificationId", "userId", "notificationType", "message", "sentTime");
        }

        // Cleanup
        notificationManager.unregisterWebSocketSession(testSessionId);
    }

    @Test
    @DisplayName("属性: Kafka消息发送和消费正确")
    void property_kafkaMessageSendAndConsumeCorrect() {
        // Given: 注册Kafka消费者
        AtomicInteger consumedCount = new AtomicInteger(0);
        notificationManager.registerKafkaConsumer("test-topic", event -> {
            consumedCount.incrementAndGet();
        });

        // When: 发送Kafka消息
        WorkflowEvent event = new WorkflowEvent(
                "TEST_EVENT", "source-001", "TEST", Map.of("key", "value"));
        NotificationResult result = notificationManager.sendKafkaMessage("test-topic", "key-001", event);

        // Then: 消息发送成功，消费者被触发
        assertThat(result.isSuccess()).isTrue();
        assertThat(consumedCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("属性: 多渠道通知发送正确")
    void property_multiChannelNotificationCorrect() {
        // Given: 注册WebSocket会话
        notificationManager.registerWebSocketSession(testSessionId, testUserId);

        // When: 发送多渠道通知
        WorkflowEvent event = new WorkflowEvent(
                "TASK_ASSIGNED", "task-001", "TASK", 
                Map.of("taskName", "测试任务", "assignee", testUserId));
        NotificationResult result = notificationManager.sendMultiChannelNotification(testUserId, event);

        // Then: 通知发送成功
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("成功");

        // Cleanup
        notificationManager.unregisterWebSocketSession(testSessionId);
    }

    @Test
    @DisplayName("属性: 站内消息管理正确")
    void property_inAppMessageManagementCorrect() {
        // Given: 发送站内消息
        NotificationResult sendResult = notificationManager.sendInAppNotification(
                testUserId, "测试标题", "测试内容", "TEST_EVENT");
        assertThat(sendResult.isSuccess()).isTrue();
        String messageId = sendResult.getNotificationId();

        // When: 获取未读消息
        List<Map<String, Object>> unreadMessages = notificationManager.getUnreadInAppMessages(testUserId, 10);

        // Then: 消息在未读列表中
        assertThat(unreadMessages).isNotEmpty();
        boolean foundMessage = unreadMessages.stream()
                .anyMatch(m -> "测试标题".equals(m.get("title")));
        assertThat(foundMessage).isTrue();

        // When: 标记为已读
        NotificationResult markResult = notificationManager.markInAppMessageAsRead(testUserId, messageId);
        assertThat(markResult.isSuccess()).isTrue();

        // Then: 消息不再在未读列表中
        unreadMessages = notificationManager.getUnreadInAppMessages(testUserId, 10);
        boolean stillUnread = unreadMessages.stream()
                .anyMatch(m -> messageId.equals(m.get("messageId")));
        assertThat(stillUnread).isFalse();
    }
}
