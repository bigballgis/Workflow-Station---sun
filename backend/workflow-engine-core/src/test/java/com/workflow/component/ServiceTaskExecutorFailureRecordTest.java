package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ServiceTaskExecutionRecord;
import com.workflow.repository.ServiceTaskExecutionRecordRepository;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AP 服务任务失败时的记录留痕。
 *
 * <p>该 delegate 跑在 Flowable {@code complete()} 事务里，失败必须抛异常让流程别往下走——
 * 于是同一个连接上写的执行记录也会被一起回滚。历史上 {@code wf_ap_execution_record} 里清一色
 * SUCCESS 就是这么来的：每次失败都把自己的证据抹掉，偏偏那才是最需要留痕的时候。
 * 这里锁住的是「失败行必须走独立事务，且是新插入的行（id 为空）」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceTaskExecutor — 失败记录独立事务留痕")
class ServiceTaskExecutorFailureRecordTest {

    private static final String FLOW_ID = "3FykxGkq8EbTre22fOsaj";

    @Mock
    private ServiceTaskExecutionRecordRepository recordRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private DelegateExecution execution;

    private ServiceTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ServiceTaskExecutor(recordRepository, restTemplate, new ObjectMapper(), transactionManager);
        ReflectionTestUtils.setField(executor, "webhookBaseUrl", "http://activepieces:80");
        ReflectionTestUtils.setField(executor, "fileServiceBaseUrl", "http://developer-workstation:8080");

        when(recordRepository.save(any(ServiceTaskExecutionRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        when(execution.getId()).thenReturn("exec-1");
        when(execution.getProcessInstanceId()).thenReturn("proc-1");
        when(execution.getVariables()).thenReturn(new HashMap<>(Map.of("file", "/api/v1/upload/files/x.csv")));
        when(execution.getCurrentFlowElement()).thenReturn(serviceTaskWithFlowId());

        // 204 = AP produced no flow response -> the delegate must fail the task.
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    private static ServiceTask serviceTaskWithFlowId() {
        ExtensionElement property = new ExtensionElement();
        property.setName("property");
        property.addAttribute(attribute("name", "ap:flowId"));
        property.addAttribute(attribute("value", FLOW_ID));

        ExtensionElement properties = new ExtensionElement();
        properties.setName("properties");
        properties.addChildElement(property);

        ServiceTask task = new ServiceTask();
        task.setExtensionElements(new HashMap<>(Map.of("properties", List.of(properties))));
        return task;
    }

    private static ExtensionAttribute attribute(String name, String value) {
        ExtensionAttribute a = new ExtensionAttribute(name);
        a.setValue(value);
        return a;
    }

    @Test
    @DisplayName("失败行走 REQUIRES_NEW，不会随 Flowable 事务回滚")
    void failureRecordIsWrittenInItsOwnTransaction() {
        assertThatThrownBy(() -> executor.execute(execution)).isInstanceOf(RuntimeException.class);

        ArgumentCaptor<TransactionDefinition> def = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(def.capture());
        assertThat(def.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @DisplayName("失败行是新插入的行(id 为空)——调用方那条 PENDING 也会被回滚，不能 merge 到它上面")
    void failureRecordIsAFreshRowNotAMergeOntoTheRolledBackOne() {
        assertThatThrownBy(() -> executor.execute(execution)).isInstanceOf(RuntimeException.class);

        ArgumentCaptor<ServiceTaskExecutionRecord> saved =
                ArgumentCaptor.forClass(ServiceTaskExecutionRecord.class);
        verify(recordRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());

        ServiceTaskExecutionRecord failed = saved.getAllValues().stream()
                .filter(r -> "FAILED".equals(r.getStatus()))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no FAILED record was persisted"));

        assertThat(failed.getId()).isNull();
        assertThat(failed.getApFlowId()).isEqualTo(FLOW_ID);
        assertThat(failed.getProcessInstanceId()).isEqualTo("proc-1");
        assertThat(failed.getErrorMessage()).contains("no response", "204");
        assertThat(failed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("留痕失败不能盖掉真正的 AP 错误")
    void auditWriteFailureDoesNotMaskTheApError() {
        when(transactionManager.getTransaction(any())).thenThrow(new IllegalStateException("no tx manager"));

        assertThatThrownBy(() -> executor.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AP webhook");
    }

    @Test
    @DisplayName("成功路径不写失败行，也不额外开事务")
    void successPathDoesNotOpenTheFailureTransaction() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("variables", Map.of("rowCount", 4))));

        executor.execute(execution);

        verify(transactionManager, org.mockito.Mockito.never()).getTransaction(any());
    }
}
