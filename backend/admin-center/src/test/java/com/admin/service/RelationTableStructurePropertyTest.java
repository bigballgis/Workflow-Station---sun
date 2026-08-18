package com.admin.service;

import com.admin.dto.request.CreateRelationTableRequest;
import com.admin.dto.request.UpdateRelationTableRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.exception.RelationTableNameDuplicateException;
import com.admin.repository.RelationFieldDefinitionRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.service.impl.RelationTableStructureServiceImpl;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RelationTableStructureService 属性测试
 * Feature: relation-tables, Property 2: 表名唯一性约束
 * Feature: relation-tables, Property 3: 表定义更新持久化
 *
 * Validates: Requirements 3.6, 4.2, 4.4
 */
class RelationTableStructurePropertyTest {

    private RelationTableDefinitionRepository tableDefinitionRepository;
    private RelationFieldDefinitionRepository fieldDefinitionRepository;
    private JdbcTemplate jdbcTemplate;
    private RelationTableStructureServiceImpl service;

    @BeforeTry
    void setUp() {
        tableDefinitionRepository = Mockito.mock(RelationTableDefinitionRepository.class);
        fieldDefinitionRepository = Mockito.mock(RelationFieldDefinitionRepository.class);
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        service = new RelationTableStructureServiceImpl(
                tableDefinitionRepository,
                fieldDefinitionRepository,
                new com.admin.service.RelationComputedFieldValidator(),
                jdbcTemplate
        );
    }

    @Provide
    Arbitrary<String> validTableNames() {
        // Generate table names matching the pattern: ^[a-z][a-z0-9_]*$
        Arbitrary<Character> firstChar = Arbitraries.chars().range('a', 'z');
        Arbitrary<String> rest = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz0123456789_".toCharArray())
                .ofMinLength(0)
                .ofMaxLength(30);
        return Combinators.combine(firstChar, rest)
                .as((first, tail) -> first + tail);
    }

    /**
     * Feature: relation-tables, Property 2: 表名唯一性约束
     *
     * For any valid table name, when a table with that name already exists,
     * attempting to create a second table with the same name should be rejected
     * with RelationTableNameDuplicateException.
     *
     * Validates: Requirements 3.6
     */
    @Property(tries = 100)
    void duplicateTableNameShouldBeRejected(@ForAll("validTableNames") String tableName) {
        // Simulate that the first table was already created (table name exists)
        when(tableDefinitionRepository.existsByTableName(tableName)).thenReturn(true);

        // Build a create request with the same table name
        CreateRelationTableRequest request = CreateRelationTableRequest.builder()
                .tableName(tableName)
                .displayName("Display " + tableName)
                .description("Test description")
                .fieldDefinitions(java.util.List.of(
                        CreateRelationTableRequest.FieldDefinitionRequest.builder()
                                .fieldName("col_a")
                                .dataType(RelationDataType.VARCHAR)
                                .length(255)
                                .nullable(true)
                                .isPrimaryKey(false)
                                .sortOrder(0)
                                .build()
                ))
                .build();

        // The second creation attempt should be rejected
        assertThatThrownBy(() -> service.createTable(request))
                .isInstanceOf(RelationTableNameDuplicateException.class);

        // Verify that save was never called (table was not persisted)
        verify(tableDefinitionRepository, never()).save(any(RelationTableDefinition.class));
    }

    // --- Providers for Property 3 ---

    @Provide
    Arbitrary<String> displayNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50)
                .map(s -> "Display " + s);
    }

    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(0)
                .ofMaxLength(100)
                .map(s -> "Desc " + s);
    }

    @Provide
    Arbitrary<String> fieldNames() {
        Arbitrary<Character> firstChar = Arbitraries.chars().range('a', 'z');
        Arbitrary<String> rest = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz0123456789_".toCharArray())
                .ofMinLength(1)
                .ofMaxLength(15);
        return Combinators.combine(firstChar, rest).as((f, r) -> f + r);
    }

    @Provide
    Arbitrary<RelationDataType> dataTypes() {
        return Arbitraries.of(RelationDataType.values());
    }

    @Provide
    Arbitrary<List<UpdateRelationTableRequest.FieldDefinitionRequest>> fieldDefinitionLists() {
        Arbitrary<UpdateRelationTableRequest.FieldDefinitionRequest> fieldArb =
                Combinators.combine(
                        fieldNames(),
                        dataTypes(),
                        Arbitraries.integers().between(1, 500),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false),
                        Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(20)
                ).as((name, type, len, nullable, pk, displayName) ->
                        UpdateRelationTableRequest.FieldDefinitionRequest.builder()
                                .fieldName(name)
                                .dataType(type)
                                .length(len)
                                .nullable(nullable)
                                .isPrimaryKey(pk)
                                .defaultValue(null)
                                .displayName(displayName)
                                .build()
                );
        return fieldArb.list().ofMinSize(1).ofMaxSize(5)
                .map(fields -> {
                    // Assign sortOrder sequentially
                    for (int i = 0; i < fields.size(); i++) {
                        fields.get(i).setSortOrder(i);
                    }
                    return fields;
                });
    }

    @Provide
    Arbitrary<UpdateRelationTableRequest> updateRequests() {
        return Combinators.combine(
                displayNames(),
                descriptions(),
                fieldDefinitionLists()
        ).as((dn, desc, fields) ->
                UpdateRelationTableRequest.builder()
                        .displayName(dn)
                        .description(desc)
                        .fieldDefinitions(fields)
                        .build()
        );
    }

    /**
     * Feature: relation-tables, Property 3: 表定义更新持久化
     *
     * For any valid update request (with random displayName, description, and field definitions),
     * after calling updateTable, the returned response should match the update request values.
     * This verifies that saving and reading back a table definition preserves all updated data.
     *
     * Validates: Requirements 4.2, 4.4
     */
    @Property(tries = 100)
    void updateTableShouldPersistAllFields(@ForAll("updateRequests") UpdateRelationTableRequest request) {
        Long tableId = 1L;

        // Build an existing entity that the repository will return
        RelationTableDefinition existing = RelationTableDefinition.builder()
                .id(tableId)
                .tableName("existing_table")
                .displayName("Old Display")
                .description("Old Description")
                .status(RelationTableStatus.DEPLOYED)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(1)
                .fieldDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        when(tableDefinitionRepository.findById(eq(tableId))).thenReturn(Optional.of(existing));
        when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RelationTableResponse response = service.updateTable(tableId, request);

        // Verify basic info matches the update request
        assertThat(response.getDisplayName()).isEqualTo(request.getDisplayName());
        assertThat(response.getDescription()).isEqualTo(request.getDescription());

        // Verify status is set to UPDATED after updating a DEPLOYED table
        assertThat(response.getStatus()).isEqualTo(RelationTableStatus.UPDATED);

        // Verify field definitions match
        assertThat(response.getFieldDefinitions()).hasSameSizeAs(request.getFieldDefinitions());
        for (int i = 0; i < request.getFieldDefinitions().size(); i++) {
            UpdateRelationTableRequest.FieldDefinitionRequest reqField = request.getFieldDefinitions().get(i);
            RelationTableResponse.FieldDefinitionResponse resField = response.getFieldDefinitions().get(i);

            assertThat(resField.getFieldName()).isEqualTo(reqField.getFieldName());
            assertThat(resField.getDataType()).isEqualTo(reqField.getDataType());
            assertThat(resField.getLength()).isEqualTo(reqField.getLength());
            assertThat(resField.getNullable()).isEqualTo(reqField.getNullable());
            assertThat(resField.getIsPrimaryKey()).isEqualTo(reqField.getIsPrimaryKey());
            assertThat(resField.getDisplayName()).isEqualTo(reqField.getDisplayName());
            assertThat(resField.getSortOrder()).isEqualTo(reqField.getSortOrder());
        }
    }
}
