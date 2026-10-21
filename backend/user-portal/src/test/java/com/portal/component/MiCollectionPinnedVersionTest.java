package com.portal.component;

import com.platform.common.functionunit.CatalogSnapshotReader;
import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiCollectionPinnedVersionTest {
    @Test void deletedDesignerTableCannotChangeThePublishedCollectionIdentityOrSource() {
        var jdbc = mock(JdbcTemplate.class);
        var builder = new MiCollectionVariableBuilder(mock(WorkflowEngineClient.class), jdbc);
        var snapshots = mock(CatalogSnapshotReader.class);
        var table = Map.<String,Object>of("tableId", 401, "tableName", "released_members", "fields",
                List.of(Map.of("fieldName", "entry_code", "isPrimaryKey", true)));
        when(snapshots.table("release-v1", "401")).thenReturn(table);
        when(snapshots.tableAliases("release-v1")).thenReturn(Map.of("dw:released_members", table,
                "dw:unrelated", Map.of("tableId", 402)));
        ReflectionTestUtils.setField(builder, "catalogSnapshots", snapshots);
        var variables = new HashMap<String,Object>(Map.of("functionUnitId", "release-v1", "__subTables__", Map.of(
                "dw:released_members", List.of(Map.of("entry_code", "OWN-1", "participant", "user-dev")),
                "dw:unrelated", List.of(Map.of("entry_code", "OTHER-1", "participant", "other-user")))));
        ReflectionTestUtils.invokeMethod(builder, "buildMiCollectionVariable", variables, "members_collection",
                "participant", "released_members", "401", null, null);
        assertThat((List<?>)variables.get("members_collection")).hasSize(1);
        assertThat(variables.get("members_collection").toString()).contains("OWN-1").doesNotContain("OTHER-1");
        verifyNoInteractions(jdbc);
    }
}
