package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.repository.FunctionUnitContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.FunctionUnitVersionException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FunctionUnitContentComponentFormTypeTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FunctionUnitContentRepository contents = mock(FunctionUnitContentRepository.class);
    private final FunctionUnitLookup lookup = mock(FunctionUnitLookup.class);
    private final FormTableBindingLoader liveBindings = mock(FormTableBindingLoader.class);
    private final FunctionUnitContentComponent component = new FunctionUnitContentComponent(
            contents, jdbc, lookup, liveBindings, new ObjectMapper());

    private void snapshot(String type, String scene) {
        org.springframework.test.util.ReflectionTestUtils.setField(component, "catalogSnapshots",
                new com.platform.common.functionunit.CatalogSnapshotReader(jdbc, new ObjectMapper()));
        var unit = FunctionUnit.builder().id("v1").code("synthetic").name("Synthetic")
                .version("1.0.0").status(FunctionUnitStatus.DEPLOYED).build();
        String form = """
                {"formId":42,"formType":"%s","scene":"%s","configJson":{"rule":[]},
                 "fieldPermissions":{"amount":"READONLY"},
                 "tableBindings":[{"bindingId":7,"tableName":"requests","bindingType":"PRIMARY","bindingMode":"EDITABLE"}]}
                """.formatted(type, scene);
        when(lookup.getById("v1")).thenReturn(unit);
        when(contents.findByFunctionUnitId("v1")).thenReturn(List.of(FunctionUnitContent.builder()
                .id("form-content").functionUnit(unit).contentType(ContentType.FORM).sourceId("42")
                .contentName("Published form").contentData(form).build()));
        String sql = "SELECT content_data FROM sys_function_unit_contents WHERE function_unit_id = ? AND content_type = ? ORDER BY id";
        when(jdbc.queryForList(sql, String.class, "v1", "FORM")).thenReturn(List.of(form));
        when(jdbc.queryForList(sql, String.class, "v1", "DATA_TABLE")).thenReturn(List.of("""
                {"tableId":23,"tableName":"requests","tableType":"MAIN","fields":[
                  {"fieldName":"case_ref","dataType":"VARCHAR","isPrimaryKey":true}]}
                """));
    }

    @Test void processFormTypeAndSceneComeFromTheRelease() {
        snapshot("PROCESS", "REQUEST");
        var form = component.assembleFunctionUnitContent("v1").getForms().get(0);
        assertThat(form.getFormType()).isEqualTo("PROCESS");
        assertThat(form.getScene()).isEqualTo("REQUEST");
        assertThat(form.getTableBindings().get(0).getPrimaryKeyFields()).containsExactly("case_ref");
        verifyNoInteractions(liveBindings);
    }

    @Test void detailFormAlsoUsesFrozenLayoutAndBindings() {
        snapshot("DETAIL", "REQUEST");
        var form = component.assembleFunctionUnitContent("v1").getForms().get(0);
        assertThat(form.getFormType()).isEqualTo("DETAIL");
        assertThat(form.getData()).contains("rule");
        assertThat(form.getTableBindings().get(0).getTableId()).isEqualTo(23L);
        verifyNoInteractions(liveBindings);
    }

    @Test void taskFormRetainsPublishedPermissionsWithoutDesignerTables() {
        snapshot("TASK", "TASK");
        var form = component.assembleFunctionUnitContent("v1").getForms().get(0);
        assertThat(form.getPublishedFieldPermissions()).containsEntry("amount", "READONLY");
        verifyNoInteractions(liveBindings);
    }
}
