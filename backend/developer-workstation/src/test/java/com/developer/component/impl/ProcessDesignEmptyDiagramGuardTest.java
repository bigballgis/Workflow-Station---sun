package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.util.XmlEncodingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 空图护栏：禁止把已存的非空流程整体覆盖成空图（设计器自动保存误触的最后一道防线）。
 *
 * <p>背景：2026-07-31 dev FU 50030 的 Start→serviceTask→End 被 2s 自动保存覆盖成空
 * {@code <bpmn:process/>}；dw_process_definitions 只存当前版本，覆盖即不可恢复。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProcessDesignComponent - empty diagram guard")
class ProcessDesignEmptyDiagramGuardTest {

    private static final long FU_ID = 50030L;

    private static final String NON_EMPTY_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                              id="Definitions_1">
              <bpmn:process id="Process_50030" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" />
                <bpmn:serviceTask id="Activity_1" name="Call AP" />
                <bpmn:endEvent id="EndEvent_1" />
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1" />
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_50030">
                  <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1" />
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            </bpmn:definitions>""";

    /** 画布被清空后 bpmn-js 导出的形状：process / plane 还在，里面什么都没有。 */
    private static final String WIPED_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                              id="Definitions_1">
              <bpmn:process id="Process_50030" isExecutable="true" />
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_50030" />
              </bpmndi:BPMNDiagram>
            </bpmn:definitions>""";

    @Mock
    private ProcessDefinitionRepository processDefinitionRepository;
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    @Mock
    private FormDefinitionRepository formDefinitionRepository;
    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    private ProcessDesignComponentImpl component;

    @BeforeEach
    void setUp() {
        when(functionUnitRepository.findById(FU_ID))
                .thenReturn(Optional.of(FunctionUnit.builder().id(FU_ID).build()));
        when(formDefinitionRepository.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        when(tableDefinitionRepository.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        when(actionDefinitionRepository.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        when(processDefinitionRepository.save(any(ProcessDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        component = new ProcessDesignComponentImpl(
                processDefinitionRepository,
                functionUnitRepository,
                new ProcessBpmnStaleIdFixer(tableDefinitionRepository, formDefinitionRepository,
                        actionDefinitionRepository),
                new ProcessBpmnValidator(tableDefinitionRepository, formDefinitionRepository, null),
                new ProcessSimulationHelper(tableDefinitionRepository),
                new ProcessDebugProbeRunner(formDefinitionRepository, null, null, null, null));
    }

    private void existingProcess(String bpmnXml) {
        when(processDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(Optional.of(
                ProcessDefinition.builder()
                        .id(1L)
                        .bpmnXml(XmlEncodingUtil.encode(bpmnXml))
                        .build()));
    }

    @Test
    @DisplayName("Should reject an empty diagram overwriting a non-empty stored process")
    void shouldRejectEmptyOverwrite() {
        existingProcess(NON_EMPTY_XML);

        assertThatThrownBy(() -> component.save(FU_ID, WIPED_XML, false))
                .isInstanceOf(DeveloperBusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "EMPTY_PROCESS_OVERWRITE_BLOCKED");

        verify(processDefinitionRepository, never()).save(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("Should persist the empty diagram when the caller explicitly allows it")
    void shouldAllowConfirmedEmptyOverwrite() {
        existingProcess(NON_EMPTY_XML);

        ProcessDefinition saved = component.save(FU_ID, WIPED_XML, true);

        assertThat(XmlEncodingUtil.smartDecode(saved.getBpmnXml())).isEqualTo(WIPED_XML);
        verify(processDefinitionRepository).save(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("Should allow an empty diagram when the stored process is empty as well")
    void shouldAllowEmptyOverEmpty() {
        existingProcess(WIPED_XML);

        component.save(FU_ID, WIPED_XML, false);

        verify(processDefinitionRepository).save(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("Should allow the first save when nothing is stored yet")
    void shouldAllowFirstSave() {
        when(processDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(Optional.empty());

        component.save(FU_ID, WIPED_XML, false);

        verify(processDefinitionRepository).save(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("Should not interfere with ordinary non-empty saves")
    void shouldAllowNonEmptySave() {
        existingProcess(NON_EMPTY_XML);

        ProcessDefinition saved = component.save(FU_ID, NON_EMPTY_XML, false);

        assertThat(XmlEncodingUtil.smartDecode(saved.getBpmnXml())).isEqualTo(NON_EMPTY_XML);
    }
}
