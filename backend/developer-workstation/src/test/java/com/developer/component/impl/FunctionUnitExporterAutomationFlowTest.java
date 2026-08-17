package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.DecisionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormStageBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.repository.TableRelationRepository;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * FR-B12 解耦：FU 导出包<b>不再携带</b> Automation flow 本体。
 *
 * <p>BPMN 里只留可移植引用（{@code ap:flowKey} 业务键 / legacy {@code ap:flowId}）；
 * flow 迁移走独立的 Automation 迁移通道，导入侧只做引用可解析校验（FR-B13）。
 * 这里锁住的是：即便 BPMN 引用了 flow，包里也没有 automation-flows/ 条目、manifest
 * 没有 automationFlows 键，且导出不依赖 flow 通道（通道不可用也能导出）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FunctionUnitExporterAutomationFlowTest {

    private static final String FLOW_KEY = "invoice-sync";
    private static final String FLOW_ID = "hxJ2K1flowAbc";

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="p1">
                <serviceTask id="t1">
                  <extensionElements>
                    <flowable:properties>
                      <flowable:property name="ap:flowKey" value="%s" />
                      <flowable:property name="ap:flowId" value="%s" />
                    </flowable:properties>
                  </extensionElements>
                </serviceTask>
              </process>
            </definitions>
            """.formatted(FLOW_KEY, FLOW_ID);

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private ActionDefinitionRepository actionDefinitionRepository;
    @Mock private DecisionDefinitionRepository decisionDefinitionRepository;
    @Mock private FormStageBindingRepository formStageBindingRepository;
    @Mock private TableRelationRepository tableRelationRepository;
    @Mock private FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;

    private FunctionUnitExporter exporter;

    @BeforeEach
    void setUp() {
        FunctionUnit functionUnit = FunctionUnit.builder()
                .name("Order")
                .code("order-20260727-aaaaaa")
                .currentVersion("1.0.0")
                .processDefinition(ProcessDefinition.builder().bpmnXml(BPMN).build())
                .build();
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));

        exporter = ExportImportTestComponents.exporter(
                functionUnitRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                formStageBindingRepository,
                tableRelationRepository,
                functionUnitWorkspaceAccessService,
                new ObjectMapper());
    }

    @Test
    void packageCarriesNoAutomationFlowBodiesEvenWhenBpmnReferencesFlows() throws Exception {
        Map<String, byte[]> entries = unzip(exporter.exportFunctionUnit(1L));

        assertFalse(entries.keySet().stream().anyMatch(name -> name.startsWith("automation-flows/")),
                "FR-B12: the exported package must not carry automation flow bodies");
        // The BPMN itself (with its portable ap:flowKey reference) is still packaged.
        assertTrue(entries.containsKey("process/process.bpmn"));
        String bpmn = new String(entries.get("process/process.bpmn"));
        assertTrue(bpmn.contains("ap:flowKey"));
    }

    @Test
    void manifestHasNoAutomationFlowsComponent() throws Exception {
        Map<String, byte[]> entries = unzip(exporter.exportFunctionUnit(1L));

        Map<?, ?> manifest = new ObjectMapper().readValue(entries.get("manifest.json"), Map.class);
        Map<?, ?> components = (Map<?, ?>) manifest.get("components");
        assertNotNull(components);
        assertNull(components.get("automationFlows"),
                "FR-B12: the manifest must no longer declare automationFlows");
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        }
        return entries;
    }
}
