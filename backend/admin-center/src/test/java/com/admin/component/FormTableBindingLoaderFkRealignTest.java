package com.admin.component;

import com.admin.dto.response.TableFieldDefinitionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A child FK's {@code refPrimaryKeyFields} must be resolved from the referenced table's CURRENT
 * primary key columns, never from the copy stored on the FK row.
 *
 * <p>Regression (Portal To Do): renaming a parent table's PK in Table Design left every child FK
 * pointing at the old column name. Nothing failed loudly, so the break only surfaced at runtime as
 * "Please create a &lt;parent&gt; record before adding &lt;child&gt; data" — the parent row was
 * present and populated, but the FK guard was looking for a column the row does not have.
 *
 * <p>Resolving from live config makes a rename self-correcting: no data repair, and a stale stored
 * copy cannot break the runtime no matter how many times the column is renamed.
 */
class FormTableBindingLoaderFkRealignTest {

    private static final Long PARENT_TABLE_ID = 50331L;
    private static final Long CHILD_TABLE_ID = 50333L;

    private static void realign(
            Map<Long, List<TableFieldDefinitionDTO>> dwFields,
            Map<Long, List<TableFieldDefinitionDTO>> rtFields) throws Exception {
        FormTableBindingLoader loader = new FormTableBindingLoader(null, new ObjectMapper());
        Method m = FormTableBindingLoader.class.getDeclaredMethod(
                "realignForeignKeyReferencesToLivePrimaryKeys", Map.class, Map.class);
        m.setAccessible(true);
        m.invoke(loader, dwFields, rtFields);
    }

    private static TableFieldDefinitionDTO pk(String name) {
        return TableFieldDefinitionDTO.builder()
                .fieldName(name).isPrimaryKey(true).isForeignKey(false).build();
    }

    private static TableFieldDefinitionDTO fk(String name, Long refTableId, List<String> declaredRefPk) {
        return TableFieldDefinitionDTO.builder()
                .fieldName(name).isPrimaryKey(false).isForeignKey(true)
                .refTableId(refTableId).refPrimaryKeyFields(declaredRefPk).build();
    }

    @Test
    @DisplayName("a stale stored ref follows the parent's CURRENT pk, whatever it was renamed to")
    void staleRefFollowsCurrentParentPk() throws Exception {
        // The parent PK has been renamed repeatedly; the child FK still stores the original name.
        for (String currentParentPk : List.of("id_idwvvb", "id_idwvvbz", "id_idwnn", "something_else")) {
            TableFieldDefinitionDTO childFk = fk("sub_task_id", PARENT_TABLE_ID, List.of("id_idw"));
            Map<Long, List<TableFieldDefinitionDTO>> dw = new LinkedHashMap<>();
            dw.put(PARENT_TABLE_ID, List.of(pk(currentParentPk)));
            dw.put(CHILD_TABLE_ID, List.of(childFk));

            realign(dw, new LinkedHashMap<>());

            assertThat(childFk.getRefPrimaryKeyFields())
                    .as("FK must track the live PK '%s', not the stored copy", currentParentPk)
                    .containsExactly(currentParentPk);
        }
    }

    @Test
    @DisplayName("an already-correct ref is left exactly as it is")
    void correctRefUntouched() throws Exception {
        TableFieldDefinitionDTO childFk = fk("sub_task_id", PARENT_TABLE_ID, List.of("id_idwnn"));
        Map<Long, List<TableFieldDefinitionDTO>> dw = new LinkedHashMap<>();
        dw.put(PARENT_TABLE_ID, List.of(pk("id_idwnn")));
        dw.put(CHILD_TABLE_ID, List.of(childFk));

        realign(dw, new LinkedHashMap<>());

        assertThat(childFk.getRefPrimaryKeyFields()).containsExactly("id_idwnn");
    }

    @Test
    @DisplayName("composite parent keys are carried across in full")
    void compositeParentPkCarriedAcross() throws Exception {
        TableFieldDefinitionDTO childFk = fk("parent_ref", PARENT_TABLE_ID, List.of("old_a"));
        Map<Long, List<TableFieldDefinitionDTO>> dw = new LinkedHashMap<>();
        dw.put(PARENT_TABLE_ID, List.of(pk("key_a"), pk("key_b")));
        dw.put(CHILD_TABLE_ID, List.of(childFk));

        realign(dw, new LinkedHashMap<>());

        assertThat(childFk.getRefPrimaryKeyFields()).containsExactly("key_a", "key_b");
    }

    @Test
    @DisplayName("an FK into a table outside this form's bindings keeps what it had")
    void unknownParentLeavesRefAlone() throws Exception {
        TableFieldDefinitionDTO childFk = fk("external_ref", 99999L, List.of("whatever"));
        Map<Long, List<TableFieldDefinitionDTO>> dw = new LinkedHashMap<>();
        dw.put(CHILD_TABLE_ID, List.of(childFk));

        realign(dw, new LinkedHashMap<>());

        assertThat(childFk.getRefPrimaryKeyFields()).containsExactly("whatever");
    }

    @Test
    @DisplayName("relation-table fields participate on the same footing as dw tables")
    void relationTableFieldsAlsoRealign() throws Exception {
        TableFieldDefinitionDTO childFk = fk("rt_ref", PARENT_TABLE_ID, List.of("stale"));
        Map<Long, List<TableFieldDefinitionDTO>> dw = new LinkedHashMap<>();
        dw.put(PARENT_TABLE_ID, List.of(pk("live_pk")));
        Map<Long, List<TableFieldDefinitionDTO>> rt = new LinkedHashMap<>();
        rt.put(CHILD_TABLE_ID, List.of(childFk));

        realign(dw, rt);

        assertThat(childFk.getRefPrimaryKeyFields()).containsExactly("live_pk");
    }
}
