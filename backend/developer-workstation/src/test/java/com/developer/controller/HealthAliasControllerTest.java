package com.developer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.SimpleHttpCodeStatusMapper;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The probe aliases answer from the actuator health groups in-process, so the answer does not depend on the Host and
 * port the probe used to reach the service (out-of-scope list row 49: through the host's mapped port it was 503).
 */
class HealthAliasControllerTest {

    private final HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
    private final HealthAliasController controller =
            new HealthAliasController(healthEndpoint, new SimpleHttpCodeStatusMapper());

    @Test
    void liveIsTheLiveGroupStatus() {
        when(healthEndpoint.healthForPath("live")).thenReturn(Health.up().build());

        ResponseEntity<Map<String, String>> response = controller.live();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of("status", "UP"));
    }

    @Test
    void aDownReadyGroupIs503() {
        when(healthEndpoint.healthForPath("ready")).thenReturn(Health.down().build());

        ResponseEntity<Map<String, String>> response = controller.ready();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isEqualTo(Map.of("status", "DOWN"));
    }

    @Test
    void outOfServiceIs503LikeTheActuator() {
        when(healthEndpoint.healthForPath("ready")).thenReturn(Health.status(Status.OUT_OF_SERVICE).build());

        assertThat(controller.ready().getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void aMissingGroupIsAConfigurationErrorNotAHealthyAnswer() {
        assertThatThrownBy(controller::live).isInstanceOf(IllegalStateException.class).hasMessageContaining("live");
    }
}
