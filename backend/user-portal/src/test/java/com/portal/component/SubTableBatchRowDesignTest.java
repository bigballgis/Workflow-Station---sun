package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchRowDesignTest {
    @Test
    void readonlyRequiredBusinessFieldsNeedVerifiedLiteralDefaultsBeforeEnablingAdd() {
        Map<String, Object> batch = Map.of("rule", List.of(Map.of("type", "inputNumber", "field", "amount")));
        Map<String, Object> required = Map.of("fieldName", "amount", "dataType", "INTEGER", "nullable", false);
        assertThatThrownBy(() -> SubTableBatchRowDesign.validateAddForm(Map.of("fields", List.of(required),
                "fieldWriteModes", Map.of("amount", "READONLY")), batch)).hasMessageContaining("default");
        Map<String, Object> literal = new java.util.HashMap<>(required);
        literal.put("defaultValue", "0");
        org.assertj.core.api.Assertions.assertThatCode(() -> SubTableBatchRowDesign.validateAddForm(Map.of("fields", List.of(literal),
                "fieldWriteModes", Map.of("amount", "READONLY")), batch)).doesNotThrowAnyException();
        literal.put("defaultValue", "1.5");
        assertThatThrownBy(() -> SubTableBatchRowDesign.validateAddForm(Map.of("fields", List.of(literal),
                "fieldWriteModes", Map.of("amount", "READONLY")), batch)).hasMessageContaining("default");
    }
    @Test
    void refusesFieldsThatAreNotBoundOrAreUnsupportedAttachments() {
        Map<String, Object> form = Map.of("rule", List.of(Map.of("type", "input", "field", "receipt")));
        assertThatThrownBy(() -> SubTableBatchRowDesign.validateAddForm(Map.of("fields", List.of(
                Map.of("fieldName", "description", "nullable", true))), form))
                .hasMessageContaining("unbound field");
        assertThatThrownBy(() -> SubTableBatchRowDesign.validateAddForm(Map.of("fields", List.of(
                Map.of("fieldName", "receipt", "dataType", "FILE", "nullable", true))), form))
                .hasMessageContaining("attachment input is unsupported");
    }
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
                "fieldDefinitions", List.of(Map.of("fieldName", "live_key", "pkGeneration", Map.of("strategy", "uuid"))))),
                "data", mapper.writeValueAsString(Map.of("configJson", Map.of(), "fieldPermissions", Map.of())));
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
        assertThat(design.get("fieldWriteModes")).isEqualTo(Map.of("published_key", "READONLY"));
    }
}
