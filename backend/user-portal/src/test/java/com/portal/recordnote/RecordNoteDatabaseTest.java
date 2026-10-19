package com.portal.recordnote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.recordnote.*;
import com.portal.dto.RecordNoteDtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transaction and crash/concurrency evidence. A dedicated schema is created and removed. */
@EnabledIfEnvironmentVariable(named="NOTE_TEST_JDBC_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecordNoteDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    JdbcTemplate admin;
    RecordNoteSubmissionComponent submit;
    RecordNoteDeliveryComponent delivery;
    RecordNoteEngineClient engine;
    RecordNoteSubmissionRepository ledger;
    ObjectMapper mapper;

    @BeforeAll void start() throws Exception {
        admin=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NOTE_TEST_JDBC_URL"),
                System.getenv("NOTE_TEST_JDBC_USER"),System.getenv("NOTE_TEST_JDBC_PASSWORD")));
        RecordNoteDatabaseFixture.schema="note_test_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE SCHEMA "+com.platform.common.jdbc.SqlIdentifiers.requireIdentifier(RecordNoteDatabaseFixture.schema));
        context=new AnnotationConfigApplicationContext(RecordNoteDatabaseFixture.class);
        jdbc=context.getBean(JdbcTemplate.class); mapper=context.getBean(ObjectMapper.class);
        jdbc.execute("CREATE TABLE up_process_instance(id varchar(64) PRIMARY KEY,function_unit_catalog_id varchar(64),title text,variables jsonb)");
        jdbc.execute("CREATE TABLE up_user_preference(user_id varchar(64),language varchar(10))");
        jdbc.execute("CREATE TABLE sys_function_unit_contents(function_unit_id varchar(64),content_type varchar(32),content_data text)");
        jdbc.execute("CREATE TABLE sys_users(id varchar(64) PRIMARY KEY,status varchar(32),language varchar(10))");
        try(var connection=context.getBean(javax.sql.DataSource.class).getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(Path.of("../../deploy/init-scripts/00-schema/94-record-note-notifications.sql").toAbsolutePath().normalize()));
        }
        submit=context.getBean(RecordNoteSubmissionComponent.class);
        delivery=context.getBean(RecordNoteDeliveryComponent.class);
        engine=context.getBean(RecordNoteEngineClient.class);
        ledger=context.getBean(RecordNoteSubmissionRepository.class);
    }
    @AfterAll void stop() {
        if(context!=null) context.close();
        if(admin!=null) admin.execute("DROP SCHEMA "+com.platform.common.jdbc.SqlIdentifiers.requireIdentifier(RecordNoteDatabaseFixture.schema)+" CASCADE");
    }
    @BeforeEach void fixture() throws Exception {
        reset(engine);
        var access=context.getBean(RecordNoteRecipientAccess.class);
        reset(access);
        when(access.allowed(anyString(),anySet())).thenAnswer(call -> call.getArgument(1));
        jdbc.execute("TRUNCATE up_record_note_deliveries,up_record_note_delivery_targets,up_record_note_submissions,up_record_note,up_notification,up_notification_preference,up_process_instance,sys_function_unit_contents,sys_users CASCADE");
        for(String user:List.of("uploader","person-a","person-b")) jdbc.update("INSERT INTO sys_users(id,status) VALUES (?,'ACTIVE')",user);
        jdbc.update("INSERT INTO up_process_instance VALUES ('request','catalog','Sample Request',CAST(? AS jsonb))",json(Map.of("__subTables__",Map.of("dw:odd_lines",List.of(
                Map.of("strange_pk","r1","handler_x9","person-a"),Map.of("strange_pk","r2","handler_x9","person-b"),
                Map.of("strange_pk","r3","handler_x9","person-a"))))));
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('catalog','PROCESS',?)", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:f="http://flowable.org/bpmn">
                  <process id="arbitrary"><subProcess id="scope"><extensionElements><f:properties>
                  <f:property name="subTableName" value="odd_lines"/><f:property name="assigneeField" value="handler_x9"/>
                  </f:properties></extensionElements><multiInstanceLoopCharacteristics/>
                  <userTask id="task-node"/></subProcess></process></definitions>
                """);
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('catalog','DATA_TABLE',?)",json(Map.of("tableId",771,"tableName","odd_lines","fields",List.of(Map.of("fieldName","strange_pk","isPrimaryKey",true)))));
        var notes=new ArrayList<Map<String,Object>>();
        for(String scope:List.of("TABLE","RECORD")) notes.add(Map.of("type","recordNote","props",Map.of("scope",scope,"formKey","form", "controlId",scope,
                "attachmentNotification",Map.of("enabled",true,"targetNodeKeys",List.of("task-node")))));
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('catalog','FORM',?)",json(Map.of("formName","form","boundTableName","odd_lines","configJson",Map.of("rule",notes))));
        runtime(List.of(task("r1","PERSON","ACTIVE",null),task("r2","PERSON","ACTIVE",null),task("r3","PERSON","ACTIVE",null)));
    }
    String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+com.platform.common.jdbc.SqlIdentifiers.requireIdentifier(table),Integer.class); }
    NoteTarget target(String scope,String row) {
        return NoteTarget.builder().targetType(scope).targetId(row).tableKind("DW").tableId("771")
                .processInstanceId("request").formKey("form").controlId(scope).clientSubmissionId(UUID.randomUUID().toString()).build();
    }
    MultipartFile file(String name) { return new MockMultipartFile("files",name,"application/pdf",new byte[]{1,2,3}); }
    NoteItem upload(NoteTarget target) { return submit.submit(new com.portal.recordnote.RecordNoteSubmissionComponent.Submission("uploader","Uploader",target,null,null,List.of(file("a.pdf")),List.of())); }
    RecordNoteTaskTarget task(String row,String mode,String state,String user) {
        return new RecordNoteTaskTarget("t-"+row,"task-node","mi-scope","odd_lines",Map.of("strange_pk",row),"round-2",mode,"handler_x9",user,state,Map.of());
    }
    void runtime(List<RecordNoteTaskTarget> tasks) {
        when(engine.resolve(eq("request"),anyList())).thenReturn(new RecordNoteRuntimeContext(
                List.of(new RecordNoteRuntimeContext.TargetDefinition("task-node","mi-scope","odd_lines","handler_x9","pool_role")),tasks,false));
    }
    @Test void attachmentOnlySubmissionCountsAllFilesAndRetriesWithoutDuplicates() {
        NoteTarget target=target("TABLE","request");
        var first=submit.submit(new com.portal.recordnote.RecordNoteSubmissionComponent.Submission("uploader","Uploader",target,null,null,List.of(file("a.pdf"),file("b.pdf")),List.of()));
        var retry=submit.submit(new com.portal.recordnote.RecordNoteSubmissionComponent.Submission("uploader","Uploader",target,null,null,List.of(file("a.pdf"),file("b.pdf")),List.of()));
        assertEquals(first.getSubmissionId(),retry.getSubmissionId());
        assertEquals(2,count("up_record_note")); assertEquals(1,count("up_record_note_submissions"));
        assertEquals(2,jdbc.queryForObject("SELECT attachment_count FROM up_record_note_submissions",Integer.class));
    }
    @Test void sameKeyWithDifferentContentIsRejected() {
        NoteTarget target=target("TABLE","request"); upload(target);
        assertThrows(RuntimeException.class,() -> submit.submit(new com.portal.recordnote.RecordNoteSubmissionComponent.Submission("uploader","Uploader",target,null,null,List.of(file("different.pdf")),List.of())));
        assertEquals(1,count("up_record_note"));
    }
    @Test void failedSecondAttachmentRollsBackFirstFileAndIntent() {
        MultipartFile invalid=new MockMultipartFile("files","big.pdf","application/pdf",new byte[10*1024*1024+1]);
        assertThrows(RuntimeException.class,() -> submit.submit(new com.portal.recordnote.RecordNoteSubmissionComponent.Submission("uploader","Uploader",target("TABLE","request"),null,null,List.of(file("a.pdf"),invalid),List.of())));
        assertEquals(0,count("up_record_note")); assertEquals(0,count("up_record_note_submissions"));
    }
    @Test void textAndInlineImageBodiesDoNotProduceAttachmentNotifications() {
        var note=submit.submit(new com.portal.recordnote.RecordNoteSubmissionComponent.Submission("uploader","Uploader",target("TABLE","request"),null,"<p>Text</p>",List.of(),List.of()));
        delivery.deliver(note.getSubmissionId());
        assertEquals("DISABLED",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
        assertEquals(0,count("up_notification"));
    }
    @Test void caseNotesDeduplicateUsersAcrossRows() {
        delivery.deliver(upload(target("TABLE","request")).getSubmissionId());
        assertEquals(2,count("up_notification"));
        assertEquals(Set.of("person-a","person-b"),new HashSet<>(jdbc.queryForList("SELECT user_id FROM up_notification",String.class)));
    }
    @Test void savedApprovalCopiesCannotPolluteTheCurrentRecipientScope() throws Exception {
        jdbc.update("UPDATE up_process_instance SET variables=variables || CAST(? AS jsonb)",json(Map.of(
                "_snapshot_previous",Map.of("__subTables__",Map.of("dw:odd_lines",List.of(
                        Map.of("strange_pk","r1","handler_x9","uploader")))))));
        delivery.deliver(upload(target("TABLE","request")).getSubmissionId());
        assertEquals(Set.of("person-a","person-b"),new HashSet<>(jdbc.queryForList("SELECT user_id FROM up_notification",String.class)));
        assertEquals("DELIVERED",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
    }
    @Test void caseNotesAggregateDifferentSubTaskTablesAndDeduplicateTheSamePerson() throws Exception {
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('catalog','DATA_TABLE',?)",json(Map.of(
                "tableId",882,"tableName","other_lines","fields",List.of(Map.of("fieldName","other_pk","isPrimaryKey",true)))));
        jdbc.execute("UPDATE sys_function_unit_contents SET content_data=jsonb_set(content_data::jsonb,"+
                "'{configJson,rule,0,props,attachmentNotification,targetNodeKeys}','[\"task-node\",\"other-task\"]')::text WHERE content_type='FORM'");
        jdbc.update("UPDATE sys_function_unit_contents SET content_data=replace(content_data,'</process>',?) WHERE content_type='PROCESS'", """
                <subProcess id="other-scope"><extensionElements><f:properties>
                <f:property name="subTableName" value="other_lines"/><f:property name="assigneeField" value="other_person"/>
                </f:properties></extensionElements><multiInstanceLoopCharacteristics/><userTask id="other-task"/></subProcess></process>
                """);
        jdbc.update("UPDATE up_process_instance SET variables=jsonb_set(variables,'{__subTables__,dw:other_lines}',CAST(? AS jsonb))",
                json(List.of(Map.of("other_pk",9,"other_person","person-a"),Map.of("other_pk",10,"other_person","uploader"))));
        var tasks=new ArrayList<>(List.of(task("r1","PERSON","ACTIVE",null),task("r2","PERSON","ACTIVE",null),task("r3","PERSON","ACTIVE",null)));
        for(int key:List.of(9,10)) tasks.add(new RecordNoteTaskTarget("other-"+key,"other-task","other-scope","other_lines",
                Map.of("other_pk",key),"other-round","PERSON","other_person",null,"ACTIVE",Map.of()));
        when(engine.resolve(eq("request"),anyList())).thenReturn(new RecordNoteRuntimeContext(List.of(
                new RecordNoteRuntimeContext.TargetDefinition("task-node","scope","odd_lines","handler_x9",null),
                new RecordNoteRuntimeContext.TargetDefinition("other-task","other-scope","other_lines","other_person",null)),tasks,false));
        delivery.deliver(upload(target("TABLE","request")).getSubmissionId());
        assertEquals(Set.of("person-a","person-b"),new HashSet<>(jdbc.queryForList("SELECT user_id FROM up_notification",String.class)));
        assertEquals(5,count("up_record_note_delivery_targets"));
    }
    @Test void transactionNotesDoNotNotifySiblingRows() {
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        assertEquals(List.of("person-b"),jdbc.queryForList("SELECT user_id FROM up_notification",String.class));
    }
    @Test void unclaimedTeamWaitsThenNotifiesOnlyTheEffectiveClaimer() {
        runtime(List.of(task("r2","TEAM","WAITING_CLAIM",null)));
        String id=upload(target("RECORD","r2")).getSubmissionId(); delivery.deliver(id);
        assertEquals(0,count("up_notification"));
        assertEquals("WAITING_TARGET",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
        runtime(List.of(task("r2","TEAM","ACTIVE","person-a"))); delivery.deliver(id);
        assertEquals(List.of("person-a"),jdbc.queryForList("SELECT user_id FROM up_notification",String.class));
    }
    @Test void automaticallyAssignedSoleTeamMemberIsARecipient() {
        runtime(List.of(task("r2","TEAM","ACTIVE","person-b")));
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        assertEquals(List.of("person-b"),jdbc.queryForList("SELECT user_id FROM up_notification",String.class));
    }
    @Test void stageActivationAndReworkResolveNewRoundInsteadOfOldPerson() {
        runtime(List.of()); String id=upload(target("RECORD","r2")).getSubmissionId(); delivery.deliver(id);
        assertEquals(0,count("up_notification"));
        runtime(List.of(task("r2","TEAM","ACTIVE","person-a"))); delivery.deliver(id);
        assertEquals(List.of("person-a"),jdbc.queryForList("SELECT user_id FROM up_notification",String.class));
    }
    @Test void completedPersonUsesFinalSnapshotRatherThanLivePersonnelField() {
        runtime(List.of(task("r2","PERSON","COMPLETED","person-a")));
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        assertEquals(List.of("person-a"),jdbc.queryForList("SELECT user_id FROM up_notification",String.class));
    }
    @Test void uploaderIsExcludedEvenWhenConfiguredAsHandler() {
        runtime(List.of(task("r2","TEAM","ACTIVE","uploader")));
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId()); assertEquals(0,count("up_notification"));
    }
    @Test void notificationPreferencesHaveAnExplicitDeliveryOutcome() {
        jdbc.update("INSERT INTO up_notification_preference(user_id,notification_type,in_app_enabled) VALUES ('person-b','ATTACHMENT_UPDATE',false)");
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        assertEquals(0,count("up_notification"));
        assertEquals("PREFERENCE_DISABLED",jdbc.queryForObject("SELECT state FROM up_record_note_deliveries",String.class));
    }
    @Test void attachmentCategoryIsIndependentFromTaskAndProcessFilters() {
        jdbc.update("INSERT INTO up_notification_preference(user_id,notification_type,in_app_enabled) VALUES ('person-b','PROCESS',false)");
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        var service=context.getBean(com.portal.service.NotificationService.class);
        assertEquals(1,service.getNotifications("person-b",0,20,"ATTACHMENT_UPDATE",null).getTotalElements());
        assertEquals(0,service.getNotifications("person-b",0,20,"PROCESS",null).getTotalElements());
        assertEquals(0,service.getNotifications("person-b",0,20,"TASK",null).getTotalElements());
    }
    @Test void migrationUsesDeliveryLedgerPreservesHistoryAndIsRepeatable() throws Exception {
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        jdbc.update("UPDATE up_notification SET type='PROCESS',is_read=true");
        var before=jdbc.queryForMap("SELECT id,title,content,link,is_read,created_at FROM up_notification");
        jdbc.update("INSERT INTO up_notification(user_id,type,title,content,is_read,created_at) VALUES ('person-b','PROCESS','Other process','Unrelated',false,now())");
        jdbc.update("INSERT INTO up_notification_preference(user_id,notification_type,in_app_enabled,quiet_start_time) VALUES ('person-a','PROCESS',false,'22:00')");
        jdbc.update("INSERT INTO up_notification_preference(user_id,notification_type,in_app_enabled) VALUES ('person-b','ATTACHMENT_UPDATE',false)");
        for(int i=0;i<2;i++) try(var connection=context.getBean(javax.sql.DataSource.class).getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(Path.of("../../deploy/init-scripts/00-schema/95-record-note-attachment-category.sql").toAbsolutePath().normalize()));
        }
        assertEquals(before,jdbc.queryForMap("SELECT id,title,content,link,is_read,created_at FROM up_notification WHERE type='ATTACHMENT_UPDATE'"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM up_notification WHERE type='PROCESS'",Integer.class));
        assertEquals(false,jdbc.queryForObject("SELECT in_app_enabled FROM up_notification_preference WHERE user_id='person-a' AND notification_type='ATTACHMENT_UPDATE'",Boolean.class));
        assertEquals("22:00:00",jdbc.queryForObject("SELECT quiet_start_time::text FROM up_notification_preference WHERE user_id='person-a' AND notification_type='ATTACHMENT_UPDATE'",String.class));
        assertEquals(false,jdbc.queryForObject("SELECT in_app_enabled FROM up_notification_preference WHERE user_id='person-b' AND notification_type='ATTACHMENT_UPDATE'",Boolean.class));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM up_notification_preference WHERE notification_type='ATTACHMENT_UPDATE'",Integer.class));
    }
    @Test void lazyPreferenceInitializationInheritsOptOutAndPreservesLaterChanges() {
        var preferences=context.getBean(com.portal.repository.NotificationPreferenceRepository.class);
        jdbc.update("INSERT INTO up_notification_preference(user_id,notification_type,in_app_enabled) VALUES ('person-a','PROCESS',false)");
        new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .executeWithoutResult(status -> preferences.ensureAttachmentUpdatePreference("person-a"));
        assertFalse(preferences.findByUserIdAndNotificationType("person-a","ATTACHMENT_UPDATE").orElseThrow().getInAppEnabled());
        jdbc.update("UPDATE up_notification_preference SET in_app_enabled=true WHERE notification_type='ATTACHMENT_UPDATE'");
        new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .executeWithoutResult(status -> preferences.ensureAttachmentUpdatePreference("person-a"));
        assertTrue(preferences.findByUserIdAndNotificationType("person-a","ATTACHMENT_UPDATE").orElseThrow().getInAppEnabled());
        new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .executeWithoutResult(status -> preferences.ensureAttachmentUpdatePreference("uploader"));
        assertTrue(preferences.findByUserIdAndNotificationType("uploader","ATTACHMENT_UPDATE").orElseThrow().getInAppEnabled());
    }
    @Test void uploadSourceSnapshotSurvivesWaitingTaskRenameAndExactRetry() {
        NoteTarget target=target("RECORD","r2"); target.setTaskId("original-upload-task");
        target.setVerifiedUploadSource(new UploadSource(UploadSourceKind.TASK,"original-upload-task","Credit Card Transaction"));
        runtime(List.of()); String id=upload(target).getSubmissionId(); delivery.deliver(id);
        assertEquals(0,count("up_notification"));
        target.setVerifiedUploadSource(new UploadSource(UploadSourceKind.TASK,"original-upload-task","Renamed task"));
        assertEquals(id,upload(target).getSubmissionId());
        runtime(List.of(task("r2","TEAM","COMPLETED","person-b"))); delivery.deliver(id);
        String content=jdbc.queryForObject("SELECT content FROM up_notification",String.class);
        assertTrue(content.startsWith("Sample Request (request)\n"));
        assertTrue(content.endsWith("Uploaded from: Credit Card Transaction"));
        assertFalse(content.contains("Renamed task"));
        target.setTaskId("different-upload-task");
        assertThrows(RuntimeException.class,()->upload(target));
        assertEquals(1,count("up_record_note")); assertEquals(1,count("up_notification"));
    }
    @Test void legacyFingerprintRemainsReplayableAfterDeployment() throws Exception {
        NoteTarget target=target("TABLE","request"); String id=upload(target).getSubmissionId();
        var fields=java.util.Arrays.asList(target.getTargetType(),target.getTargetId(),target.getTableKind(),
                target.getTableId(),target.getProcessInstanceId(),target.getFormKey(),target.getControlId(),null,null,List.of());
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        digest.update(json(fields).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(json(java.util.Arrays.asList("a.pdf","application/pdf",3L)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[]{1,2,3}));
        jdbc.update("UPDATE up_record_note_submissions SET fingerprint=?,policy=policy-'fingerprintVersion' WHERE id=?",
                java.util.HexFormat.of().formatHex(digest.digest()),id);
        assertEquals(id,upload(target).getSubmissionId()); assertEquals(1,count("up_record_note"));
    }
    @Test void twoConcurrentWorkersCannotCreateDuplicateNotifications() throws Exception {
        String id=upload(target("RECORD","r2")).getSubmissionId();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try { var jobs=pool.invokeAll(List.of(Executors.callable(() -> delivery.deliver(id)),Executors.callable(() -> delivery.deliver(id))));
            for(var job:jobs) job.get(10,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1,count("up_notification")); assertEquals(1,count("up_record_note_deliveries"));
    }
    @Test void crashAfterNotificationInsertRollsBackAndCanBeRetried() {
        String id=upload(target("RECORD","r2")).getSubmissionId();
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_note_delivery() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''forced test crash''; END'");
        jdbc.execute("CREATE TRIGGER crash_note_delivery BEFORE UPDATE ON up_record_note_deliveries FOR EACH ROW EXECUTE FUNCTION fail_note_delivery()");
        try { assertThrows(RuntimeException.class,() -> delivery.deliver(id)); }
        finally { jdbc.execute("DROP TRIGGER crash_note_delivery ON up_record_note_deliveries"); }
        assertEquals(0,count("up_notification")); assertEquals(0,count("up_record_note_deliveries"));
        delivery.deliver(id); assertEquals(1,count("up_notification"));
    }
    @Test void deletingAnAlreadyDeliveredNotificationDoesNotRecreateIt() {
        String id=upload(target("RECORD","r2")).getSubmissionId(); delivery.deliver(id);
        jdbc.execute("DELETE FROM up_notification"); delivery.deliver(id); assertEquals(0,count("up_notification"));
    }
    @Test void forgedTransactionRowAndControlAreRejectedBeforePersistence() {
        assertThrows(RuntimeException.class,() -> upload(target("RECORD","another-request-row")));
        NoteTarget target=target("RECORD","r2"); target.setControlId("forged");
        assertThrows(RuntimeException.class,() -> upload(target));
        assertEquals(0,count("up_record_note")); assertEquals(0,count("up_record_note_submissions"));
    }
    @Test void permissionLossWaitsWithoutLeakingRequestMetadataThenCanResume() {
        var access=context.getBean(RecordNoteRecipientAccess.class);
        when(access.allowed(anyString(),anySet())).thenReturn(Set.of());
        String id=upload(target("RECORD","r2")).getSubmissionId(); delivery.deliver(id);
        assertEquals(0,count("up_notification"));
        assertEquals("WAITING_PERMISSION",jdbc.queryForObject("SELECT state FROM up_record_note_delivery_targets",String.class));
        when(access.allowed(anyString(),anySet())).thenAnswer(call -> call.getArgument(1));
        delivery.deliver(id); assertEquals(1,count("up_notification"));
    }
    @Test void deliveryUsesTheRecipientLanguageAndRequestName() {
        jdbc.update("UPDATE sys_users SET language='zh-TW' WHERE id='person-b'");
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        String content=jdbc.queryForObject("SELECT content FROM up_notification",String.class);
        assertTrue(content.contains("個附件")); assertTrue(content.contains("Sample Request"));
    }
    @Test void concurrentRetriesOfTheSameUploadPersistOnlyOneSetOfFiles() throws Exception {
        String client=UUID.randomUUID().toString();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<NoteItem> job=() -> { var target=target("RECORD","r2"); target.setClientSubmissionId(client); return upload(target); };
            var results=pool.invokeAll(List.of(job,job));
            assertEquals(results.get(0).get(10,TimeUnit.SECONDS).getSubmissionId(),results.get(1).get(10,TimeUnit.SECONDS).getSubmissionId());
        } finally { pool.shutdownNow(); }
        assertEquals(1,count("up_record_note")); assertEquals(1,count("up_record_note_submissions"));
    }
    @Test void unrelatedRowsWithoutIdentityDoNotPreventSubmittingAnAttachment() throws Exception {
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('catalog','DATA_TABLE',?)",json(Map.of("tableId",999,"tableName","unrelated","fields",List.of())));
        jdbc.update("UPDATE up_process_instance SET variables=variables || CAST(? AS jsonb)",json(Map.of("other",Map.of("__subTables__",Map.of("dw:unrelated",List.of(Map.of("free_text","value")))))));
        delivery.deliver(upload(target("RECORD","r2")).getSubmissionId());
        assertEquals(1,count("up_notification"));
    }
    @Test void importedNestedCompositeKeyRowsWorkWithoutAnyFamiliarFieldNames() throws Exception {
        Map<String,Object> row=Map.of("part_alpha",73,"part_beta","Z","platformRowUuid","stable-row","arb_handler",Map.of("id","person-b"));
        jdbc.update("UPDATE sys_function_unit_contents SET content_data=? WHERE content_type='DATA_TABLE'",json(Map.of(
                "tableId",771,"tableName","anonymous_nested","fields",List.of(Map.of("fieldName","part_alpha","isPrimaryKey",true),Map.of("fieldName","part_beta","isPrimaryKey",true)))));
        jdbc.update("UPDATE sys_function_unit_contents SET content_data=replace(replace(content_data,'odd_lines','anonymous_nested'),'handler_x9','arb_handler') WHERE content_type IN ('PROCESS','FORM')");
        jdbc.update("UPDATE up_process_instance SET variables=CAST(? AS jsonb)",json(Map.of("__subTables__",Map.of("dw:parent_rows",List.of(
                Map.of("parent_key","p","__subTables__",Map.of("dw:anonymous_nested",List.of(row))))))));
        when(engine.resolve(eq("request"),anyList())).thenReturn(new RecordNoteRuntimeContext(
                List.of(new RecordNoteRuntimeContext.TargetDefinition("task-node","nested-scope","anonymous_nested","arb_handler",null)),
                List.of(new RecordNoteTaskTarget("nested-task","task-node","nested-scope","anonymous_nested",Map.of("part_alpha",73,"part_beta","Z"),
                        "activation-3","PERSON","arb_handler",null,"ACTIVE",Map.of())),false));
        delivery.deliver(upload(target("RECORD","stable-row")).getSubmissionId());
        assertEquals(List.of("person-b"),jdbc.queryForList("SELECT user_id FROM up_notification",String.class));
    }

    void migrate() throws Exception {
        try(var connection=context.getBean(javax.sql.DataSource.class).getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(Path.of("../../deploy/init-scripts/00-schema/94-record-note-notifications.sql").toAbsolutePath().normalize()));
        }
    }
    @Test void legacyRowNotesAreBackfilledOnlyWhenTheirOwningRequestIsUnique() throws Exception {
        upload(target("RECORD","r2")); jdbc.execute("UPDATE up_record_note SET process_instance_id=NULL");
        migrate(); assertEquals("request",jdbc.queryForObject("SELECT process_instance_id FROM up_record_note",String.class));
        jdbc.execute("UPDATE up_record_note SET process_instance_id=NULL");
        jdbc.execute("INSERT INTO up_process_instance SELECT 'another-request',function_unit_catalog_id,title,variables FROM up_process_instance WHERE id='request'");
        migrate(); assertNull(jdbc.queryForObject("SELECT process_instance_id FROM up_record_note",String.class));
    }
    @Test void repeatedMigrationRetainsLegacyRequestAnchorsAndCommittedDeliveryTombstones() throws Exception {
        var note=upload(target("TABLE","request")); delivery.deliver(note.getSubmissionId());
        jdbc.execute("UPDATE up_record_note SET target_type='RECORD',process_instance_id=NULL");
        migrate(); migrate();
        assertEquals("request",jdbc.queryForObject("SELECT process_instance_id FROM up_record_note",String.class));
        assertEquals(2,count("up_record_note_deliveries"));
    }

    @Test void missingHistoricalRowIdentityKeepsAnIntentAndNeverBlocksTheAttachment() throws Exception {
        jdbc.update("UPDATE up_process_instance SET variables=CAST(? AS jsonb)",json(Map.of("__subTables__",Map.of("dw:odd_lines",List.of(
                Map.of("handler_x9","person-a"),Map.of("strange_pk","r2","handler_x9","person-b"))))));
        String id=upload(target("TABLE","request")).getSubmissionId(); delivery.deliver(id);
        assertEquals(1,count("up_record_note")); assertEquals(1,count("up_notification"));
        assertEquals("WAITING_TARGET",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
        jdbc.update("UPDATE up_process_instance SET variables=CAST(? AS jsonb)",json(Map.of("__subTables__",Map.of("dw:odd_lines",List.of(
                Map.of("strange_pk","r1","handler_x9","person-a"),Map.of("strange_pk","r2","handler_x9","person-b"))))));
        delivery.deliver(id); assertEquals(2,count("up_notification"));
    }
    @Test void designerReadOnlyIsEnforcedForTaskUploadsWhileAuditRemainsASeparateEntry() {
        jdbc.execute("UPDATE sys_function_unit_contents SET content_data=jsonb_set(content_data::jsonb,'{configJson,rule,0,props,readonly}','true')::text WHERE content_type='FORM'");
        var target=target("TABLE","request"); target.setTaskId("verified-task");
        assertThrows(RuntimeException.class,() -> upload(target)); assertEquals(0,count("up_record_note"));
        assertDoesNotThrow(() -> upload(target("TABLE","request")));
    }
    @Test void aLegacyRequestWithoutAFrozenCatalogCanSaveCaseAttachmentsAndWaitForTrustedMetadata() {
        jdbc.execute("UPDATE up_process_instance SET function_unit_catalog_id=NULL");
        String id=upload(target("TABLE","request")).getSubmissionId();
        delivery.deliver(id);
        assertEquals(1,count("up_record_note")); assertEquals(0,count("up_notification"));
        assertEquals("WAITING_METADATA",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
        jdbc.execute("UPDATE up_process_instance SET function_unit_catalog_id='catalog'");
        delivery.deliver(id);
        assertEquals(2,count("up_notification"));
        assertEquals("DELIVERED",jdbc.queryForObject("SELECT state FROM up_record_note_submissions",String.class));
    }

}
