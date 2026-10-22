package com.portal.util;

import com.portal.component.ActionTableRowRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionEmailRuleConditionEvaluatorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ActionEmailRuleConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ActionEmailRuleConditionEvaluator(jdbcTemplate);
    }

    @Test
    void actionFieldReadsJoinedRowAndBindsJoinParamsBeforeInstanceId() {
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("r0", true));

        boolean[] result = evaluator.evaluate("pi-1",
                List.of(filter(cond("remark_type", "eq", "urgent", "ACTION"))),
                new ActionTableRowRef("row-1"));

        assertThat(result).containsExactly(true);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForMap(sql.capture(), params.capture());
        assertThat(sql.getValue())
                .contains("ar.data->>'remark_type'")
                .contains("LEFT JOIN dw_table_data_rows ar ON ar.process_instance_id = pi.id AND ar.row_id = ?")
                .doesNotContain("pi.variables->>'remark_type'");
        assertThat(params.getValue()).containsExactly("urgent", "row-1", "pi-1");
    }

    @Test
    void mixedMainAndActionFieldsUseTheirOwnSources() {
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("r0", true));

        evaluator.evaluate("pi-1",
                List.of(filter(cond("amount", "eq", "5", null), cond("amount", "eq", "7", "ACTION"))),
                new ActionTableRowRef("row-1"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("pi.variables->>'amount'").contains("ar.data->>'amount'");
    }

    @Test
    void mainOnlyRulesDoNotJoinActionRow() {
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("r0", true));

        evaluator.evaluate("pi-1", List.of(filter(cond("amount", "eq", "5", null))),
                new ActionTableRowRef("row-1"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForMap(sql.capture(), params.capture());
        assertThat(sql.getValue()).doesNotContain("dw_table_data_rows");
        assertThat(params.getValue()).containsExactly("5", "pi-1");
    }

    @Test
    void actionRuleWithoutActionRowFailsClosedButMainRuleStillEvaluates() {
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("r0", true));

        boolean[] result = evaluator.evaluate("pi-1", List.of(
                filter(cond("remark_type", "eq", "urgent", "ACTION")),
                filter(cond("amount", "eq", "5", null)),
                Map.of()), null);

        assertThat(result).containsExactly(false, true, true);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).doesNotContain("dw_table_data_rows");
    }

    @Test
    void unsafeActionFieldNameFailsClosedWithoutQuery() {
        boolean[] result = evaluator.evaluate("pi-1",
                List.of(filter(cond("x'); DROP TABLE t; --", "eq", "1", "ACTION"))),
                new ActionTableRowRef("row-1"));

        assertThat(result).containsExactly(false);
        verify(jdbcTemplate, never()).queryForMap(anyString(), any(Object[].class));
    }

    private static Map<String, Object> filter(Map<String, Object>... conditions) {
        return Map.of("logic", "and", "conditions", List.of(conditions), "groups", List.of());
    }

    private static Map<String, Object> cond(String field, String op, String value, String source) {
        return source == null
                ? Map.of("fieldName", field, "operator", op, "value", value)
                : Map.of("fieldName", field, "operator", op, "value", value, "source", source);
    }
}
