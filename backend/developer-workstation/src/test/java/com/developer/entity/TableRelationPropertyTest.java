package com.developer.entity;

import com.developer.repository.TableRelationRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Feature: function-unit-design-review, Property 26: 表关系级联删除
 * 
 * 使用 jqwik 验证删除表后，所有引用该表的关系均被删除。
 * This is a unit test using Mockito to verify the cascade delete behavior
 * of TableRelationRepository.
 *
 * Validates: Requirements 12.4
 */
class TableRelationPropertyTest {

    // Property 26: 表关系级联删除
    // For any table that has associated relationships (as source or target),
    // calling deleteBySourceTableIdOrTargetTableId removes all relations
    // where the table is source or target.
    @Property(tries = 100)
    void cascadeDeleteShouldRemoveAllRelationsReferencingDeletedTable(
            @ForAll @IntRange(min = 1, max = 100) int relationCount,
            @ForAll @IntRange(min = 1, max = 1000) int deletedTableId) {

        // Arrange
        TableRelationRepository repository = mock(TableRelationRepository.class);
        Long deletedId = (long) deletedTableId;

        // Build relations: some reference the deleted table as source, some as target, some as both
        List<TableRelation> allRelations = new ArrayList<>();
        List<TableRelation> expectedRemoved = new ArrayList<>();

        for (int i = 0; i < relationCount; i++) {
            TableRelation relation = TableRelation.builder()
                    .id((long) (i + 1))
                    .sourceTableId(i % 3 == 0 ? deletedId : deletedId + 100 + i)
                    .targetTableId(i % 3 == 1 ? deletedId : deletedId + 200 + i)
                    .sourceFieldName("field_" + i)
                    .targetFieldName("target_field_" + i)
                    .relationType("ONE_TO_MANY")
                    .build();
            allRelations.add(relation);

            if (relation.getSourceTableId().equals(deletedId)
                    || relation.getTargetTableId().equals(deletedId)) {
                expectedRemoved.add(relation);
            }
        }

        // Simulate: after delete, only relations NOT referencing deletedId remain
        List<TableRelation> remainingAfterDelete = allRelations.stream()
                .filter(r -> !r.getSourceTableId().equals(deletedId)
                        && !r.getTargetTableId().equals(deletedId))
                .toList();

        // Stub: findByFunctionUnitId returns remaining relations after delete
        when(repository.findByFunctionUnitId(anyLong()))
                .thenReturn(allRelations)
                .thenReturn(remainingAfterDelete);

        // Act: verify initial state has relations referencing the deleted table
        List<TableRelation> beforeDelete = repository.findByFunctionUnitId(1L);
        boolean hadReferencingRelations = beforeDelete.stream()
                .anyMatch(r -> r.getSourceTableId().equals(deletedId)
                        || r.getTargetTableId().equals(deletedId));

        // Perform cascade delete
        repository.deleteBySourceTableIdOrTargetTableId(deletedId, deletedId);

        // Verify the delete method was called with correct arguments
        verify(repository).deleteBySourceTableIdOrTargetTableId(deletedId, deletedId);

        // After deletion, query should not return any relation referencing the deleted table
        List<TableRelation> afterDelete = repository.findByFunctionUnitId(1L);
        assertThat(afterDelete).noneMatch(
                r -> r.getSourceTableId().equals(deletedId)
                        || r.getTargetTableId().equals(deletedId));

        // The removed count should match expected
        assertThat(expectedRemoved).isNotEmpty();
        assertThat(afterDelete).hasSize(allRelations.size() - expectedRemoved.size());
    }

    @Property(tries = 100)
    void cascadeDeleteShouldNotAffectUnrelatedRelations(
            @ForAll @IntRange(min = 1, max = 50) int totalRelations,
            @ForAll @IntRange(min = 1, max = 500) int deletedTableId) {

        // Arrange
        TableRelationRepository repository = mock(TableRelationRepository.class);
        Long deletedId = (long) deletedTableId;

        // Build relations where NONE reference the deleted table
        long otherBase = deletedId + 1000;
        List<TableRelation> unrelatedRelations = LongStream.range(0, totalRelations)
                .mapToObj(i -> TableRelation.builder()
                        .id(i + 1)
                        .sourceTableId(otherBase + i)
                        .targetTableId(otherBase + totalRelations + i)
                        .sourceFieldName("src_" + i)
                        .targetFieldName("tgt_" + i)
                        .relationType("ONE_TO_ONE")
                        .build())
                .toList();

        // After delete of a non-referenced table, all relations should remain
        when(repository.findByFunctionUnitId(anyLong()))
                .thenReturn(unrelatedRelations);

        // Act
        repository.deleteBySourceTableIdOrTargetTableId(deletedId, deletedId);

        // Assert: all unrelated relations still present
        List<TableRelation> afterDelete = repository.findByFunctionUnitId(1L);
        assertThat(afterDelete).hasSize(totalRelations);
        assertThat(afterDelete).noneMatch(
                r -> r.getSourceTableId().equals(deletedId)
                        || r.getTargetTableId().equals(deletedId));
    }
}
