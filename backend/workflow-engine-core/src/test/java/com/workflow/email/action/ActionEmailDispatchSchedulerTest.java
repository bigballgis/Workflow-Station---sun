package com.workflow.email.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.event.ActionEmailRequestedEvent;
import com.workflow.service.ConfiguredEmailSender;
import com.workflow.service.EmailDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionEmailDispatchSchedulerTest {

    @Mock
    private ActionEmailDeliveryRepository deliveryRepository;
    @Mock
    private ActionEmailVariableLoader variableLoader;
    @Mock
    private ConfiguredEmailSender configuredEmailSender;

    private ActionEmailDispatchScheduler scheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        scheduler = new ActionEmailDispatchScheduler(
                deliveryRepository, variableLoader, configuredEmailSender, objectMapper);
    }

    @Test
    void deliverOne_marksSent() throws Exception {
        ActionEmailRequestedEvent event = sampleEvent();
        when(variableLoader.load(any())).thenReturn(Map.of("functionUnitCode", "fu-1"));
        when(configuredEmailSender.send(anyString(), any(), any())).thenReturn("a@b.com");
        scheduler.deliverOne(new ActionEmailDeliveryRepository.PendingDelivery(
                "e1", objectMapper.writeValueAsString(event), 0));
        verify(deliveryRepository).markSent("e1");
    }

    @Test
    void deliverOne_reschedulesTransient() throws Exception {
        ActionEmailRequestedEvent event = sampleEvent();
        when(variableLoader.load(any())).thenReturn(Map.of());
        doThrow(EmailDeliveryException.sendFailed("smtp", true, "timeout", "timeout", null))
                .when(configuredEmailSender).send(anyString(), any(), any());
        scheduler.deliverOne(new ActionEmailDeliveryRepository.PendingDelivery(
                "e1", objectMapper.writeValueAsString(event), 0));
        verify(deliveryRepository).reschedule(eq("e1"), eq(30L), anyString());
    }

    @Test
    void deliverOne_failsPermanent() throws Exception {
        ActionEmailRequestedEvent event = sampleEvent();
        when(variableLoader.load(any())).thenReturn(Map.of());
        doThrow(EmailDeliveryException.permanent(EmailDeliveryException.CONFIG_INVALID, "missing"))
                .when(configuredEmailSender).send(anyString(), any(), any());
        scheduler.deliverOne(new ActionEmailDeliveryRepository.PendingDelivery(
                "e1", objectMapper.writeValueAsString(event), 0));
        verify(deliveryRepository).markFailed(eq("e1"), anyString());
    }

    private static ActionEmailRequestedEvent sampleEvent() {
        return ActionEmailRequestedEvent.builder()
                .eventId("e1")
                .processInstanceId("pi-1")
                .actionId("7")
                .postEmail(Map.of("enabled", true, "connectionId", "c", "emailTo", "a@b.com", "emailTemplateId", "1"))
                .build();
    }
}
