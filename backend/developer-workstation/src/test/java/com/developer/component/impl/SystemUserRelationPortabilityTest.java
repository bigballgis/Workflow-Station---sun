package com.developer.component.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemUserRelationPortabilityTest {
    @Test void exportsVirtualUserSchemaWithoutReadingBusinessRelationTables() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var portability = new RelationTableStructurePortability(jdbc, new ObjectMapper());
        var tables = portability.exportByIds(List.of(-1_000_000_001L));
        assertEquals(1, tables.size());
        assertEquals("sys_users", tables.get(0).get("tableName"));
        var fields = (List<?>) tables.get(0).get("fields");
        assertEquals(List.of("id", "username", "display_name", "full_name", "email", "employee_id", "status", "language"),
                fields.stream().map(f -> ((Map<?, ?>) f).get("fieldName")).toList());
        assertEquals(Boolean.TRUE, ((Map<?, ?>) fields.get(0)).get("isPrimaryKey"));
        verifyNoInteractions(jdbc);
    }

    @Test void importsPlatformReferenceWithoutCreatingOrReplacingBusinessTables() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var portability = new RelationTableStructurePortability(jdbc, new ObjectMapper());
        assertEquals(Map.of("sys_users", -1_000_000_001L), portability.importAll(
                List.of(com.platform.common.relationtable.SystemUserRelationTable.snapshot()), "tester", "fu-test"));
        verifyNoInteractions(jdbc);
    }
}
