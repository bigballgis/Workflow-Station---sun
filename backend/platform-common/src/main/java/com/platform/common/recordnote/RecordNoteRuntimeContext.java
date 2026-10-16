package com.platform.common.recordnote;

import java.util.List;

/** Frozen target definitions and current execution pointers for one request. */
public record RecordNoteRuntimeContext(List<TargetDefinition> definitions, List<RecordNoteTaskTarget> tasks,
        boolean terminated) {
    /** A selectable concrete user task and the enclosing row scope. */
    public record TargetDefinition(String nodeKey, String scopeKey, String tableName, String personField,
            String roleField) {}
}
