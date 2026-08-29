package com.portal.util;

import com.portal.dto.TaskInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mine ∪ claim-pool for the merged To Do list. Pool rows win on id clash so claim flags
 * ({@code claimable} / holder / force-unclaim) stay authoritative.
 */
public final class TodoListUnion {

    private TodoListUnion() {
    }

    public static List<TaskInfo> merge(List<TaskInfo> mine, List<TaskInfo> pool) {
        Map<String, TaskInfo> byId = new LinkedHashMap<>();
        putAll(byId, mine);
        putAll(byId, pool);
        return new ArrayList<>(byId.values());
    }

    private static void putAll(Map<String, TaskInfo> byId, List<TaskInfo> tasks) {
        if (tasks == null) {
            return;
        }
        for (TaskInfo task : tasks) {
            if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
                continue;
            }
            byId.put(task.getTaskId(), task);
        }
    }
}
