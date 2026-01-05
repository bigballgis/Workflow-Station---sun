package com.platform.messaging.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Handler for dead letter queue messages.
 * Validates: Requirements 6.6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterHandler {
    
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
            topics = {
                    KafkaTopics.PROCESS_EVENTS_DLT,
                    KafkaTopics.TASK_EVENTS_DLT,
                    KafkaTopics.PERMISSION_EVENTS_DLT,
                    KafkaTopics.DEPLOYMENT_EVENTS_DLT
            },
            groupId = "platform-dlt-handler"
    )
    public void handleDeadLetter(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.error("Received dead letter message from topic {}: key={}, value={}",
                record.topic(), record.key(), record.value());
        
        try {
            // Log the failed message for manual investigation
            logFailedMessage(record);
            
            // Acknowledge the message to prevent reprocessing
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error handling dead letter message: {}", e.getMessage(), e);
            // Still acknowledge to prevent infinite loop
            ack.acknowledge();
        }
    }
    
    private void logFailedMessage(ConsumerRecord<String, String> record) {
        // In production, this would store the message in a database
        // for manual review and potential reprocessing
        log.warn("Dead letter message details: topic={}, partition={}, offset={}, timestamp={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.timestamp());
    }
}
