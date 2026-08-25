package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompletedTaskSnapshotAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskFormFieldMapper fieldMapper = new TaskFormFieldMapper();

    @Test
    void noFormLeavesFieldValuesEmptyAndDropsLiveSubTables() {
        Map<String, Object> live = new HashMap<>();
        live.put("case_number", "ATM-DC-PW-000005");
        live.put("__subTables__", Map.of("1135", List.of(Map.of("arn", "1"))));

        Map<String, Object> fieldValues = CompletedTaskSnapshotAssembler.assembleFieldValues(
                live, Set.of("case_number"), false, fieldMapper, objectMapper);

        assertThat(fieldValues).isEmpty();
        assertThat(live.get("__subTables__")).isEqualTo(Map.of("1135", List.of(Map.of("arn", "1"))));
    }

    @Test
    void emptyPermissionsStillFreezesMainFieldsAndCanonicalSubTables() {
        Map<String, Object> liveRow = new HashMap<>();
        liveRow.put("arn", "1");
        liveRow.put("row_id", "ATM-DC-PW-TRANS-000005");
        Map<String, Object> nested = new HashMap<>();
        nested.put("1141", List.of(Map.of("correspondence_id", "Corr-000005")));
        nested.put("ATM Correspondence", List.of(Map.of("correspondence_id", "Corr-000005")));
        liveRow.put("__subTables__", nested);

        Map<String, Object> liveSub = new HashMap<>();
        liveSub.put("1135", List.of(liveRow));
        liveSub.put("ATM Transaction", List.of(liveRow));
        liveSub.put("atm transaction", List.of(liveRow));
        liveSub.put("1141", List.of(Map.of("correspondence_id", "Corr-000005")));
        liveSub.put("ATM Correspondence", List.of(Map.of("correspondence_id", "Corr-000005")));

        Map<String, Object> live = new HashMap<>();
        live.put("case_number", "ATM-DC-PW-000005");
        live.put("card_number", "1");
        live.put("__subTables__", liveSub);

        Map<String, Object> fieldValues = CompletedTaskSnapshotAssembler.assembleFieldValues(
                live, Set.of("case_number", "card_number"), true, fieldMapper, objectMapper);

        assertThat(fieldValues)
                .containsEntry("case_number", "ATM-DC-PW-000005")
                .containsEntry("card_number", "1")
                .containsKey("__subTables__");

        @SuppressWarnings("unchecked")
        Map<String, Object> frozen = (Map<String, Object>) fieldValues.get("__subTables__");
        assertThat(frozen).containsOnlyKeys("1135", "1141");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txRows = (List<Map<String, Object>>) frozen.get("1135");
        @SuppressWarnings("unchecked")
        Map<String, Object> frozenNested = (Map<String, Object>) txRows.get(0).get("__subTables__");
        assertThat(frozenNested).containsOnlyKeys("1141");

        assertThat(liveSub).containsKeys("ATM Transaction", "atm transaction", "ATM Correspondence");
    }

    @Test
    void formWithOnlySubTablesStillFreezesCanonicalSlices() {
        Map<String, Object> live = new HashMap<>();
        live.put("__subTables__", Map.of("1135", List.of(Map.of("arn", "1"))));

        Map<String, Object> fieldValues = CompletedTaskSnapshotAssembler.assembleFieldValues(
                live, Set.of(), true, fieldMapper, objectMapper);

        assertThat(fieldValues).containsOnlyKeys("__subTables__");
        @SuppressWarnings("unchecked")
        Map<String, Object> frozen = (Map<String, Object>) fieldValues.get("__subTables__");
        assertThat(frozen).containsOnlyKeys("1135");
    }

    @Test
    void snapshotCopyDoesNotShareLiveSubTableMap() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new HashMap<>(Map.of("arn", "1")));
        Map<String, Object> liveSub = new HashMap<>();
        liveSub.put("1135", rows);
        Map<String, Object> live = new HashMap<>();
        live.put("case_number", "X");
        live.put("__subTables__", liveSub);

        Map<String, Object> fieldValues = CompletedTaskSnapshotAssembler.assembleFieldValues(
                live, Set.of("case_number"), true, fieldMapper, objectMapper);

        @SuppressWarnings("unchecked")
        Map<String, Object> frozen = (Map<String, Object>) fieldValues.get("__subTables__");
        frozen.put("9999", List.of());
        assertThat(liveSub).doesNotContainKey("9999");
    }
}
