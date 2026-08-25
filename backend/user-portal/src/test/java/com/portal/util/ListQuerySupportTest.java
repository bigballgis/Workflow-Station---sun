package com.portal.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ListQuerySupportTest {

    @Mock
    private Logger log;

    @Test
    void logIfOverSlaWarnsOnlyAbove500msWithoutFilterValues() {
        ListQuerySupport.logIfOverSla(log, "todo-tasks", 0, 20, 40L, 500L, 500L, 0L);
        verifyNoInteractions(log);

        ListQuerySupport.logIfOverSla(log, "todo-tasks", 2, 20, 841L, 501L, 480L, 21L);
        verify(log).warn(
                eq("List query over 500ms SLA listKey={} page={} size={} total={} elapsedMs={} sqlMs={} hydrateMs={}"),
                eq("todo-tasks"), eq(2), eq(20), eq(841L), eq(501L), eq(480L), eq(21L));
    }

    @Test
    void logIfSlowWarnsOnlyAbove1sWithFixedFields() {
        ListQuerySupport.logIfSlow(log, "todo-tasks", 0, 20, 40L, System.nanoTime());
        verify(log, never()).warn(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyLong());

        long started = System.nanoTime() - 1_001_000_000L;
        ListQuerySupport.logIfSlow(log, "todo-tasks", 3, 20, 900L, started);
        verify(log).warn(
                eq("Slow list query listKey={} viewId={} page={} size={} total={} elapsedMs={}"),
                eq("todo-tasks"), eq("todo-tasks"), eq(3), eq(20), eq(900L), anyLong());
    }
}
