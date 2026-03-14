package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.config.KafkaTopics;
import com.platform.messaging.event.NotificationEvent;
import com.portal.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for notification events.
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.NOTIFICATION_EVENTS,
            groupId = "user-portal-notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("收到通知事件: topic={}, key={}, offset={}", record.topic(), record.key(), record.offset());

        try {
            NotificationEvent event = objectMapper.readValue(record.value(), NotificationEvent.class);

            // 校验必填字段
            if (event.getTargetUserId() == null || event.getTitle() == null || event.getNotificationType() == null) {
                log.warn("通知事件缺少必填字段，跳过处理: {}", record.value());
                ack.acknowledge();
                return;
            }

            notificationService.createFromEvent(event);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("处理通知事件失败: offset={}, error={}", record.offset(), e.getMessage(), e);
            throw new RuntimeException("处理通知事件失败", e);
        }
    }
}
