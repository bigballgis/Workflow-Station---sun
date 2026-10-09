package com.admin.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How a failed deploy reaches admin-center.
 *
 * <p>The engine answers a definition it refuses with a 4xx whose body explains why. That
 * text used to be dropped, so the designer only ever saw "Deployment failed for this
 * definition" with nothing to act on.
 */
class WorkflowEngineClientDeployTest {

    private RestTemplate restTemplate;
    private WorkflowEngineClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "UP")));
        client = new WorkflowEngineClient(restTemplate);
        ReflectionTestUtils.setField(client, "workflowEngineUrl", "http://engine");
        ReflectionTestUtils.setField(client, "workflowEngineEnabled", true);
    }

    @SuppressWarnings("unchecked")
    private void deployThrows(RuntimeException failure) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class))).thenThrow(failure);
    }

    private static HttpClientErrorException badRequest(String body) {
        return HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                new HttpHeaders(), body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    void surfacesTheEngineReasonWithoutItsGenericPrefix() {
        deployThrows(badRequest("{\"code\":\"BAD_REQUEST\",\"message\":"
                + "\"Failed to deploy process definition: Call activity 'Call_1' targets 'fu-callee', which is not deployed\"}"));

        Optional<WorkflowEngineClient.ProcessDeploymentResult> result =
                client.deployProcess("fu-caller", "<bpmn/>", "Caller");

        assertThat(result).isPresent();
        assertThat(result.get().isSuccess()).isFalse();
        assertThat(result.get().getMessage())
                .isEqualTo("Call activity 'Call_1' targets 'fu-callee', which is not deployed");
    }

    @Test
    void keepsAMessageThatHasNoPrefix() {
        deployThrows(badRequest("{\"message\":\"Process calls itself\"}"));

        assertThat(client.deployProcess("fu-caller", "<bpmn/>", "Caller"))
                .get().extracting(WorkflowEngineClient.ProcessDeploymentResult::getMessage)
                .isEqualTo("Process calls itself");
    }

    @Test
    void fallsBackToEmptyWhenTheErrorBodyHasNoMessage() {
        deployThrows(badRequest("not json"));

        assertThat(client.deployProcess("fu-caller", "<bpmn/>", "Caller")).isEmpty();
    }

    /** 5xx text can carry internal detail, so it never reaches the designer. */
    @Test
    void keepsServerErrorsGeneric() {
        deployThrows(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "boom",
                new HttpHeaders(), "{\"message\":\"NullPointerException at Foo.java:12\"}"
                        .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertThat(client.deployProcess("fu-caller", "<bpmn/>", "Caller")).isEmpty();
    }
}
