package com.admin.component;

import com.admin.dto.response.TableBindingDTO;
import com.admin.exception.AdminBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogFormSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyBareConfigJsonIsNotFrozen() {
        CatalogFormSnapshot.Payload payload = CatalogFormSnapshot.unwrap(objectMapper, "{\"rule\":[]}");
        assertThat(payload.freezeBindings()).isFalse();
        assertThat(payload.tableBindings()).isNull();
        assertThat(payload.configJson()).isEqualTo("{\"rule\":[]}");
    }

    @Test
    void fullFormWithoutTableBindingsKeyIsNotFrozen() {
        String json = """
                {"formName":"Start","formType":"PROCESS","configJson":{"rule":[]}}
                """;
        CatalogFormSnapshot.Payload payload = CatalogFormSnapshot.unwrap(objectMapper, json);
        assertThat(payload.freezeBindings()).isFalse();
        assertThat(payload.configJson()).isEqualTo("{\"rule\":[]}");
    }

    @Test
    void fullFormWithTableBindingsIsFrozen() {
        String json = """
                {"formName":"Task","formType":"TASK","configJson":{"snapshot":true},
                 "tableBindings":[
                   {"bindingId":50729,"bindingType":"SUB","tableName":"p3_mi_file",
                    "filterFkFieldName":"case_id","filterFkRefTableName":"p3_mi_case"}
                 ]}
                """;
        CatalogFormSnapshot.Payload payload = CatalogFormSnapshot.unwrap(objectMapper, json);
        assertThat(payload.freezeBindings()).isTrue();
        assertThat(payload.configJson()).isEqualTo("{\"snapshot\":true}");
        assertThat(payload.tableBindings()).hasSize(1);
        TableBindingDTO binding = payload.tableBindings().get(0);
        assertThat(binding.getBindingId()).isEqualTo(50729L);
        assertThat(binding.getTableName()).isEqualTo("p3_mi_file");
        assertThat(binding.getFilterFkFieldName()).isEqualTo("case_id");
        assertThat(binding.getFilterFkRefTableName()).isEqualTo("p3_mi_case");
    }

    @Test
    void emptyTableBindingsArrayStillFreezes() {
        String json = """
                {"formName":"Start","formType":"PROCESS","configJson":{},"tableBindings":[]}
                """;
        CatalogFormSnapshot.Payload payload = CatalogFormSnapshot.unwrap(objectMapper, json);
        assertThat(payload.freezeBindings()).isTrue();
        assertThat(payload.tableBindings()).isEmpty();
    }

    @Test
    void tableBindingsMustBeAnArray() {
        String json = """
                {"formName":"Start","formType":"PROCESS","configJson":{},"tableBindings":"nope"}
                """;
        assertThatThrownBy(() -> CatalogFormSnapshot.unwrap(objectMapper, json))
                .isInstanceOf(AdminBusinessException.class)
                .extracting(ex -> ((AdminBusinessException) ex).getErrorCode())
                .isEqualTo("INVALID_FORM_SNAPSHOT");
    }
}
