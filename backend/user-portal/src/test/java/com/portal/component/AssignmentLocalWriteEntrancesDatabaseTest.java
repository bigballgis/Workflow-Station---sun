package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.fk.PrimaryKeyAllocationService;
import com.platform.common.functionunit.*;
import com.portal.client.AssignmentEngineTransport;
import com.portal.client.WorkflowEngineClient;
import com.portal.component.impl.AssignmentPortalWriteGuardImpl;
import com.portal.dto.*;
import com.portal.dto.RecordNoteDtos.*;
import com.portal.entity.*;
import com.portal.recordnote.*;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.RecordNoteService;
import com.portal.service.UserDisplayNameResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Calls real entry components and the PostgreSQL fence; synthetic persistence stays in the owned schema. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentLocalWriteEntrancesDatabaseTest extends AssignmentPortalDatabaseFixture {
    UUID caseId;
    AssignmentPortalWriteExecutor writes;
    boolean stale;
    final EntityManager entities=mock(EntityManager.class);
    final ProcessInstanceRepository instances=mock(ProcessInstanceRepository.class);
    final RecordNoteService notes=mock(RecordNoteService.class);
    @BeforeEach void setup() {
        reset(entities,instances,notes); stale=false; caseId=UUID.randomUUID();
        jdbc.execute("CREATE TABLE IF NOT EXISTS synthetic_assignment_writes(id uuid,kind text)");
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version) VALUES(?,'actor',1)",caseId);
        var transport=mock(AssignmentEngineTransport.class);
        when(transport.post(anyList(),any(),any(),eq(false))).thenAnswer(call->{
            AssignmentWriteDecision.Request request=call.getArgument(1);
            long portalRevision=jdbc.queryForObject("SELECT portal_revision FROM we_assignment_cases WHERE case_id=?",Long.class,caseId);
            return new AssignmentWriteDecision(1,true,request.taskId(),request.actor(),request.representedUser(),request.operation(),request.requestDigest(),
                    caseId,stale?1:0,portalRevision,new AssignmentAuthorityFence(jdbc).revision(),Instant.now().plusSeconds(30),request.instanceId());
        });
        var guard=new AssignmentPortalWriteGuardImpl(transport,jdbc); ReflectionTestUtils.setField(guard,"entities",entities);
        com.portal.testsupport.AssignmentCoordinationTestSupport.stubVerified(guard);
        writes=new AssignmentPortalWriteExecutor(guard,transaction.getTransactionManager());
    }
    @Test void actionPopupCommitsBehindTheFenceAndPublishesCandidateRevision() {
        var action=action();
        assertNotNull(action.submit(task(),"action",Map.of("remark","saved"),"actor"));
        assertEquals(1,count()); assertEquals(1,revision()); verify(entities).clear();
    }
    @Test void staleActionPacketCannotReachItsInstanceReadOrRowInsert() {
        var action=action(); clearInvocations(instances); stale=true;
        assertThrows(AssignmentFailure.class,()->action.submit(task(),"action",Map.of("remark","saved"),"actor"));
        assertEquals(0,count()); assertEquals(0,revision()); verifyNoInteractions(instances);
    }
    @Test void noteAndInlineAttachmentEnterTheActualWriteTransactionBeforePersistence() {
        var component=noteComponent();
        assertNotNull(component.createComment("actor",target(),null,"<p>saved</p>",List.of(),List.of(),caseId.toString(),"task"));
        assertEquals(1,count());
        assertNotNull(component.createInlineImage("actor",target(),new MockMultipartFile("image","image.png","image/png",new byte[]{1,2,3}),caseId.toString(),"task"));
        assertEquals(2,count()); assertEquals(0,revision());
    }
    @Test void taskNotesHaveNoImplicitAdministratorBypassOfTheFinalFence() {
        var component=noteComponent(); stale=true;
        assertThrows(AssignmentFailure.class,()->component.createComment("actor",target(),null,"<p>denied</p>",List.of(),List.of(),caseId.toString(),"task"));
        assertThrows(AssignmentFailure.class,()->component.createInlineImage("actor",target(),new MockMultipartFile("image",new byte[]{1}),caseId.toString(),"task"));
        assertEquals(0,count()); verifyNoInteractions(notes);
    }
    private ActionFormPopupSubmitComponent action() {
        var lookup=mock(ActionDefinitionLookupComponent.class); var snapshots=mock(CatalogSnapshotReader.class); var rows=mock(CatalogRuntimeRows.class);
        var displays=mock(UserDisplayNameResolver.class); when(displays.resolve("actor")).thenReturn("Actor");
        when(lookup.resolvePinned("catalog",List.of("action"))).thenReturn(List.of(ActionDefinition.builder().sourceId("source").actionType("FORM_POPUP").configJson("{}").build()));
        when(snapshots.parse("{}")).thenReturn(Map.of("formId","form"));
        when(snapshots.form("catalog","form")).thenReturn(Map.of("tableBindings",List.of(Map.of("bindingType","ACTION","tableId",17,"bindingId",18))));
        when(snapshots.table("catalog","17")).thenReturn(Map.of("fields",List.of(Map.of("fieldName","remark"))));
        when(snapshots.mainTable("catalog")).thenReturn(Map.of("tableName","main"));
        when(instances.findById(caseId.toString())).thenAnswer(call->{verify(entities).clear();return Optional.of(ProcessInstance.builder().id(caseId.toString()).functionUnitCatalogId("catalog").functionUnitCode("fu").variables(Map.of()).build());});
        when(rows.insert(eq("catalog"),eq("17"),eq(caseId.toString()),anyMap(),eq("Actor"))).thenAnswer(call->{write("ACTION");return "row";});
        var action=new ActionFormPopupSubmitComponent(json,lookup,instances,snapshots,rows,mock(PrimaryKeyAllocationService.class),displays);
        ReflectionTestUtils.setField(action,"assignmentWrites",writes); return action;
    }
    private RecordNoteComponent noteComponent() {
        var catalog=mock(RecordNoteCatalogRepository.class); var access=mock(FunctionUnitAccessComponent.class);
        when(access.isSystemAdministrator("actor")).thenReturn(true);
        var submission=new RecordNoteSubmissionComponent(notes,new RecordNoteSubmissionRepository(jdbc,json),catalog);
        var component=new RecordNoteComponent(notes,submission,catalog,mock(RecordNoteParticipantRepository.class),mock(ProcessComponent.class),access,
                mock(UserDisplayNameResolver.class),mock(ChangeHistoryComponent.class),mock(WorkflowEngineClient.class),mock(TaskPermissionEvaluator.class),mock(RecordNoteUploadSourceResolver.class));
        ReflectionTestUtils.setField(component,"assignmentWrites",writes);
        when(notes.createComment(any(),any(),any(),any(),any(),anyString(),any())).thenAnswer(call->{write("NOTE");return NoteItem.builder().id("note").build();});
        when(notes.createAttachment(any(),any(),eq(true),anyString(),any())).thenAnswer(call->{write("ATTACHMENT");return NoteItem.builder().id("image").build();});
        return component;
    }
    private TaskInfo task() { return TaskInfo.builder().taskId("task").processInstanceId(caseId.toString()).actions(List.of(TaskActionInfo.builder().actionId("action").build())).build(); }
    private NoteTarget target() { return NoteTarget.builder().targetType("TABLE").targetId(caseId.toString()).tableId("17").tableKind("DW").build(); }
    private void write(String kind) { verify(entities,atLeastOnce()).clear(); jdbc.update("INSERT INTO synthetic_assignment_writes(id,kind) VALUES(?,?)",caseId,kind); }
    private int count() { return jdbc.queryForObject("SELECT count(*) FROM synthetic_assignment_writes WHERE id=?",Integer.class,caseId); }
    private long revision() { return jdbc.queryForObject("SELECT revision FROM we_assignment_cases WHERE case_id=?",Long.class,caseId); }
}
