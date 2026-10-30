package com.workflow.assignment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentTransitionVariableChangesTest {
    @Test void serviceOutputChangesReachPortalWithoutResurrectingUnchangedStaleValues() {
        var engine=node(Map.of("nextApprover","old","other","stale","nested",Map.of("changed",1,"keep","stale")));
        var portal=node(Map.of("nextApprover","old","other","newer Portal","nested",Map.of("changed",1,"keep","newer Portal")));
        AssignmentTransitionVariableChanges.apply(engine,Map.of("nextApprover","returned","other","stale","nested",Map.of("changed",2,"keep","stale")),portal);
        assertEquals("returned",portal.path("nextApprover").asText());
        assertEquals("newer Portal",portal.path("other").asText());
        assertEquals("newer Portal",portal.path("nested").path("keep").asText());
        assertEquals(2,portal.path("nested").path("changed").asInt());
        assertEquals("old",engine.path("nextApprover").asText());
    }
    @Test void newLeavesAndExplicitDeletesAreCarriedWithoutImportingUnchangedObjectFields() {
        var engine=node(Map.of("nested",Map.of("existing","stale"),"deleted","old"));
        var portal=node(Map.of("deleted","old"));
        AssignmentTransitionVariableChanges.apply(engine,Map.of("nested",Map.of("existing","stale","created","new"),"cleared",com.fasterxml.jackson.databind.node.NullNode.instance),portal);
        assertFalse(portal.has("deleted")); assertTrue(portal.path("cleared").isNull());
        assertEquals("new",portal.path("nested").path("created").asText());
        assertFalse(portal.path("nested").has("existing"));
    }
    @Test void workflowExecutionCannotChangeTheFrozenFunctionUnitAssociation() {
        var portal=node(Map.of("functionUnitId","original"));
        assertThrows(AssignmentFailure.class,()->AssignmentTransitionVariableChanges.apply(portal,Map.of("functionUnitId","different"),portal));
        assertEquals("original",portal.path("functionUnitId").asText());
    }
    @Test void rowArraysAreLeftToTheDeclaredIdentityBasedOutputCompiler() {
        var engine=node(Map.of("__subTables__",Map.of("dw:items",java.util.List.of(Map.of("key",1,"value","old")))));
        var portal=engine.deepCopy();
        AssignmentTransitionVariableChanges.apply(engine,Map.of("__subTables__",Map.of("dw:items",java.util.List.of(Map.of("key",1,"value","new")))),portal);
        assertEquals(engine,portal);
    }
    private ObjectNode node(Map<String,?> values) { return JsonUtils.getObjectMapper().valueToTree(values); }
}
