package com.admin.component;

import com.admin.repository.RelationTableDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.relationtable.SystemUserRelationTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemUserRelationImportTest {
    @Test void reusesVirtualIdentityWithoutCreatingAnRtTable() {
        var tables = mock(RelationTableDefinitionRepository.class);
        var fields = mock(RelationTableFieldMapper.class);
        var importer = new RelationTableStructureImporter(tables, fields, new ObjectMapper());
        assertEquals(Map.of(SystemUserRelationTable.ID, SystemUserRelationTable.ID),
                importer.importRelationTables(List.of(SystemUserRelationTable.snapshot()), "tester"));
        verifyNoInteractions(tables, fields);
    }

    @Test void rejectsReservedNameWithOrdinaryIdentity() {
        var importer = new RelationTableStructureImporter(mock(RelationTableDefinitionRepository.class),
                mock(RelationTableFieldMapper.class), new ObjectMapper());
        assertThrows(com.platform.common.functionunit.FunctionUnitVersionException.class,
                () -> importer.importRelationTables(List.of(Map.of(
                        "relationTableId", 12, "tableName", "sys_users")), "tester"));
    }
}
