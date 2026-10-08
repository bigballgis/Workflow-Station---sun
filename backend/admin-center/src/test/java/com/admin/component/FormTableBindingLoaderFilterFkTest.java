package com.admin.component;

import com.admin.dto.response.TableBindingDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The loader must expose the binding's declared filter FK as a resolved name + target table id,
 * not as a {@code dw_field_definitions} id. Ids are environment-local; the classifier needs the
 * target table to tell two bindings of the same physical table apart.
 */
class FormTableBindingLoaderFilterFkTest {

    private static TableBindingDTO mapRow(ResultSet rs) throws Exception {
        FormTableBindingLoader loader = new FormTableBindingLoader(null, new ObjectMapper());
        Method m = FormTableBindingLoader.class.getDeclaredMethod("mapBindingRow", ResultSet.class);
        m.setAccessible(true);
        return (TableBindingDTO) m.invoke(loader, rs);
    }

    @Test
    @DisplayName("a declared filter FK is resolved to column name + ref table id")
    void declaredFilterFkIsResolved() throws Exception {
        ResultSet rs = baseRow();
        when(rs.getString("filter_fk_field_name")).thenReturn("main_id");
        when(rs.getObject("filter_fk_ref_table_id")).thenReturn(50332L);

        TableBindingDTO dto = mapRow(rs);

        assertThat(dto.getFilterFkFieldName()).isEqualTo("main_id");
        assertThat(dto.getFilterFkRefTableId()).isEqualTo(50332L);
    }

    @Test
    @DisplayName("an undeclared (or dangling) filter FK stays null rather than inventing a target")
    void undeclaredFilterFkStaysNull() throws Exception {
        ResultSet rs = baseRow();
        when(rs.getString("filter_fk_field_name")).thenReturn(null);
        when(rs.getObject("filter_fk_ref_table_id")).thenReturn(null);

        TableBindingDTO dto = mapRow(rs);

        assertThat(dto.getFilterFkFieldName()).isNull();
        assertThat(dto.getFilterFkRefTableId()).isNull();
    }

    private static ResultSet baseRow() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("binding_id")).thenReturn(271L);
        when(rs.getObject("table_id")).thenReturn(50330L);
        when(rs.getString("binding_type")).thenReturn("SUB");
        when(rs.getString("binding_mode")).thenReturn("EDITABLE");
        when(rs.getString("sub_mode")).thenReturn("FULL");
        when(rs.getString("foreign_key_field")).thenReturn("row_id");
        when(rs.getString("binding_link_mode")).thenReturn("structuralFk");
        when(rs.getInt("sort_order")).thenReturn(4);
        when(rs.getString("table_name")).thenReturn("attachment");
        when(rs.getString("table_display_name")).thenReturn("Attachment");
        when(rs.getString("table_type")).thenReturn("SUB");
        when(rs.getString("table_description")).thenReturn(null);
        when(rs.getArray("primary_key_fields")).thenReturn(null);
        return rs;
    }
}
