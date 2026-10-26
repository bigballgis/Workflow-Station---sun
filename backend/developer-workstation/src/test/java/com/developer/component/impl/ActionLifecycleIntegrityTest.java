package com.developer.component.impl;

import com.developer.dto.ActionDefinitionRequest;
import com.developer.entity.ActionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.enums.ActionType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.*;
import com.developer.util.XmlEncodingUtil;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionLifecycleIntegrityTest {
    private record Fixture(ActionDefinitionRepository actions, FunctionUnit unit, ActionDefinition action,
                           ActionDesignComponentImpl component, ProcessBpmnStaleIdFixer fixer) {}

    private Fixture fixture(String xml) {
        var actions = mock(ActionDefinitionRepository.class);
        var units = mock(FunctionUnitRepository.class);
        var forms = mock(FormDefinitionRepository.class);
        var tables = mock(TableDefinitionRepository.class);
        var i18n = mock(I18nService.class);
        var unit = FunctionUnit.builder().id(1L).name("Integrity test").build();
        unit.setProcessDefinition(ProcessDefinition.builder().functionUnit(unit).bpmnXml(xml).build());
        var action = ActionDefinition.builder().id(12L).actionName("Approve").actionType(ActionType.APPROVE)
                .functionUnit(unit).configJson(Map.of()).build();
        when(actions.findById(12L)).thenReturn(Optional.of(action));
        lenient().when(actions.findByFunctionUnitId(1L)).thenReturn(List.of(action));
        lenient().when(actions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(i18n.getMessage(anyString())).thenReturn("test message");
        lenient().when(forms.findByFunctionUnitId(1L)).thenReturn(List.of());
        lenient().when(tables.findByFunctionUnitId(1L)).thenReturn(List.of());
        var component = new ActionDesignComponentImpl(actions, units, forms, tables,
                mock(FormTableBindingRepository.class), i18n);
        return new Fixture(actions, unit, action, component, new ProcessBpmnStaleIdFixer(tables, forms, actions));
    }

    @Test
    void refusesDeletingEncodedStringAndTextBindingsInBothScopes() {
        for (String key : List.of("actionIds", "globalActionIds")) {
            for (String entry : List.of(
                    "<c:property name=\"" + key + "\" value=\"[12]\"/>",
                    "<c:values name=\"" + key + "\" value=\"[&quot; 12 &quot;]\"/>",
                    "<c:property name=\"" + key + "\"><![CDATA[[12]]]></c:property>")) {
                var f = fixture(XmlEncodingUtil.encode(xml(key, entry)));
                assertThatThrownBy(() -> f.component.delete(12L)).isInstanceOf(DeveloperBusinessException.class)
                        .extracting(e -> ((DeveloperBusinessException) e).getErrorCode()).isEqualTo("ACTION_IN_USE");
                verify(f.actions, never()).delete(any(ActionDefinition.class));
            }
        }
    }

    @Test
    void keepsValidIdsWhenAnotherActionReusesTheirOldName() {
        for (String key : List.of("actionIds", "globalActionIds")) {
            var f = fixture(binding(key, "[12]", "Approve"));
            f.action.setActionName("Approve invoice");
            when(f.actions.findByFunctionUnitId(1L)).thenReturn(List.of(f.action,
                    ActionDefinition.builder().id(22L).actionName("Approve").actionType(ActionType.REJECT).build()));
            String result = f.fixer.fixStaleIds(1L, f.unit.getProcessDefinition().getBpmnXml());
            assertThat(result).contains("name=\"" + key + "\" value=\"[12]\"")
                    .contains("Approve invoice").doesNotContain("value=\"[22]\"");
        }
    }

    @Test
    void renamingSynchronizesBindingNamesAndKeepsStoredEncoding() {
        var f = fixture(XmlEncodingUtil.encode(binding("actionIds", "[12]", "Approve")));
        f.component.update(12L, ActionDefinitionRequest.builder().actionName("Approve invoice")
                .actionType(ActionType.APPROVE).configJson(Map.of()).build());
        String stored = f.unit.getProcessDefinition().getBpmnXml();
        assertThat(stored).doesNotStartWith("<");
        assertThat(XmlEncodingUtil.smartDecode(stored)).contains("Approve invoice").contains("value=\"[12]\"");
    }

    @Test
    void stillRepairsGenuinelyMissingLegacyIdsByName() {
        for (String key : List.of("actionIds", "globalActionIds")) {
            var f = fixture(binding(key, "[999]", "Approve"));
            assertThat(f.fixer.fixStaleIds(1L, f.unit.getProcessDefinition().getBpmnXml()))
                    .contains("name=\"" + key + "\" value=\"[12]\"");
        }
    }

    @Test
    void allowsDeletingAnUnboundAction() {
        var f = fixture(binding("actionIds", "[99]", "Another action"));
        f.component.delete(12L);
        verify(f.actions).delete(f.action);
    }

    private static String binding(String key, String ids, String name) {
        return xml(key, "<c:property name=\"" + key + "\" value=\"" + ids + "\"/>"
                + "<c:property name=\"" + (key.equals("actionIds") ? "actionNames" : "globalActionNames")
                + "\" value=\"[&quot;" + name + "&quot;]\"/>");
    }

    private static String xml(String key, String entries) {
        String props = "<bpmn:extensionElements><c:properties>" + entries + "</c:properties></bpmn:extensionElements>";
        return "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:c=\"http://workflow.platform/schema/custom\" targetNamespace=\"test\"><bpmn:process id=\"p\">"
                + (key.equals("actionIds") ? "<bpmn:userTask id=\"task\">" + props + "</bpmn:userTask>" : props)
                + "</bpmn:process></bpmn:definitions>";
    }
}
