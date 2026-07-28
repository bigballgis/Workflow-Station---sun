package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.ServiceTaskActionRequest;
import com.workflow.dto.response.ServiceTaskExecutionResult;
import com.workflow.entity.ServiceTaskExecutionRecord;
import com.workflow.repository.ServiceTaskExecutionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AP sync webhook 结果判定。
 *
 * <p>AP 只在 "Return Response" 步执行时才回写 sync 响应，所以 flow 中途失败时监听器超时、
 * 回落成 {@code 204 No Content} + 空 body。曾经只判 {@code is2xxSuccessful()}，于是失败的
 * automation 被记成 SUCCESS，流程带着空数据继续往下走（Portal 侧表现为导入 0 行且全程无报错）。
 * 这里锁住的就是「204 必须当失败」以及「它不该被重试」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceTaskExecutor — AP sync webhook 无响应判定")
class ServiceTaskExecutorNoResponseTest {

    private static final String FLOW_ID = "3FykxGkq8EbTre22fOsaj";

    @Mock
    private ServiceTaskExecutionRecordRepository recordRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ServiceTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ServiceTaskExecutor(recordRepository, restTemplate, new ObjectMapper(), transactionManager);
        ReflectionTestUtils.setField(executor, "webhookBaseUrl", "http://activepieces:80");
        ReflectionTestUtils.setField(executor, "fileServiceBaseUrl", "http://developer-workstation:8080");
        when(recordRepository.save(any(ServiceTaskExecutionRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubWebhook(ResponseEntity<Map> response) {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(response);
    }

    private ServiceTaskActionRequest actionRequest() {
        ServiceTaskActionRequest request = new ServiceTaskActionRequest();
        request.setApFlowId(FLOW_ID);
        request.setProcessInstanceId("proc-1");
        request.setTaskId("task-1");
        request.setInputData(Map.of("file", "/api/v1/upload/files/x.csv"));
        return request;
    }

    @Test
    @DisplayName("204 判为失败：flow 没跑到 Return Response，不能当成空结果的成功")
    void noContentIsFailure() {
        stubWebhook(ResponseEntity.status(HttpStatus.NO_CONTENT).build());

        ServiceTaskExecutionResult result = executor.executeSynchronous(actionRequest());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("no response", "Return Response");

        ArgumentCaptor<ServiceTaskExecutionRecord> saved =
                ArgumentCaptor.forClass(ServiceTaskExecutionRecord.class);
        verify(recordRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ServiceTaskExecutionRecord::getStatus)
                .contains("FAILED")
                .doesNotContain("SUCCESS");
    }

    @Test
    @DisplayName("204 不重试：每次尝试都要空等满 AP webhook 超时，重试只会让实例卡更久")
    void noContentIsNotRetried() {
        stubWebhook(ResponseEntity.status(HttpStatus.NO_CONTENT).build());

        executor.executeSynchronous(actionRequest());

        verify(restTemplate, times(1))
                .exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class));
    }

    @Test
    @DisplayName("200 + 正常 body 仍判成功，输出照常回写")
    void okWithBodyStillSucceeds() {
        stubWebhook(ResponseEntity.ok(Map.of("rowCount", 4, "rows", List.of("a", "b", "c", "d"))));

        ServiceTaskExecutionResult result = executor.executeSynchronous(actionRequest());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("rowCount", 4);
    }

    @Test
    @DisplayName("200 + 空 body 仍判成功：那是 flow 主动返回的空结果，与 204 不同")
    void okWithEmptyBodyIsStillSuccess() {
        stubWebhook(ResponseEntity.ok(Map.of()));

        ServiceTaskExecutionResult result = executor.executeSynchronous(actionRequest());

        assertThat(result.isSuccess()).isTrue();
    }
}
