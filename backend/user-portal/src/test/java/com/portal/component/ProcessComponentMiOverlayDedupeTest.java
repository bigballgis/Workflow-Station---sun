package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ProcessComponent#resolveMiRowProgress} overlays {@code task_current_node}; selection must not
 * let orphan extended rows overshadow the user's real runtime step ("My Requests" parity with task detail).
 */
class ProcessComponentMiOverlayDedupeTest {

    private static Method requireStatic(String name, Class<?>... p) throws Exception {
        Method m = ProcessComponent.class.getDeclaredMethod(name, p);
        m.setAccessible(true);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pickLatestActiveTaskReflect(List<Map<String, Object>> tasks) throws Exception {
        return (Map<String, Object>) requireStatic("pickLatestActiveTask", List.class).invoke(null, tasks);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dedupeReflect(List<Map<String, Object>> tasks) throws Exception {
        return (List<Map<String, Object>>) requireStatic("dedupeMiTasksPreferCompletedPerStepKey", List.class).invoke(null, tasks);
    }

    @Test
    void pickLatestPrefersAssignedOpenTaskOverNewerTimestampOrphanCreated() throws Exception {
        LocalDateTime early = LocalDateTime.parse("2026-05-01T10:00:00");
        LocalDateTime later = LocalDateTime.parse("2026-05-03T22:00:00");
        Map<String, Object> orphanSubForm1 = miTask("tid-orph", "sub form1", "MI_sf1", "CREATED", null, early, null);
        Map<String, Object> realSubForm2 = miTask("tid-real", "sub form2", "MI_sf2", "ASSIGNED", "user-1", later, null);
        Map<String, Object> picked = pickLatestActiveTaskReflect(List.of(orphanSubForm1, realSubForm2));
        assertThat(picked).isSameAs(realSubForm2);
    }

    @Test
    void dedupeCollapsesSameStepNonTerminalRowsToSingleRepresentative() throws Exception {
        LocalDateTime t1 = LocalDateTime.parse("2026-05-01T08:00:00");
        LocalDateTime t2 = LocalDateTime.parse("2026-05-02T08:00:00");
        Map<String, Object> orphan = miTask("a", "sub form1", "MI_sf1", "CREATED", null, t2, null);
        Map<String, Object> assigned = miTask("b", "sub form1", "MI_sf1", "ASSIGNED", "u", t1, null);
        List<Map<String, Object>> out = dedupeReflect(List.of(orphan, assigned));
        assertThat(out).hasSize(1);
        assertThat(out.get(0)).isSameAs(assigned);
    }

    private static Map<String, Object> miTask(
            String taskId,
            String taskName,
            String defKey,
            String status,
            String assignee,
            LocalDateTime created,
            LocalDateTime completed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", taskId);
        m.put("taskName", taskName);
        m.put("taskDefinitionKey", defKey);
        m.put("status", status);
        m.put("assignee", assignee);
        m.put("assigneeName", assignee != null ? "n-" + assignee : null);
        m.put("createdTime", created);
        m.put("completedTime", completed);
        m.put("subTableName", "dw_subtable_" + UUID.randomUUID().toString().substring(0, 8));
        m.put("subTableRowKey", Map.of("id", 1L));
        return m;
    }
}
