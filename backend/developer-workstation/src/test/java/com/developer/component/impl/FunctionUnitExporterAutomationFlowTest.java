package com.developer.component.impl;

import com.developer.client.AdminCenterAutomationFlowClient;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.DeveloperBusinessException;
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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * FU 导出包必须带上 service task 引用的 Automation flow——否则目标环境拿到的
 * {@code ap:flowId} 解析不到任何东西（本次修复的 bug）。
 */
@ExtendWith(MockitoExtension.class)
class FunctionUnitExporterAutomationFlowTest {

    private static final String FLOW_ID = "hxJ2K1flowAbc";

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="p1">
                <serviceTask id="t1">
                  <extensionElements>
                    <flowable:properties>
                      <flowable:property name="ap:flowId" value="%s" />
                    </flowable:properties>
                  </extensionElements>
                </serviceTask>
              </process>
            </definitions>
            """.formatted(FLOW_ID);

    private static final byte[] FLOW_PAYLOAD = ("""
            {"hermesFlowExport":1,"flowKey":"%s","displayName":"Notify","trigger":{}}
            """.formatted(FLOW_ID)).getBytes(StandardCharsets.UTF_8);

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private ActionDefinitionRepository actionDefinitionRepository;
    @Mock private DecisionDefinitionRepository decisionDefinitionRepository;
    @Mock private FormStageBindingRepository formStageBindingRepository;
    @Mock private TableRelationRepository tableRelationRepository;
    @Mock private FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;
    @Mock private AdminCenterAutomationFlowClient automationFlowClient;

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
                automationFlowClient,
                new ObjectMapper());
    }

    @Test
    void packagesReferencedAutomationFlowAndRegistersItInManifest() throws Exception {
        when(automationFlowClient.exportFlow(FLOW_ID)).thenReturn(Optional.of(FLOW_PAYLOAD));

        Map<String, byte[]> entries = unzip(exporter.exportFunctionUnit(1L));

        assertTrue(entries.containsKey("automation-flows/flow_0.json"),
                "exported package must carry the referenced automation flow");
        assertEquals(new String(FLOW_PAYLOAD, StandardCharsets.UTF_8),
                new String(entries.get("automation-flows/flow_0.json"), StandardCharsets.UTF_8));

        Map<?, ?> manifest = new ObjectMapper().readValue(entries.get("manifest.json"), Map.class);
        Map<?, ?> components = (Map<?, ?>) manifest.get("components");
        assertEquals(List.of("automation-flows/flow_0.json"), components.get("automationFlows"));
    }

    @Test
    void failsLoudlyWhenReferencedFlowNoLongerExists() {
        when(automationFlowClient.exportFlow(FLOW_ID)).thenReturn(Optional.empty());

        DeveloperBusinessException e = assertThrows(DeveloperBusinessException.class,
                () -> exporter.exportFunctionUnit(1L));
        assertEquals("AP_FLOW_EXPORT_UNRESOLVED", e.getErrorCode());
    }

    @Test
    void skipsTheFlowChannelWhenNoServiceTaskReferencesAFlow() throws Exception {
        FunctionUnit noFlows = FunctionUnit.builder()
                .name("Order")
                .code("order-20260727-aaaaaa")
                .currentVersion("1.0.0")
                .processDefinition(ProcessDefinition.builder()
                        .bpmnXml("<definitions><process id=\"p1\"><userTask id=\"u1\" /></process></definitions>")
                        .build())
                .build();
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(noFlows));

        Map<String, byte[]> entries = unzip(exporter.exportFunctionUnit(1L));

        assertFalse(entries.keySet().stream().anyMatch(name -> name.startsWith("automation-flows/")));
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
