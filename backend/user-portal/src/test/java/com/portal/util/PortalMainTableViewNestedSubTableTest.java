package com.portal.util;

import com.portal.util.PortalMainTableViewNestedSubTables.NestedBinding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortalMainTableViewNestedSubTableTest {

    private static final NestedBinding CORRESPONDENCE = new NestedBinding(
            "atm_correspondence", "related_transaction_id");

    @Test
    void siblingInstanceRowsAreScopedByConfiguredForeignKey() {
        Map<String, Object> parent = Map.of("row_id", "ATM-DC-PW-TRANS-000025");
        Map<String, Object> instance = Map.of(
                "__subTables__", Map.of(
                        "dw:atm_correspondence", List.of(
                                Map.of(
                                        "correspondence_id", "Corr-000027",
                                        "related_transaction_id", "ATM-DC-PW-TRANS-000025"),
                                Map.of(
                                        "correspondence_id", "Corr-other",
                                        "related_transaction_id", "ATM-DC-PW-TRANS-000026"))));

        Map<String, Object> store = PortalMainTableViewNestedSubTables.forParentRow(
                parent, instance, List.of(CORRESPONDENCE));

        assertThat(store).containsOnlyKeys("dw:atm_correspondence");
        assertThat(correspondenceIds(store)).containsExactly("Corr-000027");
    }

    @Test
    void nestedCopyOnTheParentWinsOverTheInstanceSlice() {
        Map<String, Object> parent = Map.of(
                "row_id", "ATM-DC-PW-TRANS-000025",
                "__subTables__", Map.of(
                        "dw:atm_correspondence", List.of(Map.of("correspondence_id", "nested"))));
        Map<String, Object> instance = Map.of(
                "__subTables__", Map.of(
                        "dw:atm_correspondence", List.of(Map.of("correspondence_id", "instance"))));

        Map<String, Object> store = PortalMainTableViewNestedSubTables.forParentRow(
                parent, instance, List.of(CORRESPONDENCE));

        assertThat(correspondenceIds(store)).containsExactly("nested");
    }

    @Test
    void emptyCanonicalSliceOnTheParentIsKeptAndDoesNotFallThrough() {
        Map<String, Object> parent = Map.of(
                "row_id", "ATM-DC-PW-TRANS-000030",
                "__subTables__", Map.of("dw:atm_correspondence", List.of()));
        Map<String, Object> instance = Map.of(
                "__subTables__", Map.of(
                        "dw:atm_correspondence", List.of(
                                Map.of(
                                        "correspondence_id", "Corr-should-not-appear",
                                        "related_transaction_id", "ATM-DC-PW-TRANS-000030"))));

        Map<String, Object> store = PortalMainTableViewNestedSubTables.forParentRow(
                parent, instance, List.of(CORRESPONDENCE));

        assertThat(correspondenceIds(store)).isEmpty();
    }

    @Test
    void instanceRowsWithoutAConfiguredForeignKeyAreNotDumped() {
        NestedBinding noFk = new NestedBinding("atm_correspondence", null);
        Map<String, Object> parent = Map.of("row_id", "ATM-DC-PW-TRANS-000025");
        Map<String, Object> instance = Map.of(
                "__subTables__", Map.of(
                        "dw:atm_correspondence", List.of(Map.of("correspondence_id", "Corr-000027"))));

        Map<String, Object> store = PortalMainTableViewNestedSubTables.forParentRow(
                parent, instance, List.of(noFk));

        assertThat(correspondenceIds(store)).isEmpty();
    }

    private static List<Object> correspondenceIds(Map<String, Object> store) {
        Object raw = store.get("dw:atm_correspondence");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Object> ids = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map<?, ?> map) {
                ids.add(map.get("correspondence_id"));
            }
        }
        return ids;
    }
}
