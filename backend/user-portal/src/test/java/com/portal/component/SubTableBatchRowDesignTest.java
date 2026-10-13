package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchRowDesignTest {
    @Test
    void refusesEnablingAddWhenTheFormCannotSupplyRequiredFields() {
        Map<String, Object> design = Map.of("fields", List.of(Map.of("fieldName", "description", "nullable", false)));
        assertThatThrownBy(() -> SubTableBatchRowDesign.validateAddForm(design,
                Map.of("rule", List.of(Map.of("type", "input", "field", "other")))))
                .hasMessageContaining("required user-entered field");
    }
    @Test
    void freezesPublishedFieldsNotAdminLiveDesignerEnrichment() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> form = Map.of("tableBindings", List.of(Map.of(
                "bindingId", 101, "tableId", 900, "tableName", "lines", "bindingType", "SUB", "bindingMode", "EDITABLE",
                "bindingLinkMode", "structuralFk", "primaryKeyFields", List.of("live_key"),
                "fieldDefinitions", List.of(Map.of("fieldName", "live_key", "pkGeneration", Map.of("strategy", "uuid"))))));
        Map<String, Object> field = Map.of("fieldName", "published_key", "isPrimaryKey", true,
                "pkGenerationJson", Map.of("strategy", "prefixedSequence", "prefix", "PUB-"));
        Map<String, Object> content = Map.of("forms", List.of(form), "dataTables", List.of(Map.of("data",
                mapper.writeValueAsString(Map.of("tableName", "lines", "fields", List.of(field))))));
        Map<String, Object> design = SubTableBatchRowDesign.capture(form, "101", content, mapper);
        Map<?, ?> binding = (Map<?, ?>) design.get("binding");
        assertThat(binding.get("tableId")).isEqualTo(900);
        assertThat(binding.get("primaryKeyFields")).isEqualTo(List.of("published_key"));
        Map<?, ?> frozenField = (Map<?, ?>) ((List<?>) binding.get("fieldDefinitions")).get(0);
        assertThat(frozenField.get("pkGeneration")).isEqualTo(field.get("pkGenerationJson"));
        assertThat(frozenField.get("fieldName")).isEqualTo("published_key");
    }
}
