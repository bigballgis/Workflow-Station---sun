package com.workflow.recordnote;

import com.platform.common.recordnote.RecordNoteTaskTarget;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.mockito.Mockito.*;

class RecordNoteRollbackSupportTest {
    final RecordNoteRoundRepository rounds=mock(RecordNoteRoundRepository.class);
    final RepositoryService repository=mock(RepositoryService.class);
    final RecordNoteRollbackSupport support=new RecordNoteRollbackSupport(rounds,repository,mock(JdbcTemplate.class));
    final Task task=mock(Task.class);
    @BeforeEach void setup() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(task.getId()).thenReturn("current"); when(task.getProcessInstanceId()).thenReturn("request");
        when(task.getProcessDefinitionId()).thenReturn("definition");
        when(rounds.current("request")).thenReturn(List.of(new RecordNoteTaskTarget("current","work","mi","table",
                Map.of("custom_pk","row-2"),"activation-2","PERSON","owner_q","current-handler","ACTIVE",Map.of())));
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); model.addProcess(process);
        var before=new UserTask(); before.setId("before"); process.addFlowElement(before);
        var mi=new SubProcess(); mi.setId("mi"); mi.setLoopCharacteristics(new MultiInstanceLoopCharacteristics());
        var work=new UserTask(); work.setId("work"); mi.addFlowElement(work); process.addFlowElement(mi);
        var edge=new SequenceFlow(); edge.setTargetFlowElement(mi); before.setOutgoingFlows(List.of(edge));
        when(repository.getBpmnModel("definition")).thenReturn(model);
    }
    @AfterEach void clear() { TransactionSynchronizationManager.clear(); }
    @Test void returningWithinTheSameMiScopeInvalidatesOnlyTheCurrentRow() {
        support.returning(task,"work");
        verify(rounds).invalidate("request",List.of("work"),Map.of("custom_pk","row-2"));
    }
    @Test void returningBeforeTheMiScopeInvalidatesAllAffectedRows() {
        support.returning(task,"before");
        verify(rounds).invalidate("request",List.of("before","work"),null);
    }
}
