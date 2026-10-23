package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.repository.FunctionUnitContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * A release imported before relation tables were snapshotted: existing requests read it with its relation tables
 * as deployed at the time, but a new request never starts on it.
 */
class FunctionUnitContentLegacyRelationTest {
    private static final String SQL = "SELECT content_data FROM sys_function_unit_contents WHERE function_unit_id = ? AND content_type = ? ORDER BY id";
    private static final String FORM = """
            {"formId":42,"formType":"PROCESS","configJson":{"rule":[]},
             "tableBindings":[{"bindingId":7,"bindingType":"RELATED","relationTableId":2}]}
            """;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FunctionUnitContentRepository contents = mock(FunctionUnitContentRepository.class);
    private final FunctionUnitLookup lookup = mock(FunctionUnitLookup.class);
    private final FunctionUnitContentComponent component = new FunctionUnitContentComponent(
            contents, jdbc, lookup, mock(FormTableBindingLoader.class), new ObjectMapper());

    @BeforeEach
    void legacyRelease() {
        ReflectionTestUtils.setField(component, "catalogSnapshots", new CatalogSnapshotReader(jdbc, new ObjectMapper()));
        var unit = FunctionUnit.builder().id("v1").code("synthetic").name("Synthetic")
                .version("1.0.0").status(FunctionUnitStatus.DEPLOYED).build();
        when(lookup.getById("v1")).thenReturn(unit);
        when(contents.findByFunctionUnitId("v1")).thenReturn(List.of(FunctionUnitContent.builder()
                .id("form-content").functionUnit(unit).contentType(ContentType.FORM).sourceId("42")
                .contentName("Published form").contentData(FORM).build()));
        when(jdbc.queryForList(SQL, String.class, "v1", "FORM")).thenReturn(List.of(FORM));
        when(jdbc.queryForList(SQL, String.class, "v1", "DATA_TABLE")).thenReturn(List.of(
                "{\"tableId\":23,\"tableName\":\"requests\",\"tableType\":\"MAIN\",\"fields\":[]}"));
        lenient().when(jdbc.queryForList(contains("content_checksum IS NULL"), eq(Boolean.class), eq("v1")))
                .thenReturn(List.of(true));
        Map<String, Object> row = new HashMap<>();
        row.put("id", 2L);
        row.put("table_name", "meeting_room");
        row.put("display_name", "Meeting Room");
        row.put("description", null);
        row.put("snapshot_data", "[{\"fieldName\":\"room\",\"dataType\":\"VARCHAR\",\"isPrimaryKey\":true}]");
        lenient().when(jdbc.queryForList(contains("rt_table_versions"), eq("2"), anyString(), eq("v1")))
                .thenReturn(List.of(row));
    }

    @Test
    void existingRequestsReadTheRelationTableAsDeployedWhenTheReleaseWasPublished() {
        var binding = component.assembleFunctionUnitContent("v1").getForms().get(0).getTableBindings().get(0);
        assertThat(binding.getTableName()).isEqualTo("meeting_room");
        assertThat(binding.getPrimaryKeyFields()).containsExactly("room");
    }

    @Test
    void aNewRequestNeverStartsOnALegacyRelease() {
        assertThatThrownBy(() -> component.assembleFunctionUnitContent("v1", true))
                .isInstanceOf(FunctionUnitVersionException.class)
                .hasMessageContaining("VERSION_SNAPSHOT_INCOMPLETE");
    }
}
