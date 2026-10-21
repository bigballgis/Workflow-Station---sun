package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;
import com.portal.dto.AllocatePrimaryKeyRequest;
import com.portal.dto.TaskFormData;
import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortalBatchPrimaryKeyAllocationComponentTest {
    private final TaskQueryComponent tasks = mock(TaskQueryComponent.class);
    private final TaskPermissionEvaluator permissions = mock(TaskPermissionEvaluator.class);
    private final TaskFormComponent forms = mock(TaskFormComponent.class);
    private final PrimaryKeyAllocationService allocator = mock(PrimaryKeyAllocationService.class);
    private final PortalBatchPrimaryKeyAllocationComponent component = new PortalBatchPrimaryKeyAllocationComponent(
            tasks, permissions, forms, allocator, new ObjectMapper());

    @Test
    void allocatesTheFrozenStrategyInOneBatchWithoutConsultingLiveDwFields() {
        TaskInfo task = TaskInfo.builder().taskId("T1").build();
        when(tasks.getTaskById("T1")).thenReturn(Optional.of(task));
        when(permissions.canProcessTask(task, "U1", null)).thenReturn(true);
        when(forms.getTaskFormData("T1")).thenReturn(TaskFormData.builder().subTableBatchCapabilities(Map.of("grid",
                Map.of("capabilities", Map.of("allowBatchAdd", true), "originalCrud", Map.of(), "rowDesign",
                        Map.of("binding", Map.of("tableId", 20), "fields", List.of(Map.of("fieldName", "item_key", "isPrimaryKey", true,
                                "pkGenerationJson", Map.of("strategy", "prefixedSequence", "prefix", "FROZEN-"))))))).build());
        when(allocator.allocate(eq(20L), eq("item_key"), any(), eq(2), eq(""), eq("dw_pk_sequences"))).thenReturn(List.of("FROZEN-1", "FROZEN-2"));
        assertThat(component.allocate("T1", "U1", request()).getValues()).containsExactly("FROZEN-1", "FROZEN-2");
        ArgumentCaptor<PkGenerationConfig> config = ArgumentCaptor.forClass(PkGenerationConfig.class);
        verify(allocator).allocate(eq(20L), eq("item_key"), config.capture(), eq(2), eq(""), eq("dw_pk_sequences"));
        assertThat(config.getValue().getPrefix()).isEqualTo("FROZEN-");
    }

    @Test
    void deniedTaskPermissionNeverConsumesSequenceValues() {
        when(tasks.getTaskById("T1")).thenReturn(Optional.of(TaskInfo.builder().taskId("T1").build()));
        assertThatThrownBy(() -> component.allocate("T1", "U1", request())).hasMessageContaining("not allowed");
        verifyNoInteractions(forms, allocator);
    }

    private static AllocatePrimaryKeyRequest request() {
        AllocatePrimaryKeyRequest request = new AllocatePrimaryKeyRequest();
        request.setTableId(20L);
        request.setFieldName("item_key");
        request.setCount(2);
        request.setBatchControlId("grid");
        return request;
    }
}
