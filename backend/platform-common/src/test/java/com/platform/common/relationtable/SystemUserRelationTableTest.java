package com.platform.common.relationtable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.FunctionUnitVersionException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SystemUserRelationTableTest {
    @Test void jsonRoundTripRetainsOnlyPublishedUserSchema() throws Exception {
        var mapper = new ObjectMapper();
        var snapshot = mapper.readValue(mapper.writeValueAsBytes(SystemUserRelationTable.snapshot()),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        assertTrue(SystemUserRelationTable.matches(snapshot));
        assertFalse(snapshot.containsKey("data"));
        assertEquals(8, ((List<?>) snapshot.get("fields")).size());
    }

    @Test void reservedIdentityCannotPublishArbitraryColumns() {
        var snapshot = new LinkedHashMap<>(SystemUserRelationTable.snapshot());
        snapshot.put("fields", List.of(Map.of("fieldName", "password_hash")));
        assertThrows(FunctionUnitVersionException.class, () -> SystemUserRelationTable.matches(snapshot));
        snapshot.put("relationTableId", 7L);
        assertThrows(FunctionUnitVersionException.class, () -> SystemUserRelationTable.matches(snapshot));
        assertFalse(SystemUserRelationTable.matches(Map.of("relationTableId", 7, "tableName", "reviewers")));
    }
}
