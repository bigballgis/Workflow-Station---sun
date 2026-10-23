package com.portal.component;

import com.platform.common.functionunit.FunctionUnitVersionException;
import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Claim, unclaim, transfer and reassign read the request row directly, not through hydration. */
class ProcessInstanceSyncComponentTest {

    private final ProcessInstanceRepository repository = mock(ProcessInstanceRepository.class);
    private final OwnerFieldComponent ownerFields = mock(OwnerFieldComponent.class);
    private final ProcessInstanceSyncComponent sync = new ProcessInstanceSyncComponent(
            mock(WorkflowEngineClient.class), repository, ownerFields, mock(MiOuterStepResolver.class));

    /**
     * A request whose variables predate the release pin still resolves its owner fields in the
     * release it was started on — never in the live designer.
     */
    @Test
    @SuppressWarnings("unchecked")
    void ownerSnapshotOfAnUnhealedRequestResolvesInItsRelease() {
        ProcessInstance row = ProcessInstance.builder().id("pi-1").status("RUNNING")
                .functionUnitCatalogId("rel-vendor-1").functionUnitCode("fu-vendor").functionUnitVersionLabel("1.0.2")
                .variables(new HashMap<>(Map.of("vendor_name", "Acme")))
                .build();
        when(repository.findById("pi-1")).thenReturn(Optional.of(row));

        sync.updateProcessInstanceAssignee("pi-1", "user-1", null, "Review Vendor", null);

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(ownerFields).applyAssigneeSnapshot(eq("fu-vendor"), variables.capture(), eq("user-1"), isNull(),
                eq("RUNNING"), org.mockito.ArgumentMatchers.nullable(MiOuterStepResolver.OuterLookup.class));
        assertThat(variables.getValue()).containsEntry("functionUnitId", "rel-vendor-1")
                .containsEntry("functionUnitVersionLabel", "1.0.2").containsEntry("vendor_name", "Acme");
    }

    /** No recorded release: a version error, not a silently skipped assignee update. */
    @Test
    void requestWithoutARecordedReleaseIsAVersionError() {
        ProcessInstance row = ProcessInstance.builder().id("pi-1").status("RUNNING")
                .functionUnitCode("fu-vendor").variables(new HashMap<>()).build();
        when(repository.findById("pi-1")).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> sync.updateProcessInstanceAssignee("pi-1", "user-1", null, "Review Vendor", null))
                .isInstanceOf(FunctionUnitVersionException.class);
        verify(ownerFields, never()).applyAssigneeSnapshot(anyString(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.nullable(MiOuterStepResolver.OuterLookup.class));
        verify(repository, never()).save(any());
    }
}
