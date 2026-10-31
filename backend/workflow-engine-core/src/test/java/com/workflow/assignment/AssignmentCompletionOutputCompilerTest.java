package com.workflow.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import org.flowable.bpmn.model.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AssignmentCompletionOutputCompilerTest {
    final com.fasterxml.jackson.databind.ObjectMapper json=JsonUtils.getObjectMapper();
    final AssignmentTaskStateRepository positions=mock(AssignmentTaskStateRepository.class);
    final AssignmentDefinitionPolicy definitions=mock(AssignmentDefinitionPolicy.class);
    final AssignmentCompletionOutputCompiler compiler=new AssignmentCompletionOutputCompiler(positions,definitions);
    @Test void nativeOutputsCopyOnlyDeclaredTargetsAgainstTheirOriginalPortalValues() {
        var call=new CallActivity(); call.setId("call"); var output=new IOParameter(); output.setSource("returned"); output.setTarget("destination"); call.getOutParameters().add(output);
        configure(call,null);
        var before=json.createObjectNode().put("destination","old").put("keep","newer Portal value");
        var parent=state("parent",null,"parent-def","ACTIVE",Map.of("destination","new","keep","stale Engine value"));
        var patches=compiler.compile(request("child",List.of(),List.of(baseline("parent","parent-def",before))),List.of(parent,child()));
        assertEquals(1,patches.size());
        var applied=AssignmentVariablePatch.apply(before,patches.get(0).variablePatch());
        assertEquals("new",applied.path("destination").asText()); assertEquals("newer Portal value",applied.path("keep").asText());
        assertEquals("old",before.path("destination").asText());
    }
    @Test void rowOutputsUseTheFrozenFullKeyAndFinalEngineTargetWithoutReplacingUnmappedRowFields() throws Exception {
        var call=new CallActivity(); call.setId("call"); call.setLoopCharacteristics(new MultiInstanceLoopCharacteristics());
        property(call,"callRowsTable","renamed_items"); property(call,"callOutputMapping","[{\"from\":\"returned\",\"to\":\"row.assignee\"}]");
        var key=json.readTree("{\"region\":\"HK\",\"number\":7}"); configure(call,key);
        var before=(ObjectNode)json.readTree("{\"__subTables__\":{\"dw:renamed_items\":[{\"region\":\"HK\",\"number\":7,\"assignee\":\"old\",\"keep\":\"Portal value\"}]}}");
        var frozen=json.readTree("{\"__subTables__\":{\"dw:renamed_items\":[{\"region\":\"HK\",\"number\":7,\"assignee\":\"final value\",\"keep\":\"stale value\"}]}}");
        var parent=state("parent",null,"parent-def","ACTIVE",values(frozen));
        var patches=compiler.compile(request("child",List.of(),List.of(baseline("parent","parent-def",before))),List.of(parent,child()));
        var row=AssignmentVariablePatch.apply(before,patches.get(0).variablePatch()).path("__subTables__").path("dw:renamed_items").get(0);
        assertEquals("final value",row.path("assignee").asText()); assertEquals("Portal value",row.path("keep").asText());
    }
    @Test void anOutputReturningToTheCompletingTaskUsesItsPostStageExpectedValue() {
        var call=new CallActivity(); call.setId("call"); var output=new IOParameter(); output.setTarget("destination"); call.getOutParameters().add(output); configure(call,null);
        var before=json.createObjectNode().put("destination","old"); var submitted=before.deepCopy().put("destination","submitted");
        var parent=state("parent",null,"parent-def","ACTIVE",Map.of("destination","child result"));
        var patches=compiler.compile(request("parent",AssignmentVariablePatch.between(before,submitted),List.of(baseline("parent","parent-def",before))),List.of(parent,child()));
        assertEquals("child result",AssignmentVariablePatch.apply(submitted,patches.get(0).variablePatch()).path("destination").asText());
        assertThrows(AssignmentFailure.class,()->AssignmentVariablePatch.apply(before,patches.get(0).variablePatch()));
    }
    @Test void aCancelledCallerDoesNotImportNativeOutValuesThatFlowableNeverApplied() {
        var call=new CallActivity(); call.setId("call"); var output=new IOParameter(); output.setTarget("destination"); call.getOutParameters().add(output); configure(call,null);
        var parent=state("parent",null,"parent-def","CANCELLED",Map.of("destination","stale"));
        assertTrue(compiler.compile(request("child",List.of(),List.of(baseline("parent","parent-def",json.createObjectNode().put("destination","Portal")))),List.of(parent,child())).isEmpty());
    }
    @Test void missingCallerBaselinesCannotBeReconstructedFromCurrentEngineValues() {
        var call=new CallActivity(); call.setId("call"); configure(call,null);
        var parent=state("parent",null,"parent-def","ACTIVE",Map.of());
        assertThrows(AssignmentFailure.class,()->compiler.compile(request("child",List.of(),List.of()),List.of(parent,child())));
    }
    private void configure(CallActivity call,JsonNode row) {
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("parent"); process.addFlowElement(call); model.addProcess(process);
        when(definitions.model("parent-def")).thenReturn(model);
        when(positions.roundPositions(anyString())).thenReturn(List.of(new AssignmentTaskStateRepository.RoundInstance("child","child-def","parent","call","COMPLETED",row),
                new AssignmentTaskStateRepository.RoundInstance("parent","parent-def",null,null,"ACTIVE")));
    }
    private AssignmentCompletionRequest request(String source,List<AssignmentVariablePatch.Entry> patch,List<AssignmentCompletionRequest.InstanceBaseline> baselines) {
        return new AssignmentCompletionRequest(UUID.randomUUID(),"task","actor",null,"APPROVE",Map.of("action","APPROVE"),0,
                new AssignmentCompletionRequest.Effect(source,patch,"review","a".repeat(64),List.of(),Map.of(),baselines));
    }
    private AssignmentCompletionRequest.InstanceBaseline baseline(String instance,String definition,JsonNode values) { return new AssignmentCompletionRequest.InstanceBaseline(instance,definition,values); }
    private AssignmentAcceptedProcessState child() { return state("child","parent","child-def","COMPLETED",Map.of("returned","child result")); }
    private AssignmentAcceptedProcessState state(String id,String parent,String definition,String status,Map<String,Object> vars) {
        return new AssignmentAcceptedProcessState(id,definition,"process","Process",parent,parent==null?null:"call",status,null,null,
                Instant.EPOCH,"ACTIVE".equals(status)?null:Instant.EPOCH.plusSeconds(1),vars,List.of());
    }
    private Map<String,Object> values(JsonNode value) { return json.convertValue(value,new com.fasterxml.jackson.core.type.TypeReference<>() { }); }
    private void property(CallActivity call,String name,String value) {
        var properties=new ExtensionElement(); properties.setName("properties"); var property=new ExtensionElement(); property.setName("property");
        for (var entry:Map.of("name",name,"value",value).entrySet()) { var attribute=new ExtensionAttribute(); attribute.setName(entry.getKey()); attribute.setValue(entry.getValue()); property.addAttribute(attribute); }
        properties.addChildElement(property); call.addExtensionElement(properties);
    }
}
