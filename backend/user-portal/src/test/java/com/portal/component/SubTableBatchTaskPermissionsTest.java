package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchTaskPermissionsTest {
    @Test
    void legacyInstancesKeepTheirOriginalPermissionAndMergePath() {
        Map<String, Object> form = Map.of("fieldPermissions", Map.of(), "publishedFieldPermissions", Map.of("101:field", "READONLY"));
        assertSame(form, SubTableBatchTaskPermissions.activate(Map.of(), form));
        Map<String, Object> inbound = new HashMap<>(Map.of("__subTables__", "legacy-value"));
        SubTableBatchTaskPermissions.preserveOmittedStores(Map.of(), inbound);
        assertEquals("legacy-value", inbound.get("__subTables__"));
        SubTableBatchTaskPermissions.validate(Map.of(), form, inbound, new ObjectMapper());
    }

    @Test
    void enhancedInstancesRequireActualPublishedPermissionAuthority() {
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of());
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchTaskPermissions.activate(variables,
                Map.of("fieldPermissions", Map.of())));
        Map<String, Object> active = SubTableBatchTaskPermissions.activate(variables,
                Map.of("fieldPermissions", Map.of(), "publishedFieldPermissions", Map.of("101:field", "READONLY")));
        assertEquals(Map.of("101:field", "READONLY"), active.get("fieldPermissions"));
    }

    @Test
    void partialSavesKeepOmittedTablesAndRejectMalformedRows() {
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of(),
                "__subTables__", Map.of("dw:one", List.of(Map.of("key", "one")), "dw:two", List.of(Map.of("key", "two"))));
        Map<String, Object> inbound = new HashMap<>(Map.of("__subTables__", Map.of("dw:one", List.of())));
        SubTableBatchTaskPermissions.preserveOmittedStores(variables, inbound);
        assertEquals(List.of(Map.of("key", "two")), ((Map<?, ?>) inbound.get("__subTables__")).get("dw:two"));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchTaskPermissions.preserveOmittedStores(
                variables, new HashMap<>(Map.of("__subTables__", Map.of("dw:one", List.of("invalid-row"))))));
    }

    @Test
    void editOnlyCannotBypassCurrentReadonlyPermissionByOmittingBatchIntent() {
        Map<String, Object> control = new HashMap<>(Map.of("batchControlId", "grid", "bindingId", "101", "rowDesign", Map.of(
                "binding", Map.of("tableName", "lines", "primaryKeyFields", List.of("entry_key")),
                "fields", List.of(Map.of("fieldName", "amount")))));
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1,
                "forms", Map.of("form", Map.of("controls", List.of(control)))),
                "__subTables__", Map.of("dw:lines", List.of(Map.of("entry_key", "A", "amount", 1))));
        Map<String, Object> form = Map.of("sourceId", "form", "fieldPermissions", Map.of("101:amount", "READONLY"),
                "tableBindings", List.of(Map.of("tableName", "lines", "bindingMode", "EDITABLE")));
        SubTableBatchTaskPermissions.validate(variables, form,
                Map.of("__subTables__", variables.get("__subTables__")), new ObjectMapper());
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchTaskPermissions.validate(variables,
                form, Map.of("__subTables__", Map.of("dw:lines", List.of(Map.of("entry_key", "A", "amount", 2)))), new ObjectMapper()));
        control.remove("rowDesign");
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchTaskPermissions.validate(variables,
                form, Map.of("__subTables__", variables.get("__subTables__")), new ObjectMapper()));
    }
}
