package com.platform.common.assignment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Existing task-form archive keys are Portal history, not repeated workflow input. */
public final class AssignmentSnapshotMetadata {
    public static final String PREFIX = "_snapshot_";
    private AssignmentSnapshotMetadata() { }
    public static boolean archived(String key) { return key.startsWith(PREFIX); }
    public static Map<String,Object> engineInput(Map<String,Object> values) {
        var result = new LinkedHashMap<String,Object>();
        values.forEach((key,value) -> { if (!archived(key)) result.put(key,value); });
        return result;
    }
    /** Retain this task's real expected value when a draft already created its snapshot. */
    public static ObjectNode baseline(ObjectNode values,String taskId) {
        var result = values.objectNode();
        values.fields().forEachRemaining(entry -> {
            if (!archived(entry.getKey()) || entry.getKey().equals(PREFIX+taskId)) result.set(entry.getKey(),entry.getValue().deepCopy());
        });
        return result;
    }
}
