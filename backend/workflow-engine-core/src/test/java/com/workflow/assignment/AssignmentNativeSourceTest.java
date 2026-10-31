package com.workflow.assignment;

import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentNativeSourceTest {
    @Test void exactVariableRemainsDynamicRatherThanBecomingAFixedEvaluatedUser() {
        var task=new UserTask(); task.setAssignee("${ reviewer_reference }");
        var source=AssignmentNativeSource.dynamic(task);
        assertEquals(AssignmentSourceSpec.Kind.VARIABLE,source.kind()); assertEquals("reviewer_reference",source.variable());
    }
    @Test void initiatorUsesTheCanonicalCaseSource() {
        var task=new UserTask(); task.setAssignee("#{initiator}");
        var source=AssignmentNativeSource.dynamic(task);
        assertEquals(AssignmentSourceSpec.Kind.RESOLVER,source.kind()); assertEquals("INITIATOR",source.type());
    }
    @Test void literalUsersRemainDeploymentBound() {
        var task=new UserTask(); task.setAssignee("literal-reference"); assertNull(AssignmentNativeSource.dynamic(task));
    }
    @Test void nestedAndMixedExpressionsCannotSilentlyBecomeFixedSources() {
        for (String expression:java.util.List.of("${row.reviewer}","${bean.resolve()}","prefix-${reviewer}")) {
            var task=new UserTask(); task.setAssignee(expression);
            assertThrows(IllegalArgumentException.class,()->AssignmentNativeSource.dynamic(task));
        }
    }
    @Test void mixedCandidatesRequireAnExplicitPlatformSource() {
        var task=new UserTask(); task.setAssignee("${reviewer}"); task.setCandidateUsers(java.util.List.of("other"));
        assertThrows(IllegalArgumentException.class,()->AssignmentNativeSource.dynamic(task));
        task.setAssignee(null); task.setCandidateUsers(java.util.List.of("${reviewers}"));
        assertThrows(IllegalArgumentException.class,()->AssignmentNativeSource.dynamic(task));
    }
    @Test void unknownPlatformSourceCannotBeAcceptedAsANativeFixedAssignment() {
        var task=new UserTask(); task.setAssignee("literal");
        var properties=new org.flowable.bpmn.model.ExtensionElement(); properties.setName("properties");
        var property=new org.flowable.bpmn.model.ExtensionElement(); property.setName("property");
        for (var entry:java.util.Map.of("name","assigneeType","value","UNSUPPORTED_SOURCE").entrySet()) {
            var attribute=new org.flowable.bpmn.model.ExtensionAttribute(); attribute.setName(entry.getKey()); attribute.setValue(entry.getValue()); property.addAttribute(attribute);
        }
        properties.addChildElement(property); task.addExtensionElement(properties);
        assertThrows(IllegalArgumentException.class,()->AssignmentNativeSource.dynamic(task));
    }
}
