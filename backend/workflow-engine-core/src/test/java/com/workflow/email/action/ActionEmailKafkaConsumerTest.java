package com.workflow.email.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.event.ActionEmailRequestedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionEmailKafkaConsumerTest {

    @Mock
    private ActionEmailDeliveryRepository deliveryRepository;
    @Mock
    private Acknowledgment ack;

    @Test
    void consume_insertsPendingAndAcks() throws Exception {
        ActionEmailKafkaConsumer consumer = new ActionEmailKafkaConsumer(deliveryRepository, new ObjectMapper());
        ActionEmailRequestedEvent event = ActionEmailRequestedEvent.builder()
                .eventId("e1")
                .processInstanceId("pi-1")
                .actionId("7")
                .postEmail(Map.of("enabled", true))
                .build();
        String json = new ObjectMapper().writeValueAsString(event);
        when(deliveryRepository.insertPending("e1", json, "pi-1", "7")).thenReturn(true);
        consumer.consume(new ConsumerRecord<>("platform.action-email.requests", 0, 0L, "pi-1", json), ack);
        verify(deliveryRepository).insertPending("e1", json, "pi-1", "7");
        verify(ack).acknowledge();
    }

    @Test
    void consume_duplicateStillAcks() throws Exception {
        ActionEmailKafkaConsumer consumer = new ActionEmailKafkaConsumer(deliveryRepository, new ObjectMapper());
        ActionEmailRequestedEvent event = ActionEmailRequestedEvent.builder()
                .eventId("e1")
                .processInstanceId("pi-1")
                .actionId("7")
                .postEmail(Map.of("enabled", true))
                .build();
        String json = new ObjectMapper().writeValueAsString(event);
        when(deliveryRepository.insertPending(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        consumer.consume(new ConsumerRecord<>("platform.action-email.requests", 0, 1L, "pi-1", json), ack);
        verify(ack).acknowledge();
    }

    @Test
    void consume_unreadableAcksWithoutInsert() {
        ActionEmailKafkaConsumer consumer = new ActionEmailKafkaConsumer(deliveryRepository, new ObjectMapper());
        consumer.consume(new ConsumerRecord<>("platform.action-email.requests", 0, 2L, "k", "{"), ack);
        verify(deliveryRepository, never()).insertPending(anyString(), anyString(), anyString(), anyString());
        verify(ack).acknowledge();
    }
}
