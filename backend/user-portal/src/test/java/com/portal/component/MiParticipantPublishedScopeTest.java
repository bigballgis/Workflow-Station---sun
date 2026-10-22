package com.portal.component;

import com.platform.common.functionunit.CatalogSnapshotReader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MiParticipantPublishedScopeTest {
    @Test void unrelatedPublishedRowsWithNameColumnsCannotBorrowMeetingIdentity() {
        var jdbc = mock(JdbcTemplate.class);
        var snapshots = mock(CatalogSnapshotReader.class);
        var component = new MiParticipantEnrichmentComponent(jdbc);
        ReflectionTestUtils.setField(component, "catalogSnapshots", snapshots);
        when(snapshots.contents("release-v1", "DATA_TABLE")).thenReturn(List.of(
                Map.of("tableType", "SUB", "tableName", "employees")));
        var row = new HashMap<String,Object>(Map.of("employee_key", "EMP-1", "name", "Synthetic employee"));
        var variables = Map.<String,Object>of("functionUnitId", "release-v1", "__subTables__",
                Map.of("dw:employees", List.of(row)));
        component.enrichMissingParticipantRowIdsInSubTables(variables);
        component.enrichParticipantAssignmentData(variables);
        assertThat(row).containsOnlyKeys("employee_key", "name");
        verifyNoInteractions(jdbc);
    }
}
