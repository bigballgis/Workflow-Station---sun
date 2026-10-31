package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.flowable.bpmn.model.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentCompiledMetadataTest {
    @Test void absentContractOnlyMeansLegacyWhenEveryPolicyIsOff() {
        var model=model();
        assertTrue(AssignmentCompiledMetadata.read(model).legacy());
        property(model.getFlowElement("work"),"assignmentExclusion",policy(false));
        assertTrue(AssignmentCompiledMetadata.read(model).legacy());
        property(model.getFlowElement("work"),"assignmentExclusion",policy(true));
        assertThrows(AssignmentFailure.class,()->AssignmentCompiledMetadata.read(model));
    }
    @Test void damagedPolicyIsNeverTreatedAsDisabledLegacy() {
        var model=model(); property(model.getFlowElement("work"),"assignmentExclusion","{\"schemaVersion\":1,\"excludeInitiator\":\"false\"}");
        assertThrows(IllegalArgumentException.class,()->AssignmentCompiledMetadata.read(model));
    }
    @Test void compiledContractMustContainValidTrackingAndExactUserBindings() {
        var model=model(); var process=model.getMainProcess();
        property(process,AssignmentDeploymentCompiler.CONTRACT,"1"); property(process,AssignmentDeploymentCompiler.TRACKING,"true");
        property(process,AssignmentDeploymentCompiler.USERS,"{\"alice\":\"canonical\"}");
        assertEquals(Map.of("alice","canonical"),AssignmentCompiledMetadata.read(model).users());
        property(process,AssignmentDeploymentCompiler.USERS,"{\"alice\":\"a\",\"alice\":\"b\"}");
        assertThrows(IllegalStateException.class,()->AssignmentCompiledMetadata.read(model));
        property(process,AssignmentDeploymentCompiler.USERS,"{}"); property(process,AssignmentDeploymentCompiler.TRACKING,"false");
        property(model.getFlowElement("work"),"assignmentExclusion",policy(true));
        assertThrows(IllegalStateException.class,()->AssignmentCompiledMetadata.read(model));
    }
    private BpmnModel model() {
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("fu"); model.addProcess(process);
        var task=new UserTask(); task.setId("work"); process.addFlowElement(task); return model;
    }
    private String policy(boolean enabled) { return "{\"schemaVersion\":1,\"excludeInitiator\":"+enabled+",\"excludePreviousHandlers\":false,\"excludeSelectedNodeHandlers\":false,\"selectedNodeKeys\":[],\"backup\":{\"enabled\":false}}"; }
    private void property(BaseElement element,String name,String value) {
        var containers=element.getExtensionElements().computeIfAbsent("properties",unused->new java.util.ArrayList<>());
        var container=containers.isEmpty()?new ExtensionElement():containers.get(0); container.setName("properties");
        if (containers.isEmpty()) containers.add(container);
        var entries=container.getChildElements().computeIfAbsent("property",unused->new java.util.ArrayList<>());
        entries.removeIf(entry->name.equals(entry.getAttributeValue(null,"name")));
        var property=new ExtensionElement(); property.setName("property");
        for (var pair:Map.of("name",name,"value",value).entrySet()) {
            var attribute=new ExtensionAttribute(); attribute.setName(pair.getKey()); attribute.setValue(pair.getValue()); property.addAttribute(attribute);
        }
        entries.add(property);
    }
}
