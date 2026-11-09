package com.workflow.listener;

import com.platform.common.constant.PlatformConstants;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.HistoryService;
import org.flowable.engine.delegate.event.FlowableProcessEngineEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The completion callback to user-portal carries the shared service token the portal checks. */
class ProcessCompletionListenerServiceTokenTest {

    @Test
    void completionCallbackSendsTheSharedServiceToken() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ProcessCompletionListener listener = new ProcessCompletionListener();
        ReflectionTestUtils.setField(listener, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(listener, "historyService", mock(HistoryService.class));
        ReflectionTestUtils.setField(listener, "userPortalUrl", "http://user-portal:8080");
        ReflectionTestUtils.setField(listener, "internalServiceToken", "svc-secret");
        FlowableProcessEngineEvent event = mock(FlowableProcessEngineEvent.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.PROCESS_COMPLETED);
        when(event.getProcessInstanceId()).thenReturn("pi-1");

        listener.onEvent(event);

        verify(restTemplate, timeout(5000)).postForObject(
                eq("http://user-portal:8080/api/portal/processes/pi-1/complete"),
                argThat(entity -> entity instanceof HttpEntity<?> http
                        && "svc-secret".equals(http.getHeaders().getFirst(PlatformConstants.HEADER_SERVICE_TOKEN))),
                eq(Map.class));
    }
}
