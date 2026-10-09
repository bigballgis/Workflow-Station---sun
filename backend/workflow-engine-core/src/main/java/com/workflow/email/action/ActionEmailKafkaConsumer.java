package com.workflow.email.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.config.KafkaTopics;
import com.platform.messaging.event.ActionEmailRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Records post-action email requests in the delivery ledger; {@link ActionEmailDispatchScheduler}
 * does the SMTP work so the poll thread never blocks on a mail server.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionEmailKafkaConsumer {

    private final ActionEmailDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.ACTION_EMAIL_REQUESTS,
            groupId = "workflow-engine-action-email",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        ActionEmailRequestedEvent event;
        try {
            event = objectMapper.readValue(record.value(), ActionEmailRequestedEvent.class);
        } catch (Exception e) {
            log.error("[ACTION-EMAIL] unreadable event skipped: offset={} error={}", record.offset(), e.getMessage());
            ack.acknowledge();
            return;
        }
        if (!StringUtils.hasText(event.getEventId()) || event.getPostEmail() == null
                || !StringUtils.hasText(event.getProcessInstanceId())) {
            log.error("[ACTION-EMAIL] incomplete event skipped: offset={} eventId={} actionId={}",
                    record.offset(), event.getEventId(), event.getActionId());
            ack.acknowledge();
            return;
        }
        boolean inserted = deliveryRepository.insertPending(
                event.getEventId(), record.value(), event.getProcessInstanceId(), event.getActionId());
        if (!inserted) {
            log.info("[ACTION-EMAIL] duplicate event ignored: eventId={}", event.getEventId());
        }
        ack.acknowledge();
    }
}
