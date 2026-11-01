package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.fk.PrimaryKeyAllocationService;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.portal.dto.SubTableBindingScope;
import com.portal.service.UserDisplayNameResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessStartScopedFkEnrichmentTest {
    @Test void doesNotAddAncestorForeignKeyToTheOtherBinding() { enrich(false, false); }
    @Test void explicitlyDeclaredAncestorFillStillWorks() { enrich(true, false); }
    @Test void doesNotEraseAnIllegalClientForeignKeyToBypassTheGuard() { enrich(false, true); }

    private void enrich(boolean explicit, boolean illegal) {
        var reader = mock(CatalogSnapshotReader.class);
        var design = mock(SubTableWriteDesign.class);
        var tables = List.of(table(1, "RenamedRoot", List.of(pk("rootKey"))),
                table(2, "RenamedLines", List.of(pk("lineKey"), fk("rootRef", "RenamedRoot", "rootKey"))),
                table(3, "RenamedMessages", List.of(pk("messageKey"),
                        fk("rootRef", "RenamedRoot", "rootKey"), fk("lineRef", "RenamedLines", "lineKey"))));
        when(reader.contents("catalog", "DATA_TABLE")).thenReturn(tables);
        when(reader.processForm("catalog", false)).thenReturn(Map.of("tableBindings",
                List.of(Map.of("bindingType", "PRIMARY", "tableId", 1))));
        when(reader.tableAliases("catalog")).thenReturn(Map.of("dw:renamedlines", tables.get(1),
                "dw:renamedmessages", tables.get(2)));
        String store = "dw:renamedmessages";
        when(design.resolve("catalog", null)).thenReturn(Map.of(
                "10", new SubTableWriteDesign.Binding("10", store, "rootRef", "MAIN", "RenamedRoot",
                        List.of("messageKey"), List.of("rootKey"), "structuralFk", List.of("rootRef", "lineRef"), List.of()),
                "30", new SubTableWriteDesign.Binding("30", "dw:renamedlines", "rootRef", "MAIN", "RenamedRoot",
                        List.of("lineKey"), List.of("rootKey"), "structuralFk", List.of("rootRef"), List.of()),
                "20", new SubTableWriteDesign.Binding("20", store, "lineRef", "SUB", "RenamedLines",
                        List.of("messageKey"), List.of("lineKey"), "structuralFk", List.of("rootRef", "lineRef"),
                        explicit ? List.of("rootRef") : List.of())));
        var names = mock(UserDisplayNameResolver.class);
        when(names.resolve("actor")).thenReturn("Tester");
        var enricher = new ProcessStartFormEnricherComponent(mock(JdbcTemplate.class), new ObjectMapper(),
                mock(PrimaryKeyAllocationService.class), mock(PortalPrimaryKeyAllocationComponent.class),
                mock(ProcessSubTablePrimaryKeyEnricherComponent.class), mock(OwnerFieldComponent.class),
                mock(ComputedFieldRecalculator.class), mock(RequestIdEnricher.class), names);
        ReflectionTestUtils.setField(enricher, "catalogSnapshots", reader);
        ReflectionTestUtils.setField(enricher, "subTableWriteDesign", design);
        Map<String,Object> child = new LinkedHashMap<>(Map.of("messageKey", "LINE-MESSAGE"));
        if (illegal) child.put("rootRef", "ILLEGAL");
        Map<String,Object> rootMessage = new LinkedHashMap<>(Map.of("messageKey", "ROOT-MESSAGE"));
        Map<String,Object> line = new LinkedHashMap<>(Map.of("lineKey", "LINE", "__subTables__", Map.of(store, new ArrayList<>(List.of(child)))));
        Map<String,Object> variables = new LinkedHashMap<>(Map.of("functionUnitId", "catalog", "rootKey", "ROOT",
                "__subTables__", Map.of("dw:renamedlines", new ArrayList<>(List.of(line)), store, new ArrayList<>(List.of(rootMessage)))));
        enricher.enrichOnInsert("arbitrary-code", "actor", variables, List.of(
                new SubTableBindingScope("10", store, List.of(Map.of("messageKey", "ROOT-MESSAGE")), false),
                new SubTableBindingScope("20", store, List.of(Map.of("messageKey", "LINE-MESSAGE")), false)));
        assertThat(line).containsEntry("rootRef", "ROOT");
        assertThat(rootMessage).containsEntry("rootRef", "ROOT").doesNotContainKey("lineRef");
        if (!illegal) assertThat(child).containsEntry("lineRef", "LINE");
        if (illegal) assertThat(child).containsEntry("rootRef", "ILLEGAL");
        else if (explicit) assertThat(child).containsEntry("rootRef", "ROOT");
        else assertThat(child).doesNotContainKey("rootRef");
    }
    private Map<String,Object> table(long id, String name, List<Map<String,Object>> fields) {
        return Map.of("tableId", id, "tableName", name, "fields", fields);
    }
    private Map<String,Object> pk(String name) { return Map.of("fieldName", name, "isPrimaryKey", true); }
    private Map<String,Object> fk(String name, String parent, String pk) {
        return Map.of("fieldName", name, "isForeignKey", true, "refTableName", parent, "refPrimaryKeyFields", List.of(pk));
    }
}
