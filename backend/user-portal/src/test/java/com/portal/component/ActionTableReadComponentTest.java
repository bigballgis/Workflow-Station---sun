package com.portal.component;

import com.platform.common.functionunit.CatalogRuntimeRows;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionTableReadComponentTest {
    private final ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
    private final CatalogSnapshotReader snapshots = mock(CatalogSnapshotReader.class);
    private final CatalogRuntimeRows rows = mock(CatalogRuntimeRows.class);
    private final ActionTableReadComponent component = new ActionTableReadComponent(instances, snapshots, rows);

    private void instance(String process, String catalog) {
        when(instances.findById(process)).thenReturn(Optional.of(ProcessInstance.builder().id(process)
                .functionUnitCatalogId(catalog).functionUnitCode("synthetic").build()));
        Map<String, Object> form = Map.of("formId", 5);
        when(snapshots.contents(catalog, "FORM")).thenReturn(List.of(form));
        when(snapshots.formBindings(catalog, form)).thenReturn(List.of(
                Map.of("bindingId", 71, "tableId", 23, "bindingType", "ACTION")));
    }

    @Test void readUsesThePinnedTableAndProcessEvenWithoutARequestId() {
        instance("case-1", "catalog-v1");
        when(rows.rows("catalog-v1", "23", "case-1")).thenReturn(List.of(Map.of("note", "old")));
        var result = component.getActionTableRows("case-1");
        assertThat(result).hasSize(1);
        verify(rows).rows("catalog-v1", "23", "case-1");
        verifyNoMoreInteractions(rows);
    }

    @Test void sameSourceTableInTwoVersionsDoesNotMixRows() {
        instance("case-1", "catalog-v1"); instance("case-2", "catalog-v2");
        when(rows.rows("catalog-v1", "23", "case-1")).thenReturn(List.of(Map.of("note", "one")));
        when(rows.rows("catalog-v2", "23", "case-2")).thenReturn(List.of());
        assertThat(component.getActionTableRows("case-1")).hasSize(1);
        assertThat(component.getActionTableRows("case-2")).isEmpty();
    }

    @Test void missingVersionIsAnExplicitRecoveryFailure() {
        when(instances.findById("legacy")).thenReturn(Optional.of(ProcessInstance.builder().id("legacy").build()));
        assertThatThrownBy(() -> component.getActionTableRows("legacy")).isInstanceOf(FunctionUnitVersionException.class);
        verifyNoInteractions(rows, snapshots);
    }

    @Test void storageFailureCannotLookLikeEmptyHistory() {
        instance("case-1", "catalog-v1");
        when(rows.rows("catalog-v1", "23", "case-1")).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> component.getActionTableRows("case-1")).hasMessage("unavailable");
    }
}
