package com.portal.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The identity guarantee lives on the persistence callback precisely because the write
 * paths into {@code __subTables__} are many; this pins that placement so it cannot be
 * quietly relocated to one of them.
 */
class ProcessInstanceSubTableRowIdentityTest {

    private ProcessInstance instanceWithRows(List<Map<String, Object>> rows) {
        Map<String, Object> subTables = new LinkedHashMap<>();
        subTables.put("50533", rows);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("__subTables__", subTables);
        ProcessInstance instance = new ProcessInstance();
        instance.setVariables(variables);
        return instance;
    }

    @Test
    void savingIdentifiesRowsThatArrivedWithoutAnyIdentityKey() {
        Map<String, Object> anonymous = new LinkedHashMap<>(Map.of("card_number", "4111"));
        Map<String, Object> allocated = new LinkedHashMap<>(Map.of("id_idw", 5001));
        List<Map<String, Object>> rows = new ArrayList<>(List.of(anonymous, allocated));

        instanceWithRows(rows).ensureSubTableRowIdentity();

        assertThat(String.valueOf(anonymous.get("row_id"))).isNotBlank();
        assertThat(allocated).doesNotContainKey("row_id");
    }

    @Test
    void instancesWithoutVariablesSaveUnchanged() {
        ProcessInstance instance = new ProcessInstance();
        instance.ensureSubTableRowIdentity();
        assertThat(instance.getVariables()).isNull();
    }
}
