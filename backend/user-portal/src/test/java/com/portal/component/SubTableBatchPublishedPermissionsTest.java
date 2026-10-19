package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchPublishedPermissionsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsAdminUnwrappedConfigOnlyWithExplicitPublishedAuthority() {
        assertEquals(Map.of("amount", "READONLY"), SubTableBatchPublishedPermissions.capture(
                Map.of("data", "{\"rule\":[]}", "publishedFieldPermissions", Map.of("101:amount", "READONLY")),
                "101", List.of(Map.of("fieldName", "amount")), mapper));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchPublishedPermissions.capture(
                Map.of("data", "{\"rule\":[]}"), "101", List.of(Map.of("fieldName", "amount")), mapper));
    }

    @Test
    void expandsOnlyPublishedSchemaFieldsAndNeverConsultsLiveMetadata() throws Exception {
        Map<String, Object> form = Map.of("data", mapper.writeValueAsString(Map.of("configJson", Map.of(),
                "fieldPermissions", Map.of("101:restricted", "READONLY", "anotherBinding:ordinary", "READONLY"))));
        List<?> fields = List.of(Map.of("fieldName", "ordinary"), Map.of("fieldName", "restricted"),
                Map.of("fieldName", "parentRef", "isForeignKey", true), Map.of("fieldName", "manualKey", "isPrimaryKey", true,
                        "pkGenerationJson", Map.of("strategy", "manual")));
        assertEquals(Map.of("ordinary", "EDITABLE", "restricted", "READONLY", "parentRef", "READONLY", "manualKey", "EDITABLE"),
                SubTableBatchPublishedPermissions.capture(form, "101", fields, mapper));
    }

    @Test
    void missingOrInvalidPermissionAuthorityNeverMeansAllFieldsWritable() {
        List<?> fields = List.of(Map.of("fieldName", "ordinary"));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchPublishedPermissions.capture(
                Map.of("data", "{\"configJson\":{}}"), "101", fields, mapper));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchPublishedPermissions.capture(
                Map.of("data", "{\"fieldPermissions\":{\"ordinary\":\"UNKNOWN\"}}"), "101", fields, mapper));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchPublishedPermissions.validateAddedValues(
                Map.of("fields", fields), Map.of("ordinary", "forged")));
    }

    @Test
    void fixedReadonlyDefaultsPreserveZeroAndFalseButCannotBeReplaced() {
        Map<String, Object> design = Map.of("fieldWriteModes", Map.of("quantity", "READONLY", "enabled", "READONLY"),
                "fields", List.of(Map.of("fieldName", "quantity", "dataType", "INTEGER", "defaultValue", "0"),
                        Map.of("fieldName", "enabled", "dataType", "BOOLEAN", "defaultValue", "0")));
        assertDoesNotThrow(() -> SubTableBatchPublishedPermissions.validateAddedValues(design, Map.of("quantity", 0, "enabled", false)));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchPublishedPermissions.validateAddedValues(
                design, Map.of("quantity", 1, "enabled", false)));
    }

    @Test
    void manualPkAndStructuralReferencesStillCannotBeEditedEvenIfTheyAppearInTheBatchForm() {
        Map<String, Object> design = Map.of("fieldWriteModes", Map.of("manualKey", "EDITABLE", "parentRef", "EDITABLE"),
                "fields", List.of(Map.of("fieldName", "manualKey", "isPrimaryKey", true),
                        Map.of("fieldName", "parentRef", "isForeignKey", true)));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchPublishedPermissions.validateEditedFields(design, List.of("manualKey")));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchPublishedPermissions.validateEditedFields(design, List.of("parentRef")));
    }
}
