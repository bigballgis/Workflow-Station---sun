package com.portal.recordnote;

import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.service.RecordNoteService;
import com.portal.service.RecordNoteService.RecordNoteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The outer transaction includes every attachment and the durable notification intent. */
@Component
@RequiredArgsConstructor
public class RecordNoteSubmissionComponent {
    private final RecordNoteService notes;
    private final RecordNoteSubmissionRepository submissions;
    private final RecordNoteCatalogRepository catalog;

    public record Submission(String user,String name,NoteTarget target,String subject,String body,
                             List<MultipartFile> files,List<String> inlineIds) {}

    @Transactional
    public NoteItem submit(Submission input) {
        String user=input.user(), name=input.name(), subject=input.subject(), body=input.body();
        NoteTarget target=input.target();
        List<MultipartFile> files=input.files();
        List<String> inlineIds=input.inlineIds();
        if (target.getControlId()==null || target.getFormKey()==null) {
            // FALLBACK(migration): packages predating this opt-in feature have no stable note identifiers.
            // Remove when every deployed catalog has been re-exported with Record Note identifiers.
            if (!target.getTargetId().startsWith("draft-") && catalog.notificationsEnabled(
                    catalog.request(target.getProcessInstanceId(),false))) {
                throw new RecordNoteException("NOTE_SUBMISSION_CONTEXT_REQUIRED","Reload this form before submitting a note");
            }
            return notes.createComment(target,subject,body,files,inlineIds,user,name);
        }
        if (target.getClientSubmissionId()==null) {
            throw new RecordNoteException("NOTE_SUBMISSION_CONTEXT_REQUIRED","Reload this form before submitting a note");
        }
        if (target.getClientSubmissionId().length()>64 || target.getControlId().length()>64 || target.getFormKey().length()>64) {
            throw new RecordNoteException("BAD_TARGET","Invalid submission context");
        }
        boolean draft=target.getTargetId().startsWith("draft-");
        RecordNoteCatalogRepository.Request request=draft?null:catalog.request(target.getProcessInstanceId(),false);
        var control=draft || request.catalogId()==null?null:catalog.control(request,target);
        if(control!=null && target.getTaskId()!=null && control.props().path("readonly").asBoolean()) {
            throw new RecordNoteException("FORBIDDEN","This task Record Note is read-only");
        }
        int count=files==null?0:(int)files.stream().filter(f -> f!=null&&!f.isEmpty()).count();
        if(control!=null && count>0 && !control.props().path("allowAttachment").asBoolean(true)) {
            throw new RecordNoteException("FORBIDDEN","Attachments are disabled for this Record Note");
        }
        Map<String,Object> policy=policy(control,request);
        if (target.getVerifiedUploadSource()!=null) policy.put("uploadSource",target.getVerifiedUploadSource());
        String state=draft?"WAITING_REQUEST":count==0?"DISABLED":control==null?"WAITING_METADATA"
                :control.enabled()?"PENDING":"DISABLED";
        policy.put("fingerprintVersion",2);
        String fingerprint=fingerprint(target,subject,body,files,inlineIds,true);
        String id=UUID.randomUUID().toString();
        if (!submissions.insert(new RecordNoteSubmissionRepository.Intent(id,user,fingerprint,target,count,request,policy,state))) {
            var existing=submissions.byClientKey(user,target.getClientSubmissionId());
            var previousPolicy=catalog.map(String.valueOf(existing.get("policy")));
            // FALLBACK(migration): old intents fingerprinted content before upload-context snapshots existed.
            if (!previousPolicy.containsKey("fingerprintVersion")) {
                return submissions.replay(existing,fingerprint(target,subject,body,files,inlineIds,false));
            }
            if (!Integer.valueOf(2).equals(previousPolicy.get("fingerprintVersion"))) {
                throw new IllegalStateException("NOTE_FINGERPRINT_VERSION_UNSUPPORTED");
            }
            return submissions.replay(existing,fingerprint);
        }
        target.setSubmissionId(id);
        NoteItem result=notes.createComment(target,subject,body,files,inlineIds,user,name);
        result.setSubmissionId(id);
        submissions.response(id,result);
        return result;
    }

    private Map<String,Object> policy(RecordNoteCatalogRepository.Control control,RecordNoteCatalogRepository.Request request) {
        Map<String,Object> policy=new java.util.LinkedHashMap<>();
        if(control==null) return policy;
        policy.put("enabled",control.enabled()); policy.put("targetNodeKeys",control.nodes());
        Map<String,List<Map<String,Object>>> scope=new java.util.LinkedHashMap<>();
        List<String> unresolved=new java.util.ArrayList<>();
        var tables=control.enabled()?catalog.targetTables(request,control.nodes()):java.util.Set.<String>of();
        for(var schema:request.tables()) {
            String table=schema.path("tableName").asText();
            if(!tables.contains(table)) continue;
            var rows=com.platform.common.recordnote.RecordNoteRows.rows(request.variables(),table);
            var keys=rows.stream().map(row -> catalog.rowKey(row,schema)).filter(key -> !key.isEmpty()).toList();
            if(!rows.isEmpty()) scope.put(table,keys);
            if(keys.size()!=rows.size()) unresolved.add(table);
        }
        policy.put("scopeRows",scope); policy.put("unresolvedScopeTables",unresolved);
        return policy;
    }

    @Transactional
    public int adopt(String draft,String process,String user) {
        var request=catalog.request(process,false);
        int count=notes.adoptDraftNotes(draft,process,user);
        submissions.adopt(draft,process,user,request.catalogId());
        return count;
    }

    private String fingerprint(NoteTarget target,String subject,String body,List<MultipartFile> files,List<String> inlineIds,boolean includeSource) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            var fields=new java.util.ArrayList<>(java.util.Arrays.asList(target.getTargetType(),target.getTargetId(),
                    target.getTableKind(),target.getTableId(),target.getProcessInstanceId(),target.getFormKey(),
                    target.getControlId(),subject,body,inlineIds));
            if (includeSource) { fields.add(target.getTaskId()); fields.add(target.getSourceView()); }
            digest.update(submissions.json(fields).getBytes(StandardCharsets.UTF_8));
            if(files!=null) for(MultipartFile file:files) if(file!=null&&!file.isEmpty()) {
                digest.update(submissions.json(java.util.Arrays.asList(file.getOriginalFilename(),file.getContentType(),file.getSize()))
                        .getBytes(StandardCharsets.UTF_8));
                digest.update(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(Exception e) { throw new IllegalStateException("Cannot fingerprint note submission",e); }
    }
}
