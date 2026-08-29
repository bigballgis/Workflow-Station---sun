package com.portal.component;

import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.platform.security.util.SecurityContextUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 15s Mine full-scan cache. Claim/unclaim must {@link #invalidate()} or To Do shows a stale holder.
 */
@Component
public class MineTaskListCache {

    private static final int TTL_MS = 15_000;
    private static final int MAX = 64;

    private final Map<String, Entry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > MAX;
                }
            });

    public void invalidate() {
        cache.clear();
    }

    public List<TaskInfo> get(String key) {
        Entry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                cache.remove(key);
            }
            return null;
        }
        return entry.tasks();
    }

    public void put(String key, List<TaskInfo> tasks) {
        cache.put(key, new Entry(tasks, System.currentTimeMillis()));
    }

    public static String key(String userId, TaskQueryRequest request, List<String> assignmentTypes, int size) {
        return userId + '|'
                + SecurityContextUtils.getCurrentActiveBusinessUnitId().orElse("-") + '|'
                + SecurityContextUtils.getCurrentActiveRoleId().orElse("-") + '|'
                + String.valueOf(assignmentTypes) + '|'
                + size + '|'
                + String.valueOf(request.getFilters()) + '|'
                + String.valueOf(request.getSortBy()) + '|'
                + String.valueOf(request.getSortDirection()) + '|'
                + String.valueOf(request.getGroupBy()) + '|'
                + String.valueOf(request.getKeyword()) + '|'
                + String.valueOf(request.getPriorities()) + '|'
                + String.valueOf(request.getProcessTypes()) + '|'
                + String.valueOf(request.getStatuses()) + '|'
                + String.valueOf(request.getStartTime()) + '|'
                + String.valueOf(request.getEndTime()) + '|'
                + String.valueOf(request.getIncludeOverdue());
    }

    private record Entry(List<TaskInfo> tasks, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > TTL_MS;
        }
    }
}
