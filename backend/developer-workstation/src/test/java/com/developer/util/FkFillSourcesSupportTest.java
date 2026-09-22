package com.developer.util;

import com.developer.dto.FkFillSource;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FkFillSourcesSupportTest {

    @Test
    void toPortableUsesFieldAndTableNamesNotIds() {
        TableDefinition party = TableDefinition.builder().id(2L).tableName("party").build();
        party.setFieldDefinitions(List.of(FieldDefinition.builder().id(21L).fieldName("case_id").build()));
        FormTableBinding ancestor = FormTableBinding.builder()
                .id(11L)
                .table(party)
                .filterFkFieldId(21L)
                .build();

        TableDefinition file = TableDefinition.builder().id(3L).tableName("file").build();
        file.setFieldDefinitions(List.of(FieldDefinition.builder().id(31L).fieldName("party_id").build()));
        FormTableBinding fileBinding = FormTableBinding.builder()
                .id(20L)
                .table(file)
                .fkFillSources(List.of(FkFillSource.builder()
                        .fieldId(31L)
                        .kind(FkFillSource.KIND_ANCESTOR)
                        .ancestorBindingId(11L)
                        .build()))
                .build();

        List<Map<String, Object>> portable = FkFillSourcesSupport.toPortable(
                fileBinding, List.of(ancestor, fileBinding), Map.of(2L, "party", 3L, "file"));
        assertEquals(1, portable.size());
        assertEquals("party_id", portable.get(0).get("fieldName"));
        assertEquals("ANCESTOR", portable.get(0).get("kind"));
        assertEquals("party", portable.get(0).get("ancestorTableName"));
        assertEquals("case_id", portable.get(0).get("ancestorFilterFkFieldName"));
        assertNull(portable.get(0).get("fieldId"));
        assertNull(portable.get(0).get("ancestorBindingId"));
    }

    @Test
    void fromPortableResolvesNamesBackToLocalIds() {
        TableDefinition party = TableDefinition.builder().id(200L).tableName("party").build();
        party.setFieldDefinitions(List.of(FieldDefinition.builder().id(210L).fieldName("case_id").build()));
        FormTableBinding ancestor = FormTableBinding.builder().id(1100L).table(party).filterFkFieldId(210L).build();

        TableDefinition file = TableDefinition.builder().id(300L).tableName("file").build();
        file.setFieldDefinitions(List.of(FieldDefinition.builder().id(310L).fieldName("party_id").build()));

        List<FkFillSource> restored = FkFillSourcesSupport.fromPortable(
                List.of(Map.of(
                        "fieldName", "party_id",
                        "kind", "ANCESTOR",
                        "ancestorTableName", "party",
                        "ancestorFilterFkFieldName", "case_id")),
                file,
                Map.of(FkFillSourcesSupport.ancestorKey(ancestor), 1100L));
        assertEquals(1, restored.size());
        assertEquals(310L, restored.get(0).getFieldId());
        assertEquals("party_id", restored.get(0).getFieldName());
        assertEquals(1100L, restored.get(0).getAncestorBindingId());
    }

    @Test
    void remapDropsAncestorWhenTargetBindingMissing() {
        List<FkFillSource> remapped = FkFillSourcesSupport.remapAncestorBindingIds(
                List.of(FkFillSource.builder()
                        .fieldId(1L)
                        .kind(FkFillSource.KIND_ANCESTOR)
                        .ancestorBindingId(99L)
                        .build()),
                Map.of(11L, 1100L));
        assertNull(remapped);
    }
}
