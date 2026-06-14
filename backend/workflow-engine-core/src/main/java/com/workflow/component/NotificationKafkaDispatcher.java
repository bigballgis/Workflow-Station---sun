package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.workflow.component.NotificationManagerComponent.KafkaMessage;
import com.workflow.component.NotificationManagerComponent.WorkflowEvent;
import com.workflow.dto.response.NotificationResult;
import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Kafka message-queue integration for the notification subsystem.
 *
 * <p>Extracted from {@link NotificationManagerComponent}; behaviour (topic naming, payload,
 * Redis-simulated queue, consumer dispatch) is preserved verbatim. Stateless: reads/writes the
 * shared {@link NotificationContext} passed on each call.</p>
 */
@Slf4j
@Component
class NotificationKafkaDispatcher {

    /**
     * Send a Kafka message
     */
    NotificationResult sendKafkaMessage(NotificationContext ctx, String topic, String key, WorkflowEvent event) {
        log.info("Sending Kafka message: topic={}, key={}, eventType={}", topic, key, event.getEventType());

        try {
            KafkaMessage message = new KafkaMessage(NotificationContext.KAFKA_TOPIC_PREFIX + topic, key, event);

            // Simulated Kafka sending (should use KafkaTemplate in production)
            String messageJson = ctx.objectMapper.writeValueAsString(message);

            // Store in Redis to simulate Kafka queue
            String queueKey = NotificationContext.NOTIFICATION_PREFIX + "kafka:" + topic;
            ctx.stringRedisTemplate.opsForList().rightPush(queueKey, messageJson);
            ctx.stringRedisTemplate.expire(queueKey, Duration.ofDays(7));

            // Trigger consumers
            triggerKafkaConsumers(ctx, topic, event);

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
     */
    NotificationResult registerKafkaConsumer(NotificationContext ctx, String topic, Consumer<WorkflowEvent> consumer) {
        log.info("Registering Kafka consumer: topic={}", topic);

        ctx.kafkaConsumers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(consumer);

        return NotificationResult.builder()
                .success(true)
                .message("Kafka consumer registered successfully")
                .build();
    }

    /**
     * Trigger Kafka consumers
     */
    private void triggerKafkaConsumers(NotificationContext ctx, String topic, WorkflowEvent event) {
        List<Consumer<WorkflowEvent>> consumers = ctx.kafkaConsumers.get(topic);
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
}
