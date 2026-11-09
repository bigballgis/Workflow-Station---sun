package com.developer.component.impl;

import com.developer.dto.TableRelationDTO;
import com.developer.entity.FunctionUnit;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableRelationRepository;
import com.developer.security.WorkspaceRouteOwnership;
import com.developer.security.WorkspaceRouteOwnership.Owned;
import com.developer.service.FieldFkPkSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Both ends of a relation row must be tables of the unit in the path (§6.1, §6.2): they are written as field FK
 * metadata and dw_table_relations rows exactly as sent.
 */
@ExtendWith(MockitoExtension.class)
class TableRelationComponentImplTest {

    private static final long UNIT = 1L;
    private static final long OWN_TABLE = 10L;
    private static final long FOREIGN_TABLE = 99L;

    @Mock private TableRelationRepository tableRelationRepository;
    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private FieldFkPkSyncService fieldFkPkSyncService;
    @Mock private WorkspaceRouteOwnership routeOwnership;

    private TableRelationComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new TableRelationComponentImpl(tableRelationRepository, functionUnitRepository,
                fieldFkPkSyncService, routeOwnership);
        when(functionUnitRepository.findById(UNIT)).thenReturn(Optional.of(FunctionUnit.builder().id(UNIT).build()));
        // lenient: the own-table end reaches the same method with other arguments
        lenient().doThrow(new ResourceNotFoundException("Table", String.valueOf(FOREIGN_TABLE)))
                .when(routeOwnership).requireInUnit(Owned.TABLE, FOREIGN_TABLE, UNIT);
    }

    @Test
    void saveAll_targetTableOfAnotherUnit_isNotFoundAndNothingIsWritten() {
        assertSaveRejected(relation(OWN_TABLE, FOREIGN_TABLE));
    }

    @Test
    void saveAll_sourceTableOfAnotherUnit_isNotFoundAndNothingIsWritten() {
        assertSaveRejected(relation(FOREIGN_TABLE, OWN_TABLE));
    }

    private void assertSaveRejected(TableRelationDTO relation) {
        assertThrows(ResourceNotFoundException.class, () -> component.saveAll(UNIT, List.of(relation)));
        verifyNoInteractions(fieldFkPkSyncService, tableRelationRepository);
    }

    private static TableRelationDTO relation(long source, long target) {
        return TableRelationDTO.builder()
                .sourceTableId(source).sourceFieldName("parent_id").relationType("ONE_TO_MANY")
                .targetTableId(target).targetFieldName("id").build();
    }
}
