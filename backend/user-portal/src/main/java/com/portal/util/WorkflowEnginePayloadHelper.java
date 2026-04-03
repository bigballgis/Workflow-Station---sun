package com.portal.util;

import java.util.List;
import java.util.Map;

/**
 * Maps workflow-engine REST payloads after {@link com.platform.common.util.ApiResponseBodyUnwrap#unwrapDataMap}
 * — the inner {@code data} object is already unwrapped, so {@code TaskListResult} / task DTO fields sit at the
 * top level of the map (not under a second {@code data} key).
 */
public final class WorkflowEnginePayloadHelper {

    private WorkflowEnginePayloadHelper() {
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> taskListFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object tasks = payload.get("tasks");
        if (tasks instanceof List<?>) {
            return (List<Map<String, Object>>) tasks;
        }
        Object nested = payload.get("data");
        if (nested instanceof Map<?, ?>) {
            Object innerTasks = ((Map<?, ?>) nested).get("tasks");
            if (innerTasks instanceof List<?>) {
                return (List<Map<String, Object>>) innerTasks;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> singleTaskFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        if (payload.get("taskId") != null) {
            return payload;
        }
        Object nested = payload.get("data");
        if (nested instanceof Map<?, ?>) {
            return (Map<String, Object>) nested;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> taskCountFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        if (payload.get("totalCount") != null || payload.get("overdueCount") != null) {
            return payload;
        }
        Object nested = payload.get("data");
        if (nested instanceof Map<?, ?>) {
            return (Map<String, Object>) nested;
        }
        return payload;
    }
}
