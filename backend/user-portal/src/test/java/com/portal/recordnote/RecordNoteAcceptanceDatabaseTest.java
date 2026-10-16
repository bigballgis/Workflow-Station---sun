package com.portal.recordnote;

import com.platform.common.recordnote.RecordNoteRuntimeContext;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Additional acceptance probes from the 2026-10-16 plan, in the inherited isolated PostgreSQL schema. */
class RecordNoteAcceptanceDatabaseTest extends RecordNoteDatabaseTest {
    @Test void acceptanceDisabledControlSavesFilesWithoutNotifying() {
        jdbc.execute("UPDATE sys_function_unit_contents SET content_data=jsonb_set(content_data::jsonb,'{configJson,rule,0,props,attachmentNotification,enabled}','false')::text WHERE content_type='FORM'");
        String id=upload(target("TABLE","request")).getSubmissionId(); delivery.deliver(id);
        assertEquals(1,count("up_record_note")); assertEquals(0,count("up_notification"));
        assertEquals("DISABLED",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
    }
    @Test void acceptancePersonUsesConfiguredFieldInsteadOfActiveTaskAssignee() {
        runtime(List.of(task("r2","PERSON","ACTIVE","person-a")));
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        assertEquals(List.of("person-b"),jdbc.queryForList("SELECT user_id FROM up_notification",String.class));
    }
    @Test void acceptanceInactiveRecipientDoesNotExpandToOtherPeople() {
        jdbc.update("UPDATE sys_users SET status='DISABLED' WHERE id='person-b'");
        String id=upload(target("RECORD","r2")).getSubmissionId(); delivery.deliver(id);
        assertEquals(0,count("up_notification"));
        assertEquals("WAITING_TARGET",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
    }
    @Test void acceptancePersonReassignmentBeforeDeliveryUsesCurrentField() {
        String id=upload(target("RECORD","r2")).getSubmissionId();
        jdbc.execute("UPDATE up_process_instance SET variables=jsonb_set(variables,'{__subTables__,dw:odd_lines,1,handler_x9}','\"person-a\"')");
        delivery.deliver(id);
        assertEquals(List.of("person-a"),jdbc.queryForList("SELECT user_id FROM up_notification",String.class));
    }
    @Test void acceptanceCompletedTeamUsesFinalHandler() {
        runtime(List.of(task("r2","TEAM","COMPLETED","person-a")));
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        assertEquals(List.of("person-a"),jdbc.queryForList("SELECT user_id FROM up_notification",String.class));
    }
    @Test void acceptanceTerminatedRequestStopsAcceptedIntent() {
        String id=upload(target("RECORD","r2")).getSubmissionId();
        org.mockito.Mockito.when(engine.resolve(org.mockito.ArgumentMatchers.eq("request"),org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new RecordNoteRuntimeContext(List.of(),List.of(),true));
        delivery.deliver(id); assertEquals(0,count("up_notification"));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
    }
    @Test void acceptanceSeparateSubmissionsOfIdenticalFileAreBothUpdates() {
        String first=upload(target("RECORD","r2")).getSubmissionId();
        String second=upload(target("RECORD","r2")).getSubmissionId();
        delivery.deliver(first);delivery.deliver(second);
        assertNotEquals(first,second);assertEquals(2,count("up_record_note"));assertEquals(2,count("up_notification"));
    }
    @Test void acceptanceRetryLimitIsDurableAndBacksOff() {
        String id=upload(target("RECORD","r2")).getSubmissionId();
        for(int i=0;i<10;i++)ledger.retry(id);
        assertEquals(10,jdbc.queryForObject("SELECT attempts FROM up_record_note_submissions",Integer.class));
        assertEquals("FAILED",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at > now()+interval '10 minutes' FROM up_record_note_submissions",Boolean.class));
        assertEquals(1,count("up_record_note"));assertEquals(0,count("up_notification"));
    }
    @Test void acceptanceWaitingQueueCannotStarveNewDueSubmission() {
        for(int i=0;i<25;i++) {
            String id=upload(target("RECORD","r2")).getSubmissionId();ledger.state(id,"WAITING_TARGET",null);
        }
        String fresh=upload(target("RECORD","r2")).getSubmissionId();
        assertEquals(List.of(fresh),ledger.due());
    }
    @Test void acceptanceRequestNameIsReadAtDeliveryAndThenPersists() {
        String id=upload(target("RECORD","r2")).getSubmissionId();
        jdbc.update("UPDATE up_process_instance SET title='Renamed before delivery'");delivery.deliver(id);
        String text=jdbc.queryForObject("SELECT content FROM up_notification",String.class);
        assertTrue(text.startsWith("Renamed before delivery (request)\n"));
        jdbc.update("UPDATE up_process_instance SET title='Renamed after delivery'");delivery.deliver(id);
        assertEquals(text,jdbc.queryForObject("SELECT content FROM up_notification",String.class));
    }
    @Test void acceptanceDesignerOneMiBLimitIsEnforcedByBackend() {
        jdbc.execute("UPDATE sys_function_unit_contents SET content_data=jsonb_set(content_data::jsonb,'{configJson,rule,1,props,maxFileSizeMb}','1')::text WHERE content_type='FORM'");
        var file=new MockMultipartFile("files","above-control-limit.pdf","application/pdf",new byte[1024*1024+1]);
        assertThrows(RuntimeException.class,()->submit.submit(new RecordNoteSubmissionComponent.Submission("uploader","Uploader",target("RECORD","r2"),null,null,List.of(file),List.of())));
        assertEquals(0,count("up_record_note"));assertEquals(0,count("up_record_note_submissions"));
    }
    @Test void acceptanceTwentyConcurrentIndependentSubmissionsRemainDistinct() throws Exception {
        long started=System.nanoTime();var pool=Executors.newFixedThreadPool(5);
        try {
            List<Callable<String>> work=new ArrayList<>();
            for(int i=0;i<20;i++)work.add(()->upload(target("RECORD","r2")).getSubmissionId());
            Set<String> ids=new HashSet<>();for(var future:pool.invokeAll(work))ids.add(future.get(30,TimeUnit.SECONDS));
            assertEquals(20,ids.size());
            List<Callable<Void>> sends=new ArrayList<>();for(String id:ids)sends.add(()->{delivery.deliver(id);return null;});
            for(var future:pool.invokeAll(sends))future.get(30,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertEquals(20,count("up_record_note"));assertEquals(20,count("up_notification"));assertEquals(20,count("up_record_note_deliveries"));
        System.out.println("PLAN_METRIC independentSubmissions=20 concurrency=5 elapsedMs="+(System.nanoTime()-started)/1_000_000);
    }
    @Test void acceptanceThousandRowsDeduplicateRecipients() throws Exception {
        List<Map<String,Object>> rows=new ArrayList<>();var tasks=new ArrayList<com.platform.common.recordnote.RecordNoteTaskTarget>();
        for(int i=0;i<1000;i++){String row="row-"+i;rows.add(Map.of("strange_pk",row,"handler_x9",i%2==0?"person-a":"person-b"));tasks.add(task(row,"PERSON","ACTIVE",null));}
        jdbc.update("UPDATE up_process_instance SET variables=CAST(? AS jsonb)",json(Map.of("__subTables__",Map.of("dw:odd_lines",rows))));runtime(tasks);
        long started=System.nanoTime();delivery.deliver(upload(target("TABLE","request")).getSubmissionId());
        assertEquals(1000,count("up_record_note_delivery_targets"));assertEquals(2,count("up_notification"));
        assertEquals(Set.of("person-a","person-b"),new HashSet<>(jdbc.queryForList("SELECT user_id FROM up_notification",String.class)));
        System.out.println("PLAN_METRIC rows=1000 recipients=2 elapsedMs="+(System.nanoTime()-started)/1_000_000);
    }
    @Test void acceptanceExactTenMiBServerBoundaryAndOversizeRollback() {
        var exact=new MockMultipartFile("files","exact-limit.pdf","application/pdf",new byte[10*1024*1024]);
        var saved=submit.submit(new RecordNoteSubmissionComponent.Submission("uploader","Uploader",target("RECORD","r2"),null,null,List.of(exact),List.of()));
        assertEquals(1,count("up_record_note"));
        var above=new MockMultipartFile("files","above-limit.pdf","application/pdf",new byte[10*1024*1024+1]);
        assertThrows(RuntimeException.class,()->submit.submit(new RecordNoteSubmissionComponent.Submission("uploader","Uploader",target("RECORD","r2"),null,null,List.of(above),List.of())));
        assertEquals(1,count("up_record_note"));assertEquals(1,count("up_record_note_submissions"));
    }
    @Test void acceptanceCategoryRollbackAndReupgradePreserveNotificationHistory() throws Exception {
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        jdbc.update("UPDATE up_notification SET is_read=true");
        var original=jdbc.queryForMap("SELECT id,title,content,link,is_read,created_at FROM up_notification");
        jdbc.update("UPDATE up_notification SET type='PROCESS' WHERE type='ATTACHMENT_UPDATE'");
        assertEquals("PROCESS",jdbc.queryForObject("SELECT type FROM up_notification",String.class));
        try(var connection=context.getBean(javax.sql.DataSource.class).getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource(java.nio.file.Path.of("../../deploy/init-scripts/00-schema/95-record-note-attachment-category.sql").toAbsolutePath().normalize()));
        }
        assertEquals("ATTACHMENT_UPDATE",jdbc.queryForObject("SELECT type FROM up_notification",String.class));
        assertEquals(original,jdbc.queryForMap("SELECT id,title,content,link,is_read,created_at FROM up_notification"));assertEquals(1,count("up_record_note_deliveries"));
    }
    @Test void acceptanceQuietHoursPersistAttachmentNotificationWithoutRealtimePush() {
        var push=org.mockito.Mockito.mock(com.portal.service.WebSocketNotificationService.class);
        var service=context.getBean(com.portal.service.NotificationService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"webSocketNotificationService",push);
        java.time.LocalTime start=java.time.LocalTime.now().minusHours(1),end=java.time.LocalTime.now().plusHours(1);
        jdbc.update("INSERT INTO up_notification_preference(user_id,notification_type,in_app_enabled,quiet_start_time,quiet_end_time) VALUES ('person-b','ATTACHMENT_UPDATE',true,?,?)",java.sql.Time.valueOf(start),java.sql.Time.valueOf(end));
        try {delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());assertEquals(1,count("up_notification"));org.mockito.Mockito.verifyNoInteractions(push);}
        finally {org.springframework.test.util.ReflectionTestUtils.setField(service,"webSocketNotificationService",null);}
    }

}
