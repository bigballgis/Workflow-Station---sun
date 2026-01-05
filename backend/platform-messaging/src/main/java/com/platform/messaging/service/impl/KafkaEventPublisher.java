package com.platform.messaging.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.event.*;
import com.platform.messaging.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka implementation of EventPublisher.
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public CompletableFuture<Void> publishProcessEvent(ProcessEvent event) {
        return publish(event);
    }
    
    @Override
    public CompletableFuture<Void> publishTaskEvent(TaskEvent event) {
        return publish(event);
    }
    
    @Override
    public CompletableFuture<Void> publishPermissionEvent(PermissionEvent event) {
        return publish(event);
    }
    
    @Override
    public CompletableFuture<Void> publishDeploymentEvent(DeploymentEvent event) {
        return publish(event);
    }
    
    @Override
    public CompletableFuture<Void> publish(BaseEvent event) {
        event.initializeDefaults();
        
        String topic = event.getTopic();
        String key = getEventKey(event);
        
        try {
            String payload = objectMapper.writeValueAsString(event);
            
            log.debug("Publishing event to topic {}: {}", topic, event.getEventId());
            
            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(topic, key, payload);
            
            return future.thenAccept(result -> {
                log.info("Event {} published to topic {} partition {} offset {}",
                        event.getEventId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }).exceptionally(ex -> {
                log.error("Failed to publish event {} to topic {}: {}",
                        event.getEventId(), topic, ex.getMessage(), ex);
                throw new RuntimeException("Failed to publish event", ex);
            });
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event {}: {}", event.getEventId(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private String getEventKey(BaseEvent event) {
        if (event instanceof ProcessEvent pe) {
            return pe.getProcessInstanceId();
        } else if (event instanceof TaskEvent te) {
            return te.getTaskId();
        } else if (event instanceof PermissionEvent pe) {
            return pe.getUserId();
        } else if (event instanceof DeploymentEvent de) {
            return de.getDeploymentId();
        }
        return event.getEventId();
    }
}
