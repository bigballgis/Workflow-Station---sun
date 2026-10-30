package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import com.platform.messaging.event.ActionEmailRequestedEvent;
import com.workflow.email.action.ActionEmailDeliveryRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentPostActionEnqueueDatabaseTest extends AssignmentPostgresFixture {
    final AssignmentOperationRepository operations=mock(AssignmentOperationRepository.class);
    AssignmentPostActionEnqueue component;
    UUID operation;
    @BeforeAll void mailSchema() throws Exception {
        try (var connection=source.getConnection()) {
            var script=new FileSystemResource("../../deploy/init-scripts/00-schema/92-we-action-email-deliveries.sql");
            ScriptUtils.executeSqlScript(connection,script); ScriptUtils.executeSqlScript(connection,script);
        }
        component=new AssignmentPostActionEnqueue(operations,new ActionEmailDeliveryRepository(jdbc));
    }
    @BeforeEach void receipt() {
        reset(operations); operation=UUID.randomUUID();
        var plan=new AssignmentPostAction.Plan("original-action","Original action","APPROVE","original comment",
                List.of(Map.of("enabled",true,"emailTo","${original_recipient}")));
        Map<String,Object> mail=JsonUtils.getObjectMapper().convertValue(plan,new com.fasterxml.jackson.core.type.TypeReference<>() { });
        var effect=new AssignmentCompletionRequest.Effect("original-instance",List.of(),"node","a".repeat(64),List.of(),Map.of(),List.of(),mail);
        Map<String,Object> payload=JsonUtils.getObjectMapper().convertValue(effect,new com.fasterxml.jackson.core.type.TypeReference<>() { });
        when(operations.find(operation)).thenReturn(Optional.of(new AssignmentOperationReceipt(operation,UUID.randomUUID(),"original-task","actor",null,
                "APPROVE","a".repeat(64),1,0,payload,"APPLIED")));
    }
    @Test void enqueueRetriesCreateOneDeliveryBoundToTheOriginalReceipt() {
        var command=new AssignmentPostAction.Enqueue(operation,0);
        component.enqueue(command); component.enqueue(command);
        String id=AssignmentPostAction.eventId(operation,0);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM we_action_email_deliveries WHERE event_id=?",Integer.class,id));
        var payload=jdbc.queryForObject("SELECT payload::text FROM we_action_email_deliveries WHERE event_id=?",String.class,id);
        var event=JsonUtils.fromJson(payload,ActionEmailRequestedEvent.class);
        assertEquals(operation,event.getAssignmentOperationId()); assertEquals(0,event.getAssignmentRuleIndex());
        assertEquals("original-action",event.getActionId()); assertEquals("original comment",event.getComment());
        assertEquals("actor",event.getOperatorId()); assertEquals("original-instance",event.getProcessInstanceId());
        jdbc.update("UPDATE we_action_email_deliveries SET status='SENT' WHERE event_id=?",id);
        component.enqueue(command);
        assertEquals("SENT",jdbc.queryForObject("SELECT status FROM we_action_email_deliveries WHERE event_id=?",String.class,id));
    }
    @Test void aRuleOutsideTheFrozenActionCannotBeQueued() {
        assertThrows(AssignmentFailure.class,()->component.enqueue(new AssignmentPostAction.Enqueue(operation,-1)));
        assertThrows(AssignmentFailure.class,()->component.enqueue(new AssignmentPostAction.Enqueue(operation,1)));
    }
}
