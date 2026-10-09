package com.workflow.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CallActivityRowsTest {

    private final CallActivityRows rows = new CallActivityRows();

    private DelegateExecution executionWith(Object subTables) {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("__subTables__")).thenReturn(subTables);
        return execution;
    }

    @Test
    void readsTheRowsOfTheNamedTable() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("vendor_name", "Acme");
        DelegateExecution execution = executionWith(Map.of(
                "dw:extra_vendors", List.of(row),
                "dw:budget_lines", List.of(Map.of("amount", 5))));

        List<Map<String, Object>> result = rows.of(execution, "extra_vendors");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("vendor_name", "Acme");
    }

    /** No rows means nothing to call: Flowable moves straight past an empty collection. */
    @Test
    void yieldsAnEmptyListWhenThereAreNoRows() {
        assertThat(rows.of(executionWith(Map.of()), "extra_vendors")).isEmpty();
        assertThat(rows.of(executionWith(null), "extra_vendors")).isEmpty();
        assertThat(rows.of(executionWith(Map.of("dw:extra_vendors", "garbage")), "extra_vendors")).isEmpty();
    }
}
