package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.workflow.assignment.impl.AssignmentSourceResolverImpl;
import com.workflow.client.AdminCenterClient;
import com.workflow.listener.TaskAssignmentListener;
import com.workflow.service.TaskAssigneeResolver;
import org.flowable.bpmn.model.*;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentInitiatorSourceBindingTest {
    final AssignmentSourceValues values=mock(AssignmentSourceValues.class);
    final AssignmentTaskStateRepository.CaseContext context=new AssignmentTaskStateRepository.CaseContext("original","BU-ORIGINAL",1,0);
    final AssignmentDefinitionPolicy definitions=mock(AssignmentDefinitionPolicy.class);
    final AdminCenterClient admin=mock(AdminCenterClient.class);
    final Task task=mock(Task.class);
    final UserTask node=new UserTask();
    final AssignmentSourceResolver sources=new AssignmentSourceResolverImpl(new TaskAssigneeResolver(admin),mock(TaskAssignmentListener.class),
            values,definitions);
    @BeforeEach void configure() {
        when(task.getId()).thenReturn("task"); when(task.getTaskDefinitionKey()).thenReturn("review"); when(task.getProcessDefinitionId()).thenReturn("frozen");
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("renamed_fu");
        model.addProcess(process); node.setId("review"); process.addFlowElement(node); when(definitions.model("frozen")).thenReturn(model);
        when(admin.getUserInfo(anyString())).thenAnswer(call->Map.of("entityManagerId",call.getArgument(0)+"-manager"));
    }
    @ParameterizedTest @ValueSource(strings={"forged-business-field","callee-mapped-initiator"})
    void initiatorAssignmentUsesTheCaseOriginInsteadOfBusinessOrChildInput(String supplied) {
        assertEquals("original",sources.resolve(task,source("INITIATOR",supplied,null),context).assignment().getAssignee());
    }
    @Test void defaultOrganizationAnchorUsesTheRootInitiator() {
        assertEquals("original-manager",sources.resolve(task,source("ENTITY_MANAGER","forged","forged"),context).assignment().getAssignee());
        verify(admin).getUserInfo("original");
    }
    @Test void explicitlyConfiguredLastHandlerAnchorRetainsItsOwnIdentity() {
        property("assigneeAnchor","LAST_TASK_ASSIGNEE");
        assertEquals("prior-handler-manager",sources.resolve(task,source("ENTITY_MANAGER","forged","prior-handler"),context).assignment().getAssignee());
        verify(admin).getUserInfo("prior-handler");
    }
    @Test void rootHierarchyAnchorUsesTheOriginalBusinessUnit() {
        when(admin.getUserBusinessUnitId(anyString(),any())).thenReturn("BU-ORIGINAL");
        sources.resolve(task,source("HIERARCHY_ROLE","forged","forged"),context);
        verify(admin).getUserBusinessUnitId("original","BU-ORIGINAL");
    }
    @Test void aMissingTrustedContextCannotAuthorizeAnInitiatorSource() {
        assertThrows(AssignmentFailure.class,()->sources.resolve(task,source("INITIATOR","forged",null),null));
    }
    @Test void rowRoleWithoutABuColumnUsesTheImmutableRootBuWithoutReadingAMutableWorkspaceVariable() {
        when(values.row(eq(task),anyList())).thenReturn(Map.of("role_tags",List.of("configured-role")));
        when(admin.isEligibleRole(anyString(),anyString())).thenReturn(true);
        var resolution=sources.resolve(task,AssignmentSourceSpec.row(null,"role_tags",null),context);
        assertEquals("BU-ORIGINAL",resolution.singleBusinessUnitCode());
        verify(values,never()).variables(any(),anyList());
    }
    @Test void explicitlyConfiguredRowBuStillSelectsItsOwnSource() {
        when(values.row(eq(task),anyList())).thenReturn(Map.of("role_tags",List.of("configured-role"),"row_bu","BU-ROW"));
        when(admin.isEligibleRole(anyString(),anyString())).thenReturn(true);
        var resolution=sources.resolve(task,AssignmentSourceSpec.row(null,"role_tags","row_bu"),context);
        assertEquals("BU-ROW",resolution.singleBusinessUnitCode());
    }
    private AssignmentSourceSpec source(String type,String initiator,String anchor) {
        return AssignmentSourceSpec.resolver(new AssignmentSourceSpec.Resolver(type,List.of("configured-role"),null,initiator,anchor,"BU-FORGED"));
    }
    private void property(String name,String value) {
        var properties=new ExtensionElement(); properties.setName("properties"); var property=new ExtensionElement(); property.setName("property");
        for (var pair:Map.of("name",name,"value",value).entrySet()) {
            var attribute=new ExtensionAttribute(); attribute.setName(pair.getKey()); attribute.setValue(pair.getValue()); property.addAttribute(attribute);
        }
        properties.addChildElement(property); node.addExtensionElement(properties);
    }
}
