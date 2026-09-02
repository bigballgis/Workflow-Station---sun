package com.workflow.listener;

import com.workflow.component.BpmnActionParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MI 进度列（Sub-Task Config 的 Sub-task status column / Current node column）只认配置。
 *
 * <p>历史行为是配置缺失时兜底成字面量 {@code task_status} / {@code task_current_node}。
 * 这两个名字只是约定：实测 demo FU 50005 的子表 {@code subtable} 上真实列名是
 * {@code task_statuss} / {@code task_current_nodes}，兜底的 UPDATE 会打到不存在的列上被
 * 静默跳过，表现为「配了却不生效」。本测试锁定：**没有默认列名，未配置就不写**。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MI progress columns come from Sub-Task Config only")
class MultiInstanceProgressColumnConfigTest {

    @Mock
    private BpmnActionParser bpmnActionParser;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private final MultiInstanceTaskWriter writer = new MultiInstanceTaskWriter();

    private static final String PD_ID = "pd:1:1";
    private static final String TASK_KEY = "subForm1";

    private TaskAssignmentListener ownerWithParser() {
        TaskAssignmentListener owner = mock(TaskAssignmentListener.class);
        when(owner.bpmnActionParser()).thenReturn(bpmnActionParser);
        when(owner.jdbcTemplate()).thenReturn(jdbcTemplate);
        return owner;
    }

    private void stubConfig(String statusField, String nodeField) {
        when(bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(
                PD_ID, TASK_KEY, "miTaskStatusField")).thenReturn(statusField);
        when(bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(
                PD_ID, TASK_KEY, "miTaskCurrentNodeField")).thenReturn(nodeField);
    }

    @Test
    @DisplayName("configured column names are used verbatim")
    void usesConfiguredColumnNames() {
        TaskAssignmentListener owner = ownerWithParser();
        stubConfig("task_statuss", "task_current_nodes");

        String[] cols = writer.resolveMiProgressColumnNames(owner, PD_ID, TASK_KEY);

        assertThat(cols).containsExactly("task_statuss", "task_current_nodes");
    }

    @Test
    @DisplayName("no config -> null, NOT the legacy task_status / task_current_node literals")
    void missingConfigYieldsNullInsteadOfLegacyDefaults() {
        TaskAssignmentListener owner = ownerWithParser();
        stubConfig(null, null);

        String[] cols = writer.resolveMiProgressColumnNames(owner, PD_ID, TASK_KEY);

        assertThat(cols).containsExactly(null, null);
        // 回归守卫：兜底列名一旦回来，这两条就红。
        assertThat(cols).doesNotContain("task_status", "task_current_node");
    }

    @Test
    @DisplayName("blank / non-identifier config is rejected without falling back to a literal")
    void blankOrUnsafeConfigYieldsNull() {
        TaskAssignmentListener owner = ownerWithParser();
        stubConfig("   ", "drop table; --");

        String[] cols = writer.resolveMiProgressColumnNames(owner, PD_ID, TASK_KEY);

        assertThat(cols).containsExactly(null, null);
    }

    @Test
    @DisplayName("missing process definition / task key yields null, not defaults")
    void missingIdentifiersYieldNull() {
        TaskAssignmentListener owner = mock(TaskAssignmentListener.class);
        when(owner.bpmnActionParser()).thenReturn(bpmnActionParser);

        assertThat(writer.resolveMiProgressColumnNames(owner, null, TASK_KEY))
                .containsExactly(null, null);
        assertThat(writer.resolveMiProgressColumnNames(owner, PD_ID, "  "))
                .containsExactly(null, null);
    }

    @Test
    @DisplayName("both columns unconfigured -> no UPDATE is issued at all")
    void unconfiguredColumnsIssueNoUpdate() {
        TaskAssignmentListener owner = ownerWithParser();

        writer.updateSubTableTaskProgress(owner, "subtable", rowKey(), "sub form1", null, null);

        verify(jdbcTemplate, never()).update(anyString(), (Object) any(), (Object) any());
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    @DisplayName("only the configured column is written when the other is unset")
    void writesOnlyTheConfiguredColumn() {
        TaskAssignmentListener owner = ownerWithParser();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), eq("task_statuss")))
                .thenReturn(1);

        try (var pk = org.mockito.Mockito.mockStatic(
                com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys.class)) {
            pk.when(() -> com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys
                            .resolvePrimaryKeyColumns(any(), eq("subtable")))
                    .thenReturn(java.util.List.of("id_idwze"));

            writer.updateSubTableTaskProgress(owner, "subtable", rowKey(), "sub form1", "task_statuss", null);
        }

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), org.mockito.ArgumentMatchers.<Object>any(),
                org.mockito.ArgumentMatchers.<Object>any());
        assertThat(sql.getValue()).contains("task_statuss");
        assertThat(sql.getValue()).doesNotContain("task_current_node");
    }

    private static Map<String, Object> rowKey() {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("id_idwze", 1L);
        return k;
    }
}
