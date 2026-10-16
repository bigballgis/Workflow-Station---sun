package com.portal.recordnote;

import com.platform.common.recordnote.RecordNoteTaskTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Per-target waiting states and per-user delivery tombstones survive worker crashes and user deletion. */
@Repository
@RequiredArgsConstructor
public class RecordNoteDeliveryRepository {
    private final JdbcTemplate jdbc;
    private final RecordNoteSubmissionRepository submissions;

    public void target(String submission,String key,String node,Map<String,Object> rowKey) {
        jdbc.update("INSERT INTO up_record_note_delivery_targets (submission_id,target_key,node_key,row_key,state) "
                + "VALUES (?,?,?,CAST(? AS jsonb),'WAITING_STAGE') ON CONFLICT DO NOTHING",
                submission,key,node,submissions.json(rowKey));
    }
    public List<Map<String,Object>> targets(String id) {
        return jdbc.queryForList("SELECT * FROM up_record_note_delivery_targets WHERE submission_id=?",id);
    }
    public void targetState(String id,String key,String state,RecordNoteTaskTarget task,String user) {
        jdbc.update("UPDATE up_record_note_delivery_targets SET state=?,task_id=?,activation_id=?,recipient_id=? "
                + "WHERE submission_id=? AND target_key=?",state,task==null?null:task.taskId(),
                task==null?null:task.activationId(),user,id,key);
    }
    public boolean reserve(String submission,String user) {
        return jdbc.update("INSERT INTO up_record_note_deliveries (submission_id,user_id,state) VALUES (?,?,'PENDING') "
                + "ON CONFLICT DO NOTHING",submission,user)>0;
    }
    public void delivered(String submission,String user,Long notification) {
        jdbc.update("UPDATE up_record_note_deliveries SET state=?,notification_id=? WHERE submission_id=? AND user_id=?",
                notification==null?"PREFERENCE_DISABLED":"DELIVERED",notification,submission,user);
    }
    public java.util.Set<String> activeUsers(java.util.Set<String> users) {
        if(users.isEmpty()) return java.util.Set.of();
        var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        return new java.util.HashSet<>(named.queryForList("SELECT id FROM sys_users WHERE id IN (:users) AND status='ACTIVE'",
                Map.of("users",users),String.class));
    }
    public Map<String,String> languages(java.util.Set<String> users) {
        if(users.isEmpty()) return Map.of();
        var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        return named.query("SELECT u.id,COALESCE(p.language,u.language,'en') AS language FROM sys_users u "
                        + "LEFT JOIN up_user_preference p ON p.user_id=u.id WHERE u.id IN (:users)",Map.of("users",users),
                rs -> { Map<String,String> result=new java.util.HashMap<>();
                    while(rs.next()) result.put(rs.getString("id"),rs.getString("language")); return result; });
    }

    public void removePlaceholder(String submission,String key) {
        jdbc.update("DELETE FROM up_record_note_delivery_targets WHERE submission_id=? AND target_key=? AND row_key='{}'::jsonb",
                submission,key);
    }
}
