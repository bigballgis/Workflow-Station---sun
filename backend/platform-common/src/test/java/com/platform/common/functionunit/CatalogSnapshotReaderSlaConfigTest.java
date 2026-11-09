package com.platform.common.functionunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The SLA mapping of a release sits on the table its start form binds PRIMARY (the criterion Admin lists by). */
class CatalogSnapshotReaderSlaConfigTest {

    private static final String CATALOG = "cat-sla";
    private static final String START_FORM = """
            {"formId":1,"formType":"PROCESS","scene":"TASK","configJson":{},
             "tableBindings":[{"bindingId":5,"bindingType":"PRIMARY","tableId":10,"tableName":"cases"}]}""";

    private static CatalogSnapshotReader readerWith(String mainTableSla) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(CATALOG), eq("FORM"))).thenReturn(List.of(START_FORM));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(CATALOG), eq("DATA_TABLE"))).thenReturn(List.of(
                "{\"tableId\":10,\"tableName\":\"cases\",\"tableType\":\"MAIN\",\"fields\":[]"
                        + (mainTableSla == null ? "" : ",\"slaConfig\":" + mainTableSla) + "}",
                "{\"tableId\":11,\"tableName\":\"other\",\"tableType\":\"MAIN\",\"fields\":[],"
                        + "\"slaConfig\":{\"startDateSource\":\"SUBMITTED_AT\",\"dueDateField\":\"wrong_table\"}}"));
        return new CatalogSnapshotReader(jdbc, new ObjectMapper());
    }

    @Test
    void theMappingOfThePrimaryBoundTableIsTheReleasesMapping() {
        assertThat(readerWith("{\"startDateSource\":\"FIELD\",\"startDateField\":\"opened_on\",\"dueDateField\":\"due_on\"}")
                .slaConfig(CATALOG)).get()
                .satisfies(config -> assertThat(config.get("dueDateField")).isEqualTo("due_on"));
    }

    @Test
    void aPrimaryTableWithoutAMappingMeansNoSla() {
        assertThat(readerWith(null).slaConfig(CATALOG)).isEmpty();
        assertThat(readerWith("null").slaConfig(CATALOG)).isEmpty();
    }

    @Test
    void aMappingWithoutADueDateFieldMakesTheReleaseInvalid() {
        assertThatThrownBy(() -> readerWith("{\"startDateSource\":\"SUBMITTED_AT\"}").slaConfig(CATALOG))
                .isInstanceOf(FunctionUnitVersionException.class);
        assertThatThrownBy(() -> readerWith("{\"startDateSource\":\"FIELD\",\"dueDateField\":\"due_on\"}")
                .slaConfig(CATALOG)).isInstanceOf(FunctionUnitVersionException.class);
    }
}
