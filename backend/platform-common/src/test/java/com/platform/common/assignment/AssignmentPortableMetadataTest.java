package com.platform.common.assignment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentPortableMetadataTest {
    @ParameterizedTest
    @ValueSource(strings={"assignmentExclusion","assignmentRuntimeUsers","assignmentRuntimeDefinitions"})
    void businessDisplayNamesAreNotExtensionProperties(String name) {
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="synthetic">
                <userTask id="review" name="%s"/></process></definitions>
                """.formatted(name);
        assertTrue(AssignmentExclusionBpmnValidator.policies(xml).isEmpty());
    }
    @ParameterizedTest
    @ValueSource(strings={"assignmentRuntimeContract","assignmentRuntimeTracking","assignmentRuntimeUsers","assignmentRuntimeDefinitions"})
    void portableDesignerAndImportDocumentsCannotSupplyRuntimeBindings(String name) {
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://workflow.platform/schema/custom">
                <process id="synthetic"><startEvent id="start"/><extensionElements><custom:properties>
                <custom:property name="%s" value="synthetic-value"/></custom:properties></extensionElements></process></definitions>
                """.formatted(name);
        assertThrows(IllegalArgumentException.class,()->AssignmentExclusionBpmnValidator.policies(xml));
    }
}
