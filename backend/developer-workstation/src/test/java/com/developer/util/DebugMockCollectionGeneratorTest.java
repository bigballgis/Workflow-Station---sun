package com.developer.util;

import com.developer.entity.FieldDefinition;
import com.developer.enums.DataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DebugMockCollectionGenerator")
class DebugMockCollectionGeneratorTest {

    @Test
    @DisplayName("Should generate rows from sub-table field definitions")
    void shouldGenerateRowsFromFields() {
        List<FieldDefinition> fields = List.of(
                FieldDefinition.builder().fieldName("name").dataType(DataType.VARCHAR).build(),
                FieldDefinition.builder().fieldName("assignee_id").dataType(DataType.VARCHAR).build(),
                FieldDefinition.builder().fieldName("quantity").dataType(DataType.INTEGER).build()
        );

        List<Map<String, Object>> rows = DebugMockCollectionGenerator.generate(fields, 3);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).get("rowId")).isEqualTo("debug-row-1");
        assertThat(rows.get(0).get("assignee_id")).isEqualTo("debug-user-1");
        assertThat(rows.get(1).get("assignee_id")).isEqualTo("debug-user-2");
        assertThat(rows.get(2).get("quantity")).isEqualTo(3);
    }
}
