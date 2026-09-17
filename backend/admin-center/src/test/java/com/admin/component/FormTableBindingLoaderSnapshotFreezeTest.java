package com.admin.component;

import com.admin.dto.response.FormContentDTO;
import com.admin.dto.response.TableBindingDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormTableBindingLoaderSnapshotFreezeTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private FormTableBindingLoader loader;

    @BeforeEach
    void setUp() {
        loader = new FormTableBindingLoader(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void emptyFrozenListDoesNotQueryLiveBindings() {
        FormContentDTO form = FormContentDTO.builder()
                .sourceId("42")
                .name("task-form")
                .tableBindings(List.of())
                .build();

        loader.attachTableBindings(List.of(form));

        assertThat(form.getTableBindings()).isEmpty();
        assertThat(queriedLiveFormBindings()).isFalse();
    }

    @Test
    void snapshotBindingsKeepIdentityAndResolveTableIdsByName() throws Exception {
        Answer<Void> jdbc = invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("dw_form_table_bindings")) {
                throw new AssertionError("frozen forms must not query live dw_form_table_bindings");
            }
            if (!(invocation.getArgument(1) instanceof RowCallbackHandler handler)) {
                return null;
            }
            if (sql.contains("dw_table_definitions")) {
                emitTable(handler, "p3_mi_file", 50001L, "File", "SUB");
                emitTable(handler, "p3_mi_case", 50000L, "Case", "MAIN");
            }
            return null;
        };
        doAnswer(jdbc).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any());
        doAnswer(jdbc).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(), any());

        TableBindingDTO snapshot = TableBindingDTO.builder()
                .bindingId(50729L)
                .bindingType("SUB")
                .tableName("p3_mi_file")
                .filterFkFieldName("case_id")
                .filterFkRefTableName("p3_mi_case")
                .build();
        FormContentDTO form = FormContentDTO.builder()
                .sourceId("42")
                .name("task-form")
                .tableBindings(List.of(snapshot))
                .build();

        loader.attachTableBindings(List.of(form));

        assertThat(form.getTableBindings()).hasSize(1);
        TableBindingDTO binding = form.getTableBindings().get(0);
        assertThat(binding.getBindingId()).isEqualTo(50729L);
        assertThat(binding.getTableId()).isEqualTo(50001L);
        assertThat(binding.getFilterFkRefTableId()).isEqualTo(50000L);
        assertThat(queriedLiveFormBindings()).isFalse();
    }

    private boolean queriedLiveFormBindings() {
        return mockingDetails(jdbcTemplate).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("query")
                        && invocation.getArguments().length > 0
                        && invocation.getArgument(0) instanceof String sql
                        && sql.contains("dw_form_table_bindings"));
    }

    private static void emitTable(
            RowCallbackHandler handler, String tableName, long id, String displayName, String tableType)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("table_name")).thenReturn(tableName);
        when(rs.getObject("id")).thenReturn(id);
        when(rs.getString("table_display_name")).thenReturn(displayName);
        when(rs.getString("table_type")).thenReturn(tableType);
        when(rs.getString("display_name")).thenReturn(null);
        handler.processRow(rs);
    }
}
