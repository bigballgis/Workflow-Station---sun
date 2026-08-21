package com.workflow.component;

import com.workflow.exception.WorkflowBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 部署期 AP flow 引用解析（Q7 + FR-C12 fail-fast）。
 *
 * <p>锁住的行为：{@code ap:flowKey} 业务键优先解析并把本环境实际 flowId 写入部署副本的
 * {@code ap:flowId}（无该属性节点则创建）；解析 NOT_FOUND / UNAVAILABLE 一律让部署失败；
 * 唯一豁免是「解析未配置 + 只有 legacy {@code ap:flowId}」的本地 dev 旧路径。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProcessDeploymentManager — AP flow 引用部署期解析")
class ProcessDeploymentManagerApFlowRefTest {

    private static final String RESOLVED_FLOW_ID = "resolved-local-flow-id";

    @Mock
    private ServiceTaskFlowRefResolver resolver;

    private ProcessDeploymentManager manager;

    @BeforeEach
    void setUp() {
        manager = new ProcessDeploymentManager();
        ReflectionTestUtils.setField(manager, "serviceTaskFlowRefResolver", resolver);
    }

    private static String bpmnWith(String... properties) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn"
                  targetNamespace="http://flowable.org/test">
                  <process id="Process_Ap" name="Ap" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="ApTask_1" name="Automation">
                      <extensionElements>
                        <flowable:properties>
                          %s
                        </flowable:properties>
                      </extensionElements>
                    </serviceTask>
                    <endEvent id="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="ApTask_1"/>
                    <sequenceFlow id="f2" sourceRef="ApTask_1" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(String.join("\n          ", properties));
    }

    private String normalize(String bpmnXml) {
        try {
            Method normalize = ProcessDeploymentManager.class
                    .getDeclaredMethod("normalizeBpmnXml", String.class);
            normalize.setAccessible(true);
            return (String) normalize.invoke(manager, bpmnXml);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("ap:flowKey 解析成功：本环境 flowId 写入部署副本（属性节点不存在则创建）")
    void resolvedFlowKeyWritesFlowIdPropertyIntoTheDeployedCopy() {
        when(resolver.isConfigured()).thenReturn(true);
        when(resolver.resolve("invoice-sync"))
                .thenReturn(ServiceTaskFlowRefResolver.Resolution.resolved(RESOLVED_FLOW_ID));

        String deployed = normalize(bpmnWith(
                "<flowable:property name=\"ap:flowKey\" value=\"invoice-sync\" />"));

        assertThat(deployed).contains("ap:flowKey", "invoice-sync");
        assertThat(deployed).contains("ap:flowId", RESOLVED_FLOW_ID);
        assertThat(deployed).contains("apTaskExecutor");
    }

    @Test
    @DisplayName("ap:flowKey + 过期 ap:flowId：解析值覆盖旧 flowId")
    void resolvedFlowKeyOverwritesStaleFlowId() {
        when(resolver.isConfigured()).thenReturn(true);
        when(resolver.resolve("invoice-sync"))
                .thenReturn(ServiceTaskFlowRefResolver.Resolution.resolved(RESOLVED_FLOW_ID));

        String deployed = normalize(bpmnWith(
                "<flowable:property name=\"ap:flowKey\" value=\"invoice-sync\" />",
                "<flowable:property name=\"ap:flowId\" value=\"source-env-stale-id\" />"));

        assertThat(deployed).contains(RESOLVED_FLOW_ID);
        assertThat(deployed).doesNotContain("source-env-stale-id");
    }

    @Test
    @DisplayName("NOT_FOUND → 部署失败（FR-C12），错误信息带引用与指引")
    void notFoundFailsTheDeployment() {
        when(resolver.isConfigured()).thenReturn(true);
        when(resolver.resolve(anyString()))
                .thenReturn(ServiceTaskFlowRefResolver.Resolution.notFound());

        assertThatThrownBy(() -> normalize(bpmnWith(
                "<flowable:property name=\"ap:flowKey\" value=\"invoice-sync\" />")))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("invoice-sync")
                .hasMessageContaining("FR-C12");
    }

    @Test
    @DisplayName("配置了 resolver 但 UNAVAILABLE → 部署失败，不再静默保留原引用")
    void unavailableWithConfiguredResolverFailsTheDeployment() {
        when(resolver.isConfigured()).thenReturn(true);
        when(resolver.resolve(anyString()))
                .thenReturn(ServiceTaskFlowRefResolver.Resolution.unavailable());

        assertThatThrownBy(() -> normalize(bpmnWith(
                "<flowable:property name=\"ap:flowId\" value=\"legacy-id\" />")))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("legacy-id");
    }

    @Test
    @DisplayName("出现 ap:flowKey 时解析不可用（未配置）也必须失败")
    void flowKeyWithUnconfiguredResolverFails() {
        when(resolver.isConfigured()).thenReturn(false);
        when(resolver.resolve(anyString()))
                .thenReturn(ServiceTaskFlowRefResolver.Resolution.unavailable());

        assertThatThrownBy(() -> normalize(bpmnWith(
                "<flowable:property name=\"ap:flowKey\" value=\"invoice-sync\" />")))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("兼容豁免：解析未配置 + 只有 legacy ap:flowId → warn 放行，保留原引用")
    void legacyFlowIdOnlyWithUnconfiguredResolverStillDeploys() {
        when(resolver.isConfigured()).thenReturn(false);
        when(resolver.resolve(anyString()))
                .thenReturn(ServiceTaskFlowRefResolver.Resolution.unavailable());

        String deployed = normalize(bpmnWith(
                "<flowable:property name=\"ap:flowId\" value=\"legacy-local-id\" />"));

        assertThat(deployed).contains("legacy-local-id");
        assertThat(deployed).contains("apTaskExecutor");
    }
}
