package com.portal.component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.messaging.event.NotificationEvent;
import com.portal.entity.Notification;
import com.portal.enums.NotificationType;
import com.portal.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationKafkaConsumer.
 * Validates: Requirements 1.1, 1.3, 1.4
 */
@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        consumer = new NotificationKafkaConsumer(notificationService, objectMapper);
    }

    // --- Normal consumption flow (Requirement 1.1, 1.3) ---

    @Test
    @DisplayName("正常消费: 有效JSON -> 反序列化 -> createFromEvent -> ack")
    void consume_validEvent_shouldDeserializeAndCreateNotification() {
        String json = """
                {"eventId":"evt-001","targetUserId":"user-123","notificationType":"TASK",
                 "title":"新任务分配","content":"您有一个新任务需要处理","link":"/portal/tasks/456"}
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "platform.notification.events", 0, 0L, "user-123", json);

        Notification saved = Notification.builder()
                .id(1L).userId("user-123").type(NotificationType.TASK)
                .title("新任务分配").content("您有一个新任务需要处理")
                .link("/portal/tasks/456").isRead(false).build();
        when(notificationService.createFromEvent(any(NotificationEvent.class))).thenReturn(saved);

        consumer.consume(record, acknowledgment);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationService).createFromEvent(captor.capture());
        NotificationEvent captured = captor.getValue();
        assertThat(captured.getTargetUserId()).isEqualTo("user-123");
        assertThat(captured.getNotificationType()).isEqualTo("TASK");
        assertThat(captured.getTitle()).isEqualTo("新任务分配");
        assertThat(captured.getContent()).isEqualTo("您有一个新任务需要处理");
        assertThat(captured.getLink()).isEqualTo("/portal/tasks/456");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("正常消费: 不含可选字段link的事件也应成功处理")
    void consume_eventWithoutLink_shouldSucceed() {
        String json = """
                {"eventId":"evt-002","targetUserId":"user-456","notificationType":"SYSTEM",
                 "title":"系统公告","content":"系统将于今晚维护"}
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "platform.notification.events", 0, 1L, "user-456", json);
        when(notificationService.createFromEvent(any(NotificationEvent.class))).thenReturn(null);

        consumer.consume(record, acknowledgment);

        verify(notificationService).createFromEvent(any(NotificationEvent.class));
        verify(acknowledgment).acknowledge();
    }

    // --- Deserialization failure (Requirement 1.4) ---

    @Test
    @DisplayName("反序列化失败: 无效JSON -> 抛出异常（触发Kafka重试）")
    void consume_invalidJson_shouldThrowException() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "platform.notification.events", 0, 2L, "user-789", "this is not valid json {{{");

        assertThatThrownBy(() -> consumer.consume(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process notification event");

        verify(notificationService, never()).createFromEvent(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("反序列化失败: 空消息体 -> 抛出异常")
    void consume_emptyBody_shouldThrowException() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "platform.notification.events", 0, 3L, "user-000", "");

        assertThatThrownBy(() -> consumer.consume(record, acknowledgment))
                .isInstanceOf(RuntimeException.class);

        verify(notificationService, never()).createFromEvent(any());
        verify(acknowledgment, never()).acknowledge();
    }

    // --- Missing required fields (Requirement 1.1) ---

    @Test
    @DisplayName("缺少必填字段: targetUserId为null -> 跳过处理并ack")
    void consume_missingTargetUserId_shouldSkipAndAck() {
        String json = """
                {"eventId":"evt-003","notificationType":"TASK","title":"任务通知","content":"内容"}
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "platform.notification.events", 0, 4L, null, json);

        consumer.consume(record, acknowledgment);

        verify(notificationService, never()).createFromEvent(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("缺少必填字段: title为null -> 跳过处理并ack")
    void consume_missingTitle_shouldSkipAndAck() {
        String json = """
                {"eventId":"evt-004","targetUserId":"user-123","notificationType":"TASK","content":"内容"}
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "platform.notification.events", 0, 5L, "user-123", json);

        consumer.consume(record, acknowledgment);

        verify(notificationService, never()).createFromEvent(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("缺少必填字段: notificationType为null -> 跳过处理并ack")
    void consume_missingNotificationType_shouldSkipAndAck() {
        String json = """
                {"eventId":"evt-005","targetUserId":"user-123","title":"任务通知","content":"内容"}
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "platform.notification.events", 0, 6L, "user-123", json);

        consumer.consume(record, acknowledgment);

        verify(notificationService, never()).createFromEvent(any());
        verify(acknowledgment).acknowledge();
    }

    // --- Service exception propagation (Requirement 1.4) ---

    @Test
    @DisplayName("服务层异常: createFromEvent抛出异常 -> 异常传播（触发Kafka重试）")
    void consume_serviceThrowsException_shouldPropagateForRetry() {
        String json = """
                {"eventId":"evt-006","targetUserId":"user-123","notificationType":"TASK",
                 "title":"任务通知","content":"内容"}
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "platform.notification.events", 0, 7L, "user-123", json);

        when(notificationService.createFromEvent(any(NotificationEvent.class)))
                .thenThrow(new RuntimeException("数据库连接失败"));

        assertThatThrownBy(() -> consumer.consume(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process notification event");

        verify(notificationService).createFromEvent(any(NotificationEvent.class));
        verify(acknowledgment, never()).acknowledge();
    }
}
