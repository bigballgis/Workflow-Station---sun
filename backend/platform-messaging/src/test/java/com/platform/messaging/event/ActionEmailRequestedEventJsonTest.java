package com.platform.messaging.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionEmailRequestedEventJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialize_omitsTopic() throws Exception {
        ActionEmailRequestedEvent event = ActionEmailRequestedEvent.builder()
                .eventId("e1")
                .processInstanceId("pi-1")
                .actionId("7")
                .postEmail(Map.of("enabled", true))
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertThat(json.has("topic")).isFalse();
        assertThat(json.path("eventId").asText()).isEqualTo("e1");
        assertThat(json.path("processInstanceId").asText()).isEqualTo("pi-1");
        assertThat(event.getTopic()).isEqualTo(ActionEmailRequestedEvent.TOPIC);
    }

    @Test
    void deserialize_ignoresUnknownTopic() throws Exception {
        String json = """
                {"eventId":"e1","processInstanceId":"pi-1","actionId":"7",\
                "postEmail":{"enabled":true},"topic":"platform.action-email.requests"}
                """;

        ActionEmailRequestedEvent event = objectMapper.readValue(json, ActionEmailRequestedEvent.class);

        assertThat(event.getEventId()).isEqualTo("e1");
        assertThat(event.getProcessInstanceId()).isEqualTo("pi-1");
        assertThat(event.getActionId()).isEqualTo("7");
        assertThat(event.getTopic()).isEqualTo(ActionEmailRequestedEvent.TOPIC);
    }
}
