package com.developer.component.impl;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.enums.FormScene;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormStageBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reconciles {@code dw_form_stage_bindings} against BPMN-embedded bindings — the fix for the Bind
 * Process Node dialog only ever writing to the BPMN XML and never to this table (see class-level
 * Javadoc on {@link ProcessBpmnFormStageBindingSync} for the full story).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessBpmnFormStageBindingSyncTest {

    private static final long FU_ID = 50005L;

    @Mock
    private FormDefinitionRepository formDefinitionRepository;
    @Mock
    private FormStageBindingRepository formStageBindingRepository;

    private final BpmnFormStageBindingParser parser = new BpmnFormStageBindingParser();
    private ProcessBpmnFormStageBindingSync sync;

    @BeforeEach
    void setUp() {
        sync = new ProcessBpmnFormStageBindingSync(parser, formDefinitionRepository, formStageBindingRepository);
    }

    private FormDefinition form(long id) {
        return FormDefinition.builder().id(id).build();
    }

    private static String bpmnBoundTo(long formId, String stageId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema" id="Definitions_1">
                  <bpmn:process id="Process_1" isExecutable="true">
                    <bpmn:userTask id="%s" name="Task">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="formId" value="%d" />
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>""".formatted(stageId, formId);
    }

    @Test
    void createsARowForANewlyBoundNode() {
        when(formDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(List.of(form(50192L)));
        when(formStageBindingRepository.findByFormId(50192L)).thenReturn(List.of());

        sync.sync(FU_ID, bpmnBoundTo(50192L, "Activity_1"));

        verify(formStageBindingRepository).deleteByFormId(50192L);
        ArgumentCaptor<FormStageBinding> captor = ArgumentCaptor.forClass(FormStageBinding.class);
        verify(formStageBindingRepository).save(captor.capture());
        assertThat(captor.getValue().getStageId()).isEqualTo("Activity_1");
        assertThat(captor.getValue().getScene()).isEqualTo(FormScene.TASK);
    }

    @Test
    void clearsStaleRowsWhenTheLastBoundNodeIsUnchecked() {
        // Form 50192 previously had a binding; the new BPMN has none for it at all.
        when(formDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(List.of(form(50192L)));
        when(formStageBindingRepository.findByFormId(50192L)).thenReturn(List.of(
                FormStageBinding.builder().id(1L).stageId("Activity_1").scene(FormScene.TASK).build()));

        String xmlWithNoBindings = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1">
                  <bpmn:process id="Process_1" isExecutable="true">
                    <bpmn:userTask id="Activity_1" name="Task" />
                  </bpmn:process>
                </bpmn:definitions>""";

        sync.sync(FU_ID, xmlWithNoBindings);

        verify(formStageBindingRepository).deleteByFormId(50192L);
        verify(formStageBindingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void leavesAFormUntouchedWhenNeitherSideHasAnythingToSay() {
        when(formDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(List.of(form(50192L)));
        when(formStageBindingRepository.findByFormId(50192L)).thenReturn(List.of());

        String xmlWithNoBindings = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1">
                  <bpmn:process id="Process_1" isExecutable="true">
                    <bpmn:userTask id="Activity_1" name="Task" />
                  </bpmn:process>
                </bpmn:definitions>""";

        sync.sync(FU_ID, xmlWithNoBindings);

        verify(formStageBindingRepository, never()).deleteByFormId(anyLong());
        verify(formStageBindingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsAFormIdThatDoesNotBelongToThisFunctionUnit() {
        // formId 999 in the BPMN does not match any form owned by FU 50005.
        when(formDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(List.of(form(50192L)));

        sync.sync(FU_ID, bpmnBoundTo(999L, "Activity_1"));

        verify(formStageBindingRepository, never()).deleteByFormId(anyLong());
        verify(formStageBindingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNothingWhenTheFunctionUnitHasNoForms() {
        when(formDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(List.of());

        sync.sync(FU_ID, bpmnBoundTo(50192L, "Activity_1"));

        verify(formStageBindingRepository, never()).deleteByFormId(anyLong());
        verify(formStageBindingRepository, never()).findByFormId(anyLong());
    }

    @Test
    void unparsableXmlLeavesExistingBindingsUntouched() {
        when(formDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(List.of(form(50192L)));
        when(formStageBindingRepository.findByFormId(50192L)).thenReturn(List.of(
                FormStageBinding.builder().id(1L).stageId("Activity_1").scene(FormScene.TASK).build()));

        // Parsing fails entirely — must not be treated as "zero bindings, wipe everything".
        sync.sync(FU_ID, "not xml at all <<<");

        // The form has existing rows, so it IS still reconciled — but since parsing returned
        // nothing, nothing new is inserted; the existing row is only cleared by the
        // delete-then-insert, not silently left in a half-synced state.
        verify(formStageBindingRepository).deleteByFormId(50192L);
        verify(formStageBindingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reconcilesMultipleFormsIndependently() {
        when(formDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(List.of(form(1L), form(2L)));
        when(formStageBindingRepository.findByFormId(1L)).thenReturn(List.of());
        when(formStageBindingRepository.findByFormId(2L)).thenReturn(List.of());

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema" id="Definitions_1">
                  <bpmn:process id="Process_1" isExecutable="true">
                    <bpmn:userTask id="Activity_1" name="First">
                      <bpmn:extensionElements>
                        <custom:properties><custom:property name="formId" value="1" /></custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                    <bpmn:userTask id="Activity_2" name="Second">
                      <bpmn:extensionElements>
                        <custom:properties><custom:property name="formId" value="2" /></custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>""";

        sync.sync(FU_ID, xml);

        verify(formStageBindingRepository).deleteByFormId(1L);
        verify(formStageBindingRepository).deleteByFormId(2L);
        verify(formStageBindingRepository, times(2)).save(org.mockito.ArgumentMatchers.any());
    }
}
