package com.developer.component.impl;

import com.developer.client.AdminCenterAutomationFlowClient;
import com.developer.entity.FunctionUnit;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.DecisionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormStageBindingRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.repository.TableRelationRepository;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.util.DeveloperWorkstationSequenceSynchronizer;
import com.developer.validation.DmnXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-B13 导入前校验：BPMN 引用的 Automation flow 必须在本环境可解析，否则导入显式失败
 * （而不是落一个 service task 空转的半残 FU）；旧包携带的 automation-flows/ 条目被忽略
 * 而非还原（FR-B12 解耦后 flow 迁移走独立通道）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FunctionUnitImporter — FR-B13 flow 引用校验")
class FunctionUnitImporterFlowRefValidationTest {

    private static final String FLOW_KEY = "invoice-sync";

    private static final String BPMN_WITH_FLOW_REF = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
              xmlns:flowable="http://flowable.org/bpmn"
              xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI">
              <bpmn:process id="Process_flow_ref" isExecutable="true">
                <bpmn:serviceTask id="ApTask_1">
                  <bpmn:extensionElements>
                    <flowable:properties>
                      <flowable:property name="ap:flowKey" value="%s" />
                    </flowable:properties>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_flow_ref" />
              </bpmndi:BPMNDiagram>
            </bpmn:definitions>
            """.formatted(FLOW_KEY);

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private ProcessDefinitionRepository processDefinitionRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private ActionDefinitionRepository actionDefinitionRepository;
    @Mock private DecisionDefinitionRepository decisionDefinitionRepository;
    @Mock private DmnXmlParser dmnXmlParser;
    @Mock private AdminCenterAutomationFlowClient automationFlowClient;

    private ExportImportComponentImpl impl;

    @BeforeEach
    void setUp() {
        impl = ExportImportTestComponents.build(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                mock(FormTableBindingRepository.class),
                mock(FormStageBindingRepository.class),
                mock(TableRelationRepository.class),
                dmnXmlParser,
                mock(FunctionUnitWorkspaceAccessService.class),
                mock(FunctionUnitDevGroupAssignmentRepository.class),
                mock(jakarta.persistence.EntityManager.class),
                new ObjectMapper(),
                mock(DeveloperWorkstationSequenceSynchronizer.class),
                automationFlowClient);

        when(functionUnitRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(functionUnitRepository.existsByCode(any())).thenReturn(false);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
    }

    private MockMultipartFile packageWith(String... entryNameAndContentPairs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < entryNameAndContentPairs.length; i += 2) {
                zos.putNextEntry(new ZipEntry(entryNameAndContentPairs[i]));
                zos.write(entryNameAndContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", "fu.zip", "application/zip", baos.toByteArray());
    }

    @Test
    @DisplayName("引用解析不到 → AP_FLOW_REF_MISSING，且不落任何 FU 内容")
    void missingFlowRefAbortsBeforeAnyContentIsWritten() throws Exception {
        when(automationFlowClient.resolveFlow(FLOW_KEY)).thenReturn(Optional.empty());

        MockMultipartFile file = packageWith(
                "manifest.json", "{\"name\":\"FlowFU\",\"code\":\"flow-fu\",\"version\":\"1.0.0\"}",
                "process/process.bpmn", BPMN_WITH_FLOW_REF);

        DeveloperBusinessException e = assertThrows(DeveloperBusinessException.class,
                () -> impl.importFunctionUnit(file, null));

        assertEquals("AP_FLOW_REF_MISSING", e.getErrorCode());
        assertTrue(e.getMessage().contains(FLOW_KEY), "缺失清单必须点名引用");
        verify(functionUnitRepository, never()).save(any());
        verify(processDefinitionRepository, never()).save(any());
    }

    @Test
    @DisplayName("解析通道抛错（未配置/不可达）→ 显式失败，不静默跳过校验")
    void unavailableChannelFailsLoudly() throws Exception {
        when(automationFlowClient.resolveFlow(FLOW_KEY)).thenThrow(
                new DeveloperBusinessException("AP_FLOW_CHANNEL_UNAVAILABLE",
                        "Automation flow resolution channel is not configured"));

        MockMultipartFile file = packageWith(
                "manifest.json", "{\"name\":\"FlowFU\",\"code\":\"flow-fu\",\"version\":\"1.0.0\"}",
                "process/process.bpmn", BPMN_WITH_FLOW_REF);

        DeveloperBusinessException e = assertThrows(DeveloperBusinessException.class,
                () -> impl.importFunctionUnit(file, null));

        assertEquals("AP_FLOW_CHANNEL_UNAVAILABLE", e.getErrorCode());
        verify(functionUnitRepository, never()).save(any());
    }

    @Test
    @DisplayName("引用可解析 → 导入照常成功")
    void resolvableRefImportsNormally() throws Exception {
        when(automationFlowClient.resolveFlow(FLOW_KEY)).thenReturn(Optional.of("local-flow-id"));

        MockMultipartFile file = packageWith(
                "manifest.json", "{\"name\":\"FlowFU\",\"code\":\"flow-fu\",\"version\":\"1.0.0\"}",
                "process/process.bpmn", BPMN_WITH_FLOW_REF);

        Map<String, Object> result = impl.importFunctionUnit(file, null);

        assertEquals("SUCCESS", result.get("status"));
        verify(processDefinitionRepository).save(any());
    }

    @Test
    @DisplayName("旧包的 automation-flows/ 条目被忽略：不再还原 flow，导入不因此失败")
    void legacyPackagedFlowEntriesAreIgnored() throws Exception {
        MockMultipartFile file = packageWith(
                "manifest.json", "{\"name\":\"LegacyFU\",\"code\":\"legacy-fu\",\"version\":\"1.0.0\"}",
                "automation-flows/flow_0.json", "{\"hermesFlowExport\":1,\"flowKey\":\"k\"}");

        Map<String, Object> result = impl.importFunctionUnit(file, null);

        assertEquals("SUCCESS", result.get("status"));
        // 没有 BPMN 引用 → 不该有任何解析调用；更不允许还原（方法已删，编译期即保证）
        verify(automationFlowClient, never()).resolveFlow(anyString());
    }
}
