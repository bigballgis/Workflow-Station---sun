package com.workflow.component;

import com.workflow.entity.ExtendedTaskInfo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** MI sub-task completion writes back to a physical row only; JSON-row sub-tables live in the request's variables. */
class TaskMultiInstanceServiceWriteBackTest {

    private final MultiInstanceDataResolver resolver = mock(MultiInstanceDataResolver.class);
    private final TaskMultiInstanceService service = new TaskMultiInstanceService();

    @Test
    void jsonRowSubTableIsNotWrittenBackAsAPhysicalRow() {
        ReflectionTestUtils.setField(service, "multiInstanceDataResolver", resolver);
        ExtendedTaskInfo task = ExtendedTaskInfo.builder().taskId("t-1").processInstanceId("pi-1")
                .extendedProperties("{\"multiInstance\":true,\"subTableName\":\"fu_release_rows\","
                        + "\"subTableRowKey\":{\"id_idw\":\"R-1\"}}")
                .build();
        when(resolver.tryResolveSubTableRowKey(eq("fu_release_rows"), anyMap(), eq("pi-1")))
                .thenReturn(Map.of("id_idw", "R-1"));
        when(resolver.subTableExists("fu_release_rows")).thenReturn(false);

        // A completion that carries neither formData nor __subTables__ still has nothing physical to write.
        service.handleMultiInstanceSubTaskCompletion("t-1", Map.of("approved", true), task);

        verify(resolver, never()).loadSubTableRow(anyString(), anyMap());
        verify(resolver, never()).writeBackSubTableRow(anyString(), anyMap(), anyLong());
    }
}
