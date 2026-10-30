package com.platform.common.assignment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentExclusionBpmnValidatorTest {
    @Test void legacyProcessHasNoPolicy() {
        assertTrue(AssignmentExclusionBpmnValidator.policies(model("", "<userTask id=\"review\"/>" )).isEmpty());
    }

    @Test void referencesUseIdsAndSurviveDisplayNameChanges() {
        String bpmn = model(property("review"), "<userTask id=\"review\" name=\"Renamed\"/>");
        var policies = AssignmentExclusionBpmnValidator.policies(bpmn);
        assertEquals(java.util.List.of("review"), policies.get("work").selectedNodeKeys());
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionBpmnValidator.policies(model(property("missing"), "")));
    }

    @Test void duplicatePropertyAndNonUserTaskTargetAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionBpmnValidator.policies(
                model(property("review") + property("review"), "<userTask id=\"review\"/>")));
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionBpmnValidator.policies(
                model(property("review"), "<serviceTask id=\"review\"/>")));
    }

    @Test void characterEntitiesCannotHidePolicyFromValidation() {
        String bpmn = model(property("missing"), "").replace("assignmentExclusion", "assignment&#69;xclusion");
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionBpmnValidator.policies(bpmn));
    }

    @Test void externalEntitiesAreRejected() {
        String bpmn = "<!DOCTYPE definitions [<!ENTITY xxe SYSTEM 'file:///synthetic-unused'>]>" + model("", "");
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionBpmnValidator.policies(bpmn));
    }

    @Test void backupNodeBusinessUnitRequiresAResolvableConfiguredSource() {
        String backup = property("review").replace("&quot;enabled&quot;:false", "&quot;enabled&quot;:true,&quot;mode&quot;:&quot;BU_ROLE&quot;,"
                + "&quot;buSource&quot;:&quot;NODE_ASSIGNMENT_BU&quot;,&quot;roleCodes&quot;:[&quot;REVIEW&quot;]");
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionBpmnValidator.policies(model(backup, "<userTask id=\"review\"/>")));
        String source = "<custom:property name=\"assigneeType\" value=\"BU_ROLE\"/>";
        assertEquals(1, AssignmentExclusionBpmnValidator.policies(model(backup + source, "<userTask id=\"review\"/>")).size());
    }

    private String property(String target) {
        return """
                <custom:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,
                &quot;excludeInitiator&quot;:false,&quot;excludePreviousHandlers&quot;:false,
                &quot;excludeSelectedNodeHandlers&quot;:true,&quot;selectedNodeKeys&quot;:[&quot;%s&quot;],
                &quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
                """.formatted(target);
    }

    private String model(String properties, String otherTasks) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://workflow/custom" targetNamespace="test">
                  <process id="sample"><userTask id="work"><extensionElements><custom:properties>%s</custom:properties>
                  </extensionElements></userTask>%s</process>
                </definitions>
                """.formatted(properties, otherTasks);
    }
}
