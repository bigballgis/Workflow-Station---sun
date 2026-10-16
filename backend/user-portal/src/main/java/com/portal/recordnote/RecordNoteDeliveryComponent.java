package com.portal.recordnote;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.recordnote.*;
import com.platform.messaging.event.NotificationEvent;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** A delivery transaction owns the submission, stable task state and committed request row. */
@Component
@RequiredArgsConstructor
public class RecordNoteDeliveryComponent {
    private final RecordNoteSubmissionRepository submissions;
    private final RecordNoteDeliveryRepository deliveries;
    private final RecordNoteCatalogRepository catalog;
    private final RecordNoteEngineClient engine;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    private final RecordNoteRecipientAccess access;
    private final org.springframework.context.MessageSource messages;

    @Transactional
    public void deliver(String id) {
        Map<String,Object> submission=submissions.lock(id);
        if(submission==null || !Set.of("PENDING","WAITING_TARGET","WAITING_METADATA","RETRY").contains(submission.get("state"))) return;
        String processId=(String)submission.get("process_instance_id");
        RecordNoteRequestLock.acquire(jdbc,processId);
        // NOWAIT is essential: a Portal writer may be waiting for an engine call. Never wait on its row here.
        var request=catalog.request(processId,true);
        if(request.catalogId()==null) {
            submissions.state(id,"WAITING_METADATA","NOTE_CATALOG_UNRESOLVED"); return;
        }
        if(submission.get("catalog_id")==null) {
            submissions.attachCatalog(id,request.catalogId());
            submission.put("catalog_id",request.catalogId());
        }
        NoteTarget target=target(submission);
        var control=catalog.control(request,target);
        if(!control.enabled() || ((Number)submission.get("attachment_count")).intValue()==0) {
            submissions.state(id,"DISABLED",null); return;
        }
        if(!request.catalogId().equals(submission.get("catalog_id"))) throw new IllegalStateException("NOTE_CATALOG_CHANGED");
        RecordNoteRuntimeContext runtime=engine.resolve(processId,control.nodes());
        if(runtime.terminated()) { submissions.state(id,"CANCELLED",null); return; }
        initialize(id,submission,request,target,runtime);
        java.util.Set<String> candidates=new java.util.HashSet<>();
        runtime.tasks().forEach(task -> { String user=recipient(request,task); if(user!=null) candidates.add(user); });
        java.util.Set<String> activeUsers=deliveries.activeUsers(candidates);
        java.util.Set<String> permitted=access.allowed(processId,activeUsers);
        Map<String,String> languages=deliveries.languages(permitted);
        Map<String,NotificationEvent> events=new LinkedHashMap<>();
        var context=new DeliveryContext(id,submission,request,runtime,activeUsers,permitted,languages,events);
        boolean waiting=false;
        for(Map<String,Object> pending:deliveries.targets(id)) {
            if(Set.of("DELIVERED","SELF_EXCLUDED","CANCELLED").contains(pending.get("state"))) continue;
            waiting|=!deliverTarget(context,pending);
        }
        var created=notifications.createFromEvents(events.values());
        events.keySet().forEach(user -> deliveries.delivered(id,user,
                created.containsKey(user)?created.get(user).getId():null));
        submissions.state(id,waiting?"WAITING_TARGET":"DELIVERED",null);
    }

    private void initialize(String id,Map<String,Object> submission,RecordNoteCatalogRepository.Request request,
                            NoteTarget source,RecordNoteRuntimeContext runtime) {
        Map<String,Object> savedPolicy=catalog.map(String.valueOf(submission.get("policy")));
        Object storedRows=savedPolicy.get("scopeRows");
        List<Map<String,Object>> existing=deliveries.targets(id);
        for(var definition:runtime.definitions()) {
            boolean placeholder=existing.stream().anyMatch(t -> definition.nodeKey().equals(t.get("node_key"))
                    && "{}".equals(String.valueOf(t.get("row_key"))));
            if(!placeholder && existing.stream().anyMatch(t -> definition.nodeKey().equals(t.get("node_key")))) continue;
            int added=0;
            boolean unresolved=false;
            JsonNode schema=request.tables().stream().filter(t -> definition.tableName().equals(t.path("tableName").asText()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("NOTE_TARGET_TABLE_MISSING"));
            if("RECORD".equals(source.getTargetType()) && !source.getTableId().equals(schema.path("tableId").asText())) {
                throw new IllegalStateException("NOTE_TRANSACTION_SCOPE_MISMATCH");
            }
            List<Map<String,Object>> rows=RecordNoteRows.rows(request.variables(),definition.tableName());
            for(var row:rows) {
                if("RECORD".equals(source.getTargetType()) && !catalog.selector(row,schema).equals(source.getTargetId())) continue;
                Map<String,Object> key=catalog.rowKey(row,schema);
                if(key.isEmpty()) { unresolved=true; continue; }
                boolean incomplete=savedPolicy.get("unresolvedScopeTables") instanceof List<?> tables && tables.contains(definition.tableName());
                if(!incomplete && storedRows instanceof Map<?,?> saved && saved.get(definition.tableName()) instanceof List<?> keys
                        && !keys.isEmpty() && keys.stream().noneMatch(k -> k instanceof Map<?,?> m && sameKey(key,m))) continue;
                String targetKey=definition.nodeKey()+":"+digest(submissions.json(new TreeMap<>(key)));
                deliveries.target(id,targetKey,definition.nodeKey(),key);
                added++;
            }
            if(added==0 || unresolved) deliveries.target(id,definition.nodeKey()+":pending",definition.nodeKey(),Map.of());
            else deliveries.removePlaceholder(id,definition.nodeKey()+":pending");
        }
    }

    private record DeliveryContext(String id,Map<String,Object> submission,RecordNoteCatalogRepository.Request request,
            RecordNoteRuntimeContext runtime,Set<String> activeUsers,Set<String> permitted,Map<String,String> languages,
            Map<String,NotificationEvent> events) {}

    private boolean deliverTarget(DeliveryContext context,Map<String,Object> pending) {
        String id=context.id(); var submission=context.submission(); var request=context.request();
        var runtime=context.runtime(); var activeUsers=context.activeUsers(); var permitted=context.permitted();
        var languages=context.languages(); var events=context.events();
        Map<String,Object> key=catalog.map(String.valueOf(pending.get("row_key")));
        List<RecordNoteTaskTarget> current=runtime.tasks().stream()
                .filter(t -> t.nodeKey().equals(pending.get("node_key")) && sameKey(t.rowKey(),key)).toList();
        if(current.size()!=1) {
            deliveries.targetState(id,(String)pending.get("target_key"),"WAITING_STAGE",null,null); return false;
        }
        RecordNoteTaskTarget task=current.get(0);
        if(!Set.of("ACTIVE","WAITING_CLAIM","COMPLETED").contains(task.state())) {
            deliveries.targetState(id,(String)pending.get("target_key"),"UNRESOLVED",task,null); return false;
        }
        String user=recipient(request,task);
        if(user==null || !activeUsers.contains(user)) {
            deliveries.targetState(id,(String)pending.get("target_key"),
                    "TEAM".equals(task.assignmentMode())?"WAITING_CLAIM":"UNRESOLVED",task,null); return false;
        }
        if(user.equals(submission.get("user_id"))) {
            deliveries.targetState(id,(String)pending.get("target_key"),"SELF_EXCLUDED",task,user); return true;
        }
        if(!permitted.contains(user)) {
            deliveries.targetState(id,(String)pending.get("target_key"),"WAITING_PERMISSION",task,user); return false;
        }
        if(deliveries.reserve(id,user)) {
            String link="/record-note-notifications/"+id;
            Locale locale=Locale.forLanguageTag(languages.getOrDefault(user,"en").replace('_','-'));
            String title=messages.getMessage("record_note.notification_title",null,locale);
            String content=RecordNoteNotificationText.content(request,submission,
                    catalog.map(String.valueOf(submission.get("policy"))),locale,messages);
            events.put(user,NotificationEvent.builder().targetUserId(user)
                    .notificationType("ATTACHMENT_UPDATE").title(title).content(content).link(link).build());
        }
        deliveries.targetState(id,(String)pending.get("target_key"),"DELIVERED",task,user);
        return true;
    }

    private String recipient(RecordNoteCatalogRepository.Request request,RecordNoteTaskTarget task) {
        return RecordNoteRecipients.resolve(request.variables(),task);
    }

    private static NoteTarget target(Map<String,Object> row) {
        return NoteTarget.builder().targetType((String)row.get("target_type")).targetId((String)row.get("target_id"))
                .tableKind((String)row.get("table_kind")).tableId((String)row.get("table_id"))
                .formKey((String)row.get("form_key")).controlId((String)row.get("control_id"))
                .processInstanceId((String)row.get("process_instance_id")).build();
    }
    private static boolean sameKey(Map<?,?> a,Map<?,?> b) {
        return a.size()==b.size()&&a.entrySet().stream().allMatch(e -> b.containsKey(e.getKey())
                && String.valueOf(e.getValue()).equals(String.valueOf(b.get(e.getKey()))));
    }
    private static String digest(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
