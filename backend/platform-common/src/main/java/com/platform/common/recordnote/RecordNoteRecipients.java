package com.platform.common.recordnote;

import java.util.Map;
import java.util.Set;

/** One authority for active Person fields versus completed snapshots and claimed Team tasks. */
public final class RecordNoteRecipients {
    private RecordNoteRecipients() {}

    public static String resolve(Map<String,Object> variables,RecordNoteTaskTarget task) {
        if(!Set.of("ACTIVE","WAITING_CLAIM","COMPLETED").contains(task.state())) return null;
        if("COMPLETED".equals(task.state()) || "TEAM".equals(task.assignmentMode())) return task.recipientId();
        if(!"PERSON".equals(task.assignmentMode()) || task.personField()==null) return null;
        var rows=RecordNoteRows.rows(variables,task.tableName()).stream()
                .filter(row -> RecordNoteRows.matches(row,task.rowKey())).toList();
        if(rows.size()!=1) return null;
        return userId(rows.get(0).get(task.personField()));
    }

    public static String userId(Object value) {
        // User-reference transport keys follow the engine's AssigneeUserIdNormalizer contract.
        // Conflicting references cannot establish a trustworthy recipient.
        if(value instanceof Map<?,?> map) {
            var ids=java.util.stream.Stream.of("id","userId","user_id","value")
                    .map(map::get).filter(v -> v instanceof String || v instanceof Number)
                    .map(RecordNoteRecipients::userId).filter(java.util.Objects::nonNull).distinct().toList();
            return ids.size()==1?ids.get(0):null;
        }
        if(value instanceof String text) return text.isBlank()?null:text.trim();
        if(value instanceof Number number) {
            double numeric=number.doubleValue();
            return Double.isFinite(numeric) && Math.floor(numeric)==numeric?String.valueOf(number.longValue()):number.toString();
        }
        return null;
    }

}
