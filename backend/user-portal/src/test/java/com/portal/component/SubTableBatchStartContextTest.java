package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.BatchStartContext;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class SubTableBatchStartContextTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProcessStartComponent.ActiveCatalogPin pin = new ProcessStartComponent.ActiveCatalogPin("synthetic-catalog", "synthetic", "1");
    private static final String REFRESH = "Refresh the request form";

    @Test void samePublishedContentMatchesWithoutExposingOtherForms() throws Exception {
        var content = content(true, "Original");
        var context = SubTableBatchStartContext.capture(content, pin, mapper);
        assertThat(context.definitionDigest()).matches("[a-f0-9]{64}");
        assertThat(mapper.writeValueAsString(context)).doesNotContain("forms", "caption", "rule", "snapshot");
        assertThatCode(() -> SubTableBatchStartContext.validate(context, context, false, REFRESH)).doesNotThrowAnyException();
        assertThat(content).doesNotContainKeys("batchStartContext", SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
        assertThat(SubTableBatchStartContext.forPage(content, pin.catalogId()).get("batchStartContext")).isEqualTo(context);
    }

    @Test void rejectsMissingForgedAndSameCatalogOverwrittenContexts() throws Exception {
        var before = SubTableBatchStartContext.capture(content(true, "Original"), pin, mapper);
        var after = SubTableBatchStartContext.capture(content(true, "Changed"), pin, mapper);
        assertThat(after.catalogId()).isEqualTo(before.catalogId());
        for (BatchStartContext submitted : new BatchStartContext[] { null, before,
                new BatchStartContext(2, after.catalogId(), after.definitionDigest()),
                new BatchStartContext(1, "another-catalog", after.definitionDigest()) }) {
            assertThatThrownBy(() -> SubTableBatchStartContext.validate(submitted, after, false, REFRESH)).hasMessage(REFRESH);
        }
    }

    @Test void disablingBatchStillRejectsAnAlreadyOpenEnhancedPage() throws Exception {
        var before = SubTableBatchStartContext.capture(content(true, "Original"), pin, mapper);
        var disabled = SubTableBatchStartContext.capture(content(false, "Original"), pin, mapper);
        assertThat(disabled).isNull();
        assertThatThrownBy(() -> SubTableBatchStartContext.validate(before, disabled, false, REFRESH)).hasMessage(REFRESH);
        assertThatCode(() -> SubTableBatchStartContext.validate(null, disabled, false, REFRESH)).doesNotThrowAnyException();
    }

    @Test void bpmnChangesAreIncludedButLiveDeploymentMetadataIsNot() throws Exception {
        var content = content(true, "Original");
        var before = SubTableBatchStartContext.capture(content, pin, mapper);
        content.put("processes", List.of(Map.of("data", bpmn("synthetic"), "flowableProcessDefinitionId", "new-live-id")));
        assertThat(SubTableBatchStartContext.capture(content, pin, mapper)).isEqualTo(before);
        content.put("processes", List.of(Map.of("data", bpmn("changed-process"))));
        assertThat(SubTableBatchStartContext.capture(content, pin, mapper)).isNotEqualTo(before);
    }

    @Test void trustedServerProducersUseTheSameLoadedDesignWithoutAClientEcho() throws Exception {
        var context = SubTableBatchStartContext.capture(content(true, "Original"), pin, mapper);
        assertThatCode(() -> SubTableBatchStartContext.validate(null, context, true, REFRESH)).doesNotThrowAnyException();
    }

    static Map<String, Object> content(boolean enabled, String label) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var config = Map.of("rule", List.of(Map.of("type", "subTable", "batchControlId", "synthetic-control",
                "props", Map.of("_bindingId", 101, "allowBatchEdit", enabled))), "batchForms", Map.of("101",
                Map.of("schemaVersion", 1, "rule", List.of(Map.of("type", "input", "field", "caption", "title", label)))));
        var form = Map.of("sourceId", "synthetic-form", "formType", "TASK", "data", mapper.writeValueAsString(
                Map.of("configJson", config, "fieldPermissions", Map.of())), "tableBindings", List.of(
                Map.of("bindingId", 101, "tableName", "synthetic_entries", "bindingType", "SUB", "tableId", 20,
                        "bindingMode", "EDITABLE", "bindingLinkMode", "structuralFk", "foreignKeyField", "case_ref"),
                Map.of("bindingId", 102, "tableName", "synthetic_cases", "tableId", 10, "bindingType", "PRIMARY")));
        var entries = Map.of("tableName", "synthetic_entries", "fields", List.of(
                Map.of("fieldName", "item_token", "dataType", "TEXT", "isPrimaryKey", true),
                Map.of("fieldName", "case_ref", "isForeignKey", true, "refTableName", "synthetic_cases", "refPrimaryKeyFields", List.of("case_token")),
                Map.of("fieldName", "caption", "dataType", "TEXT", "nullable", false)));
        var cases = Map.of("tableName", "synthetic_cases", "tableType", "MAIN", "fields", List.of(
                Map.of("fieldName", "case_token", "dataType", "TEXT", "isPrimaryKey", true)));
        return new LinkedHashMap<>(Map.of("code", "synthetic", "version", "1", "forms", List.of(form),
                "processes", List.of(Map.of("data", bpmn("synthetic"))), "dataTables", List.of(
                        Map.of("data", mapper.writeValueAsString(entries)), Map.of("data", mapper.writeValueAsString(cases)))));
    }

    private static String bpmn(String id) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><process id=\"" + id + "\"/></definitions>";
    }
}
