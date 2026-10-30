package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentFailure;
import com.workflow.component.DataTableManagerComponent;
import com.workflow.component.VariableManagerComponent;
import com.workflow.dto.request.VariableSetRequest;
import com.workflow.entity.ProcessVariable;
import com.workflow.enums.VariableScope;
import com.workflow.enums.VariableType;
import com.workflow.repository.ProcessVariableRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual Flowable variable values must survive rejected generic writes, not just return an error. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentVariableManagerDatabaseTest extends AssignmentPostgresFixture {
    VariableManagerComponent variables;
    ProcessVariableRepository history;
    String definition;
    @BeforeAll void configure() {
        openEngine(); history=mock(ProcessVariableRepository.class);
        when(history.save(any(ProcessVariable.class))).thenAnswer(call->call.getArgument(0));
        variables=new VariableManagerComponent(engine.getRuntimeService(),history,new ObjectMapper(),mock(DataTableManagerComponent.class));
        var legacy=new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),
                new AssignmentCaseRepository(jdbc,new ObjectMapper()),new AssignmentDefinitionPolicy(engine.getRepositoryService()));
        ReflectionTestUtils.setField(variables,"assignmentLegacy",legacy);
        var deployment=engine.getRepositoryService().createDeployment().addString("variables.bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="synthetic">
                <process id="generic_variables" isExecutable="true"><startEvent id="start"/><userTask id="work"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/></process></definitions>
                """).deploy();
        definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }

    @Test void protectedSetIsRejectedBeforeChangingTheSourceOrSavingHistory() {
        String process=start(true); engine.getRuntimeService().setVariable(process,"renamed_source","original");
        clearInvocations(history);
        assertAll(()->assertThrows(AssignmentFailure.class,()->variables.setVariable(request(process))),
                ()->assertEquals("original",engine.getRuntimeService().getVariable(process,"renamed_source")),
                ()->verifyNoInteractions(history));
    }
    @Test void protectedRemoveCannotEraseAnExistingSource() {
        String process=start(true); engine.getRuntimeService().setVariable(process,"renamed_source","original");
        clearInvocations(history);
        assertAll(()->assertThrows(AssignmentFailure.class,()->variables.removeVariable(process,"renamed_source")),
                ()->assertEquals("original",engine.getRuntimeService().getVariable(process,"renamed_source")),
                ()->verifyNoInteractions(history));
    }
    @Test void positivelyIdentifiedLegacySetAndRemoveKeepTheirOriginalBehavior() {
        String process=start(false);
        variables.setVariable(request(process));
        assertEquals("replacement",engine.getRuntimeService().getVariable(process,"renamed_source"));
        variables.removeVariable(process,"renamed_source");
        assertNull(engine.getRuntimeService().getVariable(process,"renamed_source"));
    }
    private VariableSetRequest request(String process) {
        return VariableSetRequest.builder().processInstanceId(process).name("renamed_source").value("replacement")
                .type(VariableType.STRING).scope(VariableScope.PROCESS_INSTANCE).build();
    }
    private String start(boolean tracked) {
        String instance=engine.getRuntimeService().startProcessInstanceById(definition).getId();
        if (tracked) {
            var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper()); var id=UUID.randomUUID();
            cases.createCase(id,instance,"original","BU");
            cases.insert(new AssignmentCaseRegistry.Registration(instance,id,UUID.randomUUID(),definition,List.of()),null,null,"generic_variables");
        }
        return instance;
    }
}
