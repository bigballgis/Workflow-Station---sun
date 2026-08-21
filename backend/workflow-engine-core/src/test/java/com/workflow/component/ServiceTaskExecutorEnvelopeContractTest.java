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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 信封契约 v1（FR-C）。
 *
 * <p>入参恒为固定信封 {@code {envelopeVersion, variables(全量流程变量), context(执行上下文)}}，
 * 出参恒取顶层 {@code variables} 对象——没有 per-task mapping，也没有「整体合并」回退。
 * 这里锁住的是：信封结构、ap:flowKey 业务键优先/ap:flowId legacy 回退、契约违例必须
 * 失败留痕且绝不把整个 body 灌进流程变量。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceTaskExecutor — 信封契约 v1")
class ServiceTaskExecutorEnvelopeContractTest {

    private static final String FLOW_KEY = "invoice-sync";
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
        when(execution.getCurrentActivityId()).thenReturn("ServiceTask_1");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("orderNo", "SO-1");
        vars.put("file", "/api/v1/upload/files/x.csv");
        when(execution.getVariables()).thenReturn(vars);
        when(execution.getCurrentFlowElement()).thenReturn(serviceTask(
                Map.of("ap:flowKey", FLOW_KEY, "ap:flowId", FLOW_ID)));
    }

    private static ServiceTask serviceTask(Map<String, String> apProperties) {
        ExtensionElement properties = new ExtensionElement();
        properties.setName("properties");
        apProperties.forEach((name, value) -> {
            ExtensionElement property = new ExtensionElement();
            property.setName("property");
            property.addAttribute(attribute("name", name));
            property.addAttribute(attribute("value", value));
            properties.addChildElement(property);
        });
        ServiceTask task = new ServiceTask();
        task.setExtensionElements(new HashMap<>(Map.of("properties", List.of(properties))));
        return task;
    }

    private static ExtensionAttribute attribute(String name, String value) {
        ExtensionAttribute a = new ExtensionAttribute(name);
        a.setValue(value);
        return a;
    }

    private void stubWebhook(ResponseEntity<Map> response) {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentBody() {
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(String.class), eq(HttpMethod.POST), entity.capture(), eq(Map.class));
        return entity.getValue().getBody();
    }

    @Test
    @DisplayName("入参信封：envelopeVersion=1 + 全量变量 + 完整 context（flowKey 业务键优先）")
    void requestCarriesTheFixedEnvelope() {
        stubWebhook(ResponseEntity.ok(Map.of("variables", Map.of())));

        executor.execute(execution);

        Map<String, Object> body = sentBody();
        assertThat(body).containsEntry("envelopeVersion", 1);

        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) body.get("variables");
        assertThat(variables)
                .containsEntry("orderNo", "SO-1")
                // 相对文件路径在信封内已转绝对地址
                .containsEntry("file", "http://developer-workstation:8080/api/v1/upload/files/x.csv");

        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.get("context");
        assertThat(context)
                .containsEntry("processInstanceId", "proc-1")
                .containsEntry("executionId", "exec-1")
                .containsEntry("activityId", "ServiceTask_1")
                .containsEntry("flowKey", FLOW_KEY)
                .containsEntry("flowId", FLOW_ID);
    }

    @Test
    @DisplayName("legacy BPMN（只有 ap:flowId）：context.flowKey 回退为原 flowId 引用")
    void legacyFlowIdOnlyBpmnStillWorks() {
        when(execution.getCurrentFlowElement()).thenReturn(serviceTask(Map.of("ap:flowId", FLOW_ID)));
        stubWebhook(ResponseEntity.ok(Map.of("variables", Map.of())));

        executor.execute(execution);

        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) sentBody().get("context");
        assertThat(context)
                .containsEntry("flowKey", FLOW_ID)
                .containsEntry("flowId", FLOW_ID);
    }

    @Test
    @DisplayName("出参只回写顶层 variables 对象——绝不整体合并 body")
    void onlyTheVariablesObjectIsWrittenBack() {
        stubWebhook(ResponseEntity.ok(Map.of(
                "variables", Map.of("resultCode", "OK"),
                "somethingElse", "must-not-leak")));

        executor.execute(execution);

        ArgumentCaptor<Map<String, Object>> written = ArgumentCaptor.forClass(Map.class);
        verify(execution).setVariables(written.capture());
        assertThat(written.getValue())
                .containsEntry("resultCode", "OK")
                .doesNotContainKey("somethingElse");
    }

    @Test
    @DisplayName("空 variables 对象合法：成功但不回写任何变量")
    void emptyVariablesObjectWritesNothing() {
        stubWebhook(ResponseEntity.ok(Map.of("variables", Map.of())));

        executor.execute(execution);

        verify(execution, never()).setVariables(anyMap());

        ArgumentCaptor<ServiceTaskExecutionRecord> saved =
                ArgumentCaptor.forClass(ServiceTaskExecutionRecord.class);
        verify(recordRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ServiceTaskExecutionRecord::getStatus)
                .contains("SUCCESS");
    }

    @Test
    @DisplayName("响应缺 variables 键 = 契约违例：抛异常 + FAILED 留痕 + 不回写")
    void missingVariablesKeyFailsWithARecord() {
        stubWebhook(ResponseEntity.ok(Map.of("rowCount", 4)));

        assertThatThrownBy(() -> executor.execute(execution))
                .isInstanceOf(ServiceTaskExecutor.ApFlowContractViolationException.class)
                .hasMessageContaining("variables");

        verify(execution, never()).setVariables(anyMap());

        ArgumentCaptor<ServiceTaskExecutionRecord> saved =
                ArgumentCaptor.forClass(ServiceTaskExecutionRecord.class);
        verify(recordRepository, atLeastOnce()).save(saved.capture());
        ServiceTaskExecutionRecord failed = saved.getAllValues().stream()
                .filter(r -> "FAILED".equals(r.getStatus()))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no FAILED record was persisted"));
        assertThat(failed.getErrorMessage()).contains("envelope contract");
        assertThat(failed.getOutputData()).contains("rowCount");
    }

    @Test
    @DisplayName("variables 不是对象 = 契约违例")
    void nonObjectVariablesFails() {
        stubWebhook(ResponseEntity.ok(Map.of("variables", "not-an-object")));

        assertThatThrownBy(() -> executor.execute(execution))
                .isInstanceOf(ServiceTaskExecutor.ApFlowContractViolationException.class);

        verify(execution, never()).setVariables(anyMap());
    }
}
