package com.developer.component.impl;

import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormStageBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessDesignComponentImpl#save} must keep {@code dw_form_stage_bindings} in sync with
 * whatever the Bind Process Node dialog just wrote into the BPMN, and a sync failure must never
 * take down the BPMN save itself (defense in depth — the sync is best-effort consistency, not
 * the primary write).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessDesignComponentSaveFormStageBindingSyncTest {

    private static final long FU_ID = 50005L;

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
    @Mock
    private FormStageBindingRepository formStageBindingRepository;

    private ProcessDesignComponentImpl component;

    private static final String BOUND_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:custom="http://custom.bpmn.io/schema" id="Definitions_1">
              <bpmn:process id="Process_1" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" />
                <bpmn:userTask id="Activity_1" name="Sub task">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="formId" value="50192" />
                      <custom:property name="formName" value="Sub task" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                <bpmn:endEvent id="EndEvent_1" />
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1" />
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_1" targetRef="EndEvent_1" />
              </bpmn:process>
            </bpmn:definitions>""";

    @BeforeEach
    void setUp() {
        when(functionUnitRepository.findById(FU_ID))
                .thenReturn(Optional.of(FunctionUnit.builder().id(FU_ID).build()));
        when(formDefinitionRepository.findByFunctionUnitId(anyLong())).thenReturn(
                List.of(FormDefinition.builder().id(50192L).build()));
        when(tableDefinitionRepository.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        when(actionDefinitionRepository.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        when(processDefinitionRepository.save(any(ProcessDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(formStageBindingRepository.findByFormId(anyLong())).thenReturn(List.of());

        component = new ProcessDesignComponentImpl(
                processDefinitionRepository,
                functionUnitRepository,
                new ProcessBpmnStaleIdFixer(tableDefinitionRepository, formDefinitionRepository,
                        actionDefinitionRepository),
                new ProcessBpmnValidator(tableDefinitionRepository, formDefinitionRepository, null),
                new ProcessSimulationHelper(tableDefinitionRepository),
                new ProcessDebugProbeRunner(formDefinitionRepository, null, null, null, null),
                new ProcessBpmnFormStageBindingSync(
                        new BpmnFormStageBindingParser(), formDefinitionRepository, formStageBindingRepository));
    }

    @Test
    void savingABoundBpmnSyncsTheFormStageBindingsTable() {
        component.save(FU_ID, BOUND_XML, false);

        verify(formStageBindingRepository).deleteByFormId(50192L);
        verify(formStageBindingRepository, times(1)).save(any());
    }

    @Test
    void aSyncFailureDoesNotFailTheBpmnSave() {
        when(formStageBindingRepository.findByFormId(anyLong()))
                .thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> component.save(FU_ID, BOUND_XML, false)).doesNotThrowAnyException();
        verify(processDefinitionRepository).save(any(ProcessDefinition.class));
    }
}
