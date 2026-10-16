package com.platform.common.recordnote;

import java.util.Map;

/** Minimal service-to-service task context. Never contains note bodies or attachment content. */
public record RecordNoteTaskTarget(String taskId, String nodeKey, String scopeKey, String tableName,
        Map<String, Object> rowKey, String activationId, String assignmentMode, String personField,
        String recipientId, String state, Map<String,String> activationContext) {}
