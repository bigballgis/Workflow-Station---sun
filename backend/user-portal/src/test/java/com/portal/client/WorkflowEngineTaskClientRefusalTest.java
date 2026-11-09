package com.portal.client;

import com.portal.exception.PortalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An engine refusal (4xx) of a task operation reaches the user with its status and message; only transport failures
 * and engine errors come back empty (which callers report as 500 / unavailable).
 */
class WorkflowEngineTaskClientRefusalTest {

    private static final String HELD = "{\"success\":false,\"message\":\"This task is held by another user\"}";

    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final WorkflowEngineTaskClient client = new WorkflowEngineTaskClient(engine);

    @BeforeEach
    void setUp() {
        when(engine.isAvailable()).thenReturn(true);
        when(engine.engineUrl()).thenReturn("http://engine");
        when(engine.restTemplate()).thenReturn(restTemplate);
        when(engine.parseWorkflowEngineErrorMessage(HELD)).thenReturn("This task is held by another user");
    }

    @Test
    void everyTaskOperationPassesTheEnginesRefusalOn() {
        Map<String, Supplier<Object>> operations = Map.of(
                "/claim", () -> client.claimTask("t1", "u1"),
                "/unclaim", () -> client.unclaimTask("t1", "u1"),
                "/reassign-claim", () -> client.reassignClaim("t1", "u1", "u2"),
                "/transfer", () -> client.transferTask("t1", "u1", "u2", "why"),
                "/delegate", () -> client.delegateTask("t1", Map.of("delegatedBy", "u1")),
                "/return", () -> client.returnTask("t1", "Activity_1", "u1", "why"));
        operations.forEach((path, call) -> {
            engineAnswers(path, new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                    HELD.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

            assertThatThrownBy(call::get).as(path).isInstanceOfSatisfying(PortalException.class, e -> {
                assertThat(e.getCode()).isEqualTo("400");
                assertThat(e.getMessage()).isEqualTo("This task is held by another user");
            });
        });
    }

    @Test
    void aForbiddenOrConflictKeepsItsStatus() {
        engineAnswers("/claim", new HttpClientErrorException(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY,
                HELD.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client.claimTask("t1", "u1"))
                .isInstanceOfSatisfying(PortalException.class, e -> assertThat(e.getCode()).isEqualTo("409"));
    }

    @Test
    void anEngineErrorOrAnUnreachableEngineIsStillNoAnswer() {
        engineAnswers("/claim", new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(client.claimTask("t1", "u1")).isEmpty();

        engineAnswers("/transfer", new ResourceAccessException("connection refused"));
        assertThat(client.transferTask("t1", "u1", "u2", "why")).isEmpty();
    }

    @Test
    void aCompletionRefusalCarriesTheEnginesStatus() {
        engineAnswers("/complete", new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY, HELD.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        Map<String, Object> answer = client.completeTask("t1", "u1", "APPROVE", Map.of()).orElseThrow();

        assertThat(answer).containsEntry("success", false).containsEntry("status", 400)
                .containsEntry("message", "This task is held by another user");
    }

    @SuppressWarnings("unchecked")
    private void engineAnswers(String pathSuffix, RuntimeException failure) {
        when(restTemplate.exchange(eq("http://engine/api/v1/tasks/t1" + pathSuffix), eq(HttpMethod.POST),
                any(HttpEntity.class), any(ParameterizedTypeReference.class))).thenThrow(failure);
    }
}
