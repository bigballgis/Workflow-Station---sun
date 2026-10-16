package com.portal.recordnote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Durable submission ledger and idempotency boundary. All SQL values are bound. */
@Repository
@RequiredArgsConstructor
public class RecordNoteSubmissionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public record Intent(String id,String user,String fingerprint,NoteTarget target,int count,
                         RecordNoteCatalogRepository.Request request,Object policy,String state) {}

    public boolean insert(Intent input) {
        var target=input.target(); var request=input.request();
        return jdbc.update("""
                INSERT INTO up_record_note_submissions
                (id,user_id,client_key,fingerprint,process_instance_id,target_type,target_id,table_kind,table_id,
                 form_key,control_id,catalog_id,attachment_count,policy,state)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?) ON CONFLICT(user_id,client_key) DO NOTHING
                """,input.id(),input.user(),target.getClientSubmissionId(),input.fingerprint(),target.getProcessInstanceId(),
                target.getTargetType(),target.getTargetId(),target.getTableKind(),target.getTableId(),
                target.getFormKey(),target.getControlId(),request==null?null:request.catalogId(),input.count(),json(input.policy()),input.state())>0;
    }

    public Map<String,Object> byClientKey(String user,String key) {
        return jdbc.queryForMap("SELECT * FROM up_record_note_submissions WHERE user_id=? AND client_key=?",user,key);
    }
    public Map<String,Object> lock(String id) {
        var rows=jdbc.queryForList("SELECT * FROM up_record_note_submissions WHERE id=? FOR UPDATE SKIP LOCKED",id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public void response(String id,NoteItem result) {
        jdbc.update("UPDATE up_record_note_submissions SET response=CAST(? AS jsonb) WHERE id=?",json(result),id);
    }
    public NoteItem replay(Map<String,Object> stored,String fingerprint) {
        if (!fingerprint.equals(stored.get("fingerprint"))) {
            throw new com.portal.service.RecordNoteService.RecordNoteException("NOTE_IDEMPOTENCY_CONFLICT",
                    "Submission identifier was already used for different content");
        }
        try {
            NoteItem result=mapper.readValue(String.valueOf(stored.get("response")),NoteItem.class);
            result.setReplayed(true);
            return result;
        } catch(Exception e) { throw new IllegalStateException("Invalid stored submission response",e); }
    }
    public List<String> due() {
        return jdbc.queryForList("SELECT id FROM up_record_note_submissions WHERE state IN "
                + "('PENDING','WAITING_TARGET','WAITING_METADATA','RETRY') AND next_attempt_at<=now() ORDER BY next_attempt_at,created_at LIMIT 20",String.class);
    }
    public void attachCatalog(String id,String catalogId) {
        jdbc.update("UPDATE up_record_note_submissions SET catalog_id=? WHERE id=? AND catalog_id IS NULL",catalogId,id);
    }
    public void state(String id,String state,String error) {
        jdbc.update("UPDATE up_record_note_submissions SET state=?,last_error=?,next_attempt_at=now()+interval '15 seconds' "
                + "WHERE id=?",state,error,id);
    }
    public void retry(String id) {
        jdbc.update("UPDATE up_record_note_submissions SET attempts=attempts+1, "
                + "state=CASE WHEN attempts>=9 THEN 'FAILED' ELSE 'RETRY' END,last_error='DELIVERY_RETRY_REQUIRED', "
                + "next_attempt_at=now()+LEAST(1800, 15*power(2,LEAST(attempts,7))) * interval '1 second' WHERE id=? AND state IN ('PENDING','WAITING_TARGET','WAITING_METADATA','RETRY')",id);
    }
    public void adopt(String draftId,String processId,String user,String catalogId) {
        jdbc.update("UPDATE up_record_note_submissions SET process_instance_id=?,catalog_id=?,target_id=?,state='PENDING' "
                + "WHERE target_id=? AND user_id=? AND state='WAITING_REQUEST'",processId,catalogId,processId,draftId,user);
    }
    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(Exception e) { throw new IllegalStateException("Cannot serialize note submission",e); }
    }
}
