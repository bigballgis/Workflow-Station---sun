package com.workflow.recordnote;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecordNoteTaskContextTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final RuntimeService runtime=mock(RuntimeService.class);
    final RepositoryService definitions=mock(RepositoryService.class);
    final RecordNoteTaskContext context=new RecordNoteTaskContext(jdbc,new ObjectMapper());
    final Task task=mock(Task.class);
    final Map<String,Object> row=new HashMap<>(Map.of("arbitrary_pk","Z9","person_q","first-person","team_q","a-role"));
    @BeforeEach void fixture() {
        ReflectionTestUtils.setField(context,"runtime",runtime);
        ReflectionTestUtils.setField(context,"definitions",definitions);
        when(task.getId()).thenReturn("task"); when(task.getTaskDefinitionKey()).thenReturn("work");
        when(task.getProcessDefinitionId()).thenReturn("definition"); when(task.getProcessInstanceId()).thenReturn("request");
        when(task.getExecutionId()).thenReturn("execution");
        String bpmn="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:f="http://flowable.org/bpmn">
                <process id="p"><subProcess id="mi"><extensionElements><f:properties>
                <f:property name="subTableName" value="arbitrary_table"/><f:property name="assigneeField" value="person_q"/>
                <f:property name="roleField" value="team_q"/><f:property name="assigneeMode" value="both"/>
                </f:properties></extensionElements><multiInstanceLoopCharacteristics/>
                <subProcess id="nested"><userTask id="work"/></subProcess></subProcess></process></definitions>
                """;
        when(jdbc.queryForList(anyString(),eq("catalog"))).thenReturn(List.of(
                Map.of("content_type","PROCESS","content_data",bpmn),
                Map.of("content_type","FORM","content_data","{\"type\":\"recordNote\",\"props\":{\"attachmentNotification\":{\"enabled\":true}}}"),
                Map.of("content_type","DATA_TABLE","content_data","{\"tableName\":\"arbitrary_table\",\"fields\":[{\"fieldName\":\"arbitrary_pk\",\"isPrimaryKey\":true}]}")));
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("p"); model.addProcess(process);
        var mi=new SubProcess(); mi.setId("mi");
        var loop=new MultiInstanceLoopCharacteristics(); loop.setElementVariable("configuredRow"); mi.setLoopCharacteristics(loop);
        var nested=new SubProcess(); nested.setId("nested"); var user=new UserTask(); user.setId("work");
        nested.addFlowElement(user); mi.addFlowElement(nested); process.addFlowElement(mi);
        when(definitions.getBpmnModel("definition")).thenReturn(model);
        when(jdbc.queryForList(contains("up_process_instance"),eq(String.class),eq("request"))).thenReturn(List.of("catalog"));
        when(jdbc.queryForList(contains("SELECT code"),eq(String.class),eq("catalog"))).thenReturn(List.of("p"));
        when(runtime.getVariable("execution","configuredRow")).thenReturn(row);
        when(runtime.hasVariableLocal("execution","nrOfInstances")).thenReturn(true);
        var query=mock(org.flowable.engine.runtime.ExecutionQuery.class);
        var execution=mock(org.flowable.engine.runtime.Execution.class);
        var root=mock(org.flowable.engine.runtime.Execution.class);
        when(runtime.createExecutionQuery()).thenReturn(query);
        when(query.executionId(anyString())).thenReturn(query);
        when(query.singleResult()).thenReturn(execution,root,execution,root);
        when(execution.getActivityId()).thenReturn("mi");
        when(execution.getParentId()).thenReturn("request");
    }
    @Test void soleAutomaticallyAssignedTeamMemberIsCapturedAndFrozenMetadataIsLoadedOnce() {
        when(task.getAssignee()).thenReturn("sole-member");
        var first=context.capture(task); var second=context.capture(task);
        assertEquals("TEAM",first.assignmentMode()); assertEquals("sole-member",first.recipientId());
        assertEquals(Map.of("arbitrary_pk","Z9"),second.rowKey()); assertEquals("execution",second.activationId());
        verify(jdbc,times(1)).queryForList(contains("content_type,content_data"),eq("catalog"));
    }
    @Test void personModeUsesFieldAndCompletionUsesTheSubmittedCurrentRow() {
        row.remove("team_q"); when(task.getAssignee()).thenReturn("unrelated-assignee");
        var target=context.capture(task); assertEquals("PERSON",target.assignmentMode());
        assertEquals("first-person",target.recipientId());
        when(runtime.getVariable("request","__subTables__")).thenReturn(Map.of("dw:arbitrary_table",
                List.of(Map.of("arbitrary_pk","Z9","person_q","final-person"))));
        assertEquals("final-person",context.personAtCompletion(task,target));
    }
    @Test void missingRowIdentityCannotCreateAnUntrustedCurrentPointer() {
        row.remove("arbitrary_pk"); assertNull(context.capture(task));
    }
    @Test void emptyEncodedOrBlankRoleListsUseTheSamePersonBranchAsActualAssignment() {
        row.put("team_q","[]");
        assertEquals("PERSON",context.capture(task).assignmentMode());
        row.put("team_q",List.of(" "));
        assertEquals("PERSON",context.capture(task).assignmentMode());
    }
    @Test void requestPinnedCatalogWorksWhenPortalRedeployCreatesANewDefinitionId() {
        when(jdbc.queryForList(contains("up_process_instance"),eq(String.class),eq("request"))).thenReturn(List.of());
        when(runtime.getVariable("request","functionUnitId")).thenReturn("catalog");
        assertTrue(context.enabled("definition","request"));
        assertEquals("arbitrary_table",context.capture(task).tableName());
        verify(jdbc,never()).queryForList(contains("flowable_process_definition_id"),eq(String.class),any(),any());
    }

    @Test void nestedActivationIncludesItsParentSlotWithoutUsingAnotherParallelParent() {
        var query=mock(org.flowable.engine.runtime.ExecutionQuery.class);
        var inner=mock(org.flowable.engine.runtime.Execution.class);
        var outer=mock(org.flowable.engine.runtime.Execution.class);
        var root=mock(org.flowable.engine.runtime.Execution.class);
        when(runtime.createExecutionQuery()).thenReturn(query);
        when(query.executionId(anyString())).thenReturn(query);
        when(query.singleResult()).thenReturn(inner,outer,root);
        when(inner.getActivityId()).thenReturn("inner"); when(inner.getParentId()).thenReturn("parent-a");
        when(outer.getActivityId()).thenReturn("outer"); when(outer.getParentId()).thenReturn("request");
        when(runtime.hasVariableLocal("parent-a","nrOfInstances")).thenReturn(true);
        assertEquals(Map.of("inner:parent-a","execution","outer:request","parent-a"),context.capture(task).activationContext());
    }

}
