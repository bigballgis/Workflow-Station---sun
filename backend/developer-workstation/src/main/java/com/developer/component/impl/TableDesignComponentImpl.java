package com.developer.component.impl;

import com.developer.component.TableDesignComponent;
import com.developer.dto.FieldDefinitionRequest;
import com.developer.dto.ForeignKeyDTO;
import com.developer.dto.TableDefinitionRequest;
import com.developer.dto.ValidationResult;
import com.developer.entity.FieldDefinition;
import com.developer.entity.ForeignKey;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.enums.DatabaseDialect;
import com.developer.enums.TableType;
import com.developer.service.MainTableViewService;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.*;
import com.developer.service.FieldFkPkSyncService;
import com.developer.service.FormConfigFieldRenamer;
import com.developer.util.DeveloperWorkstationSequenceSynchronizer;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Table Design component implementation.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TableDesignComponentImpl implements TableDesignComponent {
    
    // DoS mitigation: cap field-definition count.
    private static final int MAX_FIELD_DEFINITIONS = 200;

    private final TableDefinitionRepository tableDefinitionRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final ForeignKeyRepository foreignKeyRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final I18nService i18nService;
    private final DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;
    private final FieldFkPkSyncService fieldFkPkSyncService;
    private final JdbcTemplate jdbcTemplate;
    private final MainTableViewService mainTableViewService;
    
    @Override
    @Transactional
    public TableDefinition create(Long functionUnitId, TableDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        if (isTableNameTaken(request.getTableName(), null)) {
            throw new DeveloperBusinessException("CONFLICT_TABLE_NAME_EXISTS", 
                    i18nService.getMessage("table.name_exists", request.getTableName()),
                    i18nService.getMessage("table.use_other_name"));
        }
        
        TableDefinition tableDefinition = TableDefinition.builder()
                .functionUnit(functionUnit)
                .tableName(request.getTableName())
                .tableDisplayName(request.getTableDisplayName())
                .tableType(request.getTableType())
                .displayName(request.getDescription())
                .requestIdConfig(request.getRequestIdConfig())
                .build();
        
        tableDefinition = tableDefinitionRepository.save(tableDefinition);
        
        // Persist field rows.
        if (request.getFields() != null) {
            // DoS mitigation: enforce field-definition upper bound.
            if (request.getFields().size() > MAX_FIELD_DEFINITIONS) {
                throw new DeveloperBusinessException("FIELD_COUNT_EXCEEDED",
                        i18nService.getMessage("table.field_count_exceeded",
                                request.getFields().size(), MAX_FIELD_DEFINITIONS));
            }
            List<TableDefinition> allTables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
            fieldFkPkSyncService.validateIncomingFields(tableDefinition, request.getFields(), allTables);
            sequenceSynchronizer.synchronizeFieldDefinitions();
            int sortOrder = 0;
            for (FieldDefinitionRequest fieldRequest : request.getFields()) {
                FieldDefinition field = createField(tableDefinition, fieldRequest, sortOrder++);
                tableDefinition.getFieldDefinitions().add(field);
            }
        }
        
        TableDefinition saved = tableDefinitionRepository.save(tableDefinition);
        fieldFkPkSyncService.syncForeignKeysForFunctionUnit(functionUnitId);
        if (saved.getTableType() == TableType.MAIN) {
            mainTableViewService.seedDefaultViewIfAbsent(functionUnitId, saved.getId());
        }
        return saved;
    }
    
    @Override
    @Transactional
    public TableDefinition update(Long id, TableDefinitionRequest request) {
        TableDefinition tableDefinition = getById(id);
        
        if (isTableNameTaken(request.getTableName(), id)) {
            throw new DeveloperBusinessException("CONFLICT_TABLE_NAME_EXISTS", 
                    i18nService.getMessage("table.name_exists", request.getTableName()),
                    i18nService.getMessage("table.use_other_name"));
        }

        // Before deleting legacy fields snapshot (fieldName, description, ...) keyed by id for diffing after save
        // so renames/display-name tweaks propagate into every referencing Form rule + fieldPermissions.
        Map<Long, OldFieldSnapshot> originals = new HashMap<>();
        for (FieldDefinition existing : tableDefinition.getFieldDefinitions()) {
            if (existing.getId() == null) continue;
            originals.put(existing.getId(), new OldFieldSnapshot(
                    existing.getFieldName(),
                    existing.getDisplayName(),
                    existing.getDataType() != null ? existing.getDataType().name() : null,
                    existing.getLength(),
                    existing.getScale(),
                    existing.getNullable()
            ));
        }
        Long functionUnitId = tableDefinition.getFunctionUnit().getId();

        // Update table metadata.
        tableDefinition.setTableName(request.getTableName());
        tableDefinition.setTableDisplayName(request.getTableDisplayName());
        tableDefinition.setTableType(request.getTableType());
        tableDefinition.setDisplayName(request.getDescription());
        tableDefinition.setRequestIdConfig(request.getRequestIdConfig());

        List<TableDefinition> allTables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            fieldFkPkSyncService.validateIncomingFields(tableDefinition, request.getFields(), allTables);
        }
        
        // Refresh field definitions: cascade=CascadeType.ALL + orphanRemoval=true → clear memory, delete rows explicitly
        // to avoid unique constraint races.
        tableDefinition.getFieldDefinitions().clear();
        fieldDefinitionRepository.deleteByTableDefinitionId(id);
        fieldDefinitionRepository.flush();
        
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            // DoS mitigation: enforce field-definition upper bound.
            if (request.getFields().size() > MAX_FIELD_DEFINITIONS) {
                throw new DeveloperBusinessException("FIELD_COUNT_EXCEEDED",
                        i18nService.getMessage("table.field_count_exceeded",
                                request.getFields().size(), MAX_FIELD_DEFINITIONS));
            }
            // Import/init inserts with large IDs but stale sequences ⇒ PK clash on delete+reinsert unless we realign first.
            sequenceSynchronizer.synchronizeFieldDefinitions();
            int sortOrder = 0;
            for (FieldDefinitionRequest fieldRequest : request.getFields()) {
                // Skip fields with empty name or null dataType
                if (fieldRequest.getFieldName() == null || fieldRequest.getFieldName().trim().isEmpty()) {
                    continue;
                }
                if (fieldRequest.getDataType() == null) {
                    log.warn("Skipping field {} with null dataType", fieldRequest.getFieldName());
                    continue;
                }
                
                FieldDefinition field = createField(tableDefinition, fieldRequest, sortOrder++);
                tableDefinition.getFieldDefinitions().add(field);
            }
        }
        
        TableDefinition saved = tableDefinitionRepository.save(tableDefinition);
        tableDefinitionRepository.flush();

        // Diff field deltas (fieldName/description/dataType/length/scale/nullable) and push into every related Form canvas + permissions.
        List<FormConfigFieldRenamer.FieldChange> changes = computeFieldChanges(originals, request.getFields());
        if (!changes.isEmpty()) {
            propagateFieldChangesToForms(functionUnitId, saved, changes);
        }

        fieldFkPkSyncService.syncForeignKeysForFunctionUnit(functionUnitId);

        // Reload with fields to ensure consistent state for serialization
        return tableDefinitionRepository.findByIdWithFields(saved.getId())
                .orElse(saved);
    }

    /** Within the transaction: scan every form in the FunctionUnit and propagate field deltas into rule.field/title/props/validate + fieldPermissions. */
    private void propagateFieldChangesToForms(Long functionUnitId,
                                              TableDefinition table,
                                              List<FormConfigFieldRenamer.FieldChange> changes) {
        List<FormDefinition> forms = formDefinitionRepository.findByFunctionUnitIdWithBindings(functionUnitId);
        List<FormDefinition> dirty = FormConfigFieldRenamer.apply(table, forms, changes);
        if (dirty.isEmpty()) return;
        formDefinitionRepository.saveAll(dirty);
        log.info("Propagated {} field change(s) on table {} to {} form(s)",
                changes.size(), table.getId(), dirty.size());
    }

    private List<FormConfigFieldRenamer.FieldChange> computeFieldChanges(
            Map<Long, OldFieldSnapshot> originals,
            List<FieldDefinitionRequest> incoming) {
        if (originals.isEmpty() || incoming == null || incoming.isEmpty()) {
            return Collections.emptyList();
        }
        List<FormConfigFieldRenamer.FieldChange> out = new ArrayList<>();
        for (FieldDefinitionRequest f : incoming) {
            if (f == null || f.getId() == null) continue;
            OldFieldSnapshot orig = originals.get(f.getId());
            if (orig == null) continue;
            String oldName = orig.fieldName();
            if (oldName == null || oldName.isBlank()) continue;
            FormConfigFieldRenamer.FieldChange ch = new FormConfigFieldRenamer.FieldChange(
                    oldName,
                    f.getFieldName(),
                    orig.displayName(),
                    f.getDisplayName(),
                    orig.dataType(),
                    f.getDataType() != null ? f.getDataType().name() : null,
                    orig.length(),
                    f.getLength(),
                    orig.scale(),
                    f.getScale(),
                    orig.nullable(),
                    f.getNullable()
            );
            // Skip emitting FieldChange when every tracked axis is unchanged.
            if (!ch.fieldNameChanged()
                    && !ch.displayNameChanged()
                    && !ch.lengthChanged()
                    && !ch.scaleChanged()
                    && !ch.nullableChanged()) {
                continue;
            }
            out.add(ch);
        }
        return out;
    }

    private record OldFieldSnapshot(String fieldName,
                                    String displayName,
                                    String dataType,
                                    Integer length,
                                    Integer scale,
                                    Boolean nullable) {}
    
    @Override
    @Transactional
    public void delete(Long id) {
        TableDefinition tableDefinition = getById(id);
        
        // Guard: legacy single boundTable associations.
        if (formDefinitionRepository.existsByBoundTable_Id(id)) {
            throw new DeveloperBusinessException("BIZ_TABLE_IN_USE", 
                    i18nService.getMessage("table.in_use_by_form"),
                    i18nService.getMessage("table.unbind_form_first"));
        }
        
        // Guard: multi-table bindings referencing this catalog table.
        if (formTableBindingRepository.existsByTableId(id)) {
            throw new DeveloperBusinessException("BIZ_TABLE_IN_USE", 
                    i18nService.getMessage("table.in_use_by_binding"),
                    i18nService.getMessage("table.unbind_form_first"));
        }
        
        tableDefinitionRepository.delete(tableDefinition);
    }
    
    @Override
    @Transactional(readOnly = true)
    public TableDefinition getById(Long id) {
        return tableDefinitionRepository.findByIdWithFields(id)
                .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<TableDefinition> getByFunctionUnitId(Long functionUnitId) {
        return tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public String generateDDL(Long id, DatabaseDialect dialect) {
        TableDefinition tableDefinition = getById(id);
        return generateDDLForDialect(tableDefinition, dialect);
    }
    
    @Override
    @Transactional(readOnly = true)
    public ValidationResult validateRelationships(Long functionUnitId) {
        ValidationResult result = new ValidationResult();
        
        if (hasCircularDependency(functionUnitId)) {
            result.addError("CIRCULAR_DEPENDENCY", i18nService.getMessage("table.circular_dependency"), null);
        }
        
        return result;
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean hasCircularDependency(Long functionUnitId) {
        List<ForeignKey> foreignKeys = foreignKeyRepository.findByFunctionUnitId(functionUnitId);
        
        // Build FK dependency adjacency graph.
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (ForeignKey fk : foreignKeys) {
            Long fromTableId = fk.getTableDefinition().getId();
            Long toTableId = fk.getRefTableDefinition().getId();
            graph.computeIfAbsent(fromTableId, k -> new HashSet<>()).add(toTableId);
        }
        
        // DFS cycle detection.
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();
        
        for (Long tableId : graph.keySet()) {
            if (hasCycle(tableId, graph, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasCycle(Long node, Map<Long, Set<Long>> graph, 
                            Set<Long> visited, Set<Long> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        
        visited.add(node);
        recursionStack.add(node);
        
        Set<Long> neighbors = graph.getOrDefault(node, Collections.emptySet());
        for (Long neighbor : neighbors) {
            if (hasCycle(neighbor, graph, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ForeignKeyDTO> getForeignKeys(Long functionUnitId) {
        List<ForeignKey> foreignKeys = foreignKeyRepository.findByFunctionUnitId(functionUnitId);
        return foreignKeys.stream()
                .map(ForeignKeyDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    private FieldDefinition createField(TableDefinition tableDefinition, 
                                        FieldDefinitionRequest request, int sortOrder) {
        return FieldDefinition.builder()
                .tableDefinition(tableDefinition)
                .fieldName(request.getFieldName())
                .dataType(request.getDataType())
                .length(request.getLength())
                .precision(request.getPrecision())
                .scale(request.getScale())
                .nullable(request.getNullable())
                .defaultValue(request.getDefaultValue())
                .isPrimaryKey(request.getIsPrimaryKey())
                .isUnique(request.getIsUnique())
                .displayName(request.getDisplayName())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : sortOrder)
                .isForeignKey(Boolean.TRUE.equals(request.getIsForeignKey()))
                .refTableId(request.getRefTableId())
                .refPrimaryKeyFields(request.getRefPrimaryKeyFields())
                .pkGenerationJson(request.getPkGeneration())
                .fkDisplayMode(request.getFkDisplayMode() != null ? request.getFkDisplayMode() : "readonly")
                .relationCardinality(request.getRelationCardinality())
                .build();
    }
    
    private void validateIdentifier(String name) {
        if (name == null || !name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new DeveloperBusinessException("INVALID_IDENTIFIER", "Invalid identifier: " + name);
        }
    }

    private String generateDDLForDialect(TableDefinition table, DatabaseDialect dialect) {
        StringBuilder ddl = new StringBuilder();
        String tableName = table.getTableName();
        validateIdentifier(tableName);
        
        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");
        
        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        
        for (FieldDefinition field : table.getFieldDefinitions()) {
            validateIdentifier(field.getFieldName());
            String columnDef = "    " + field.getFieldName() + " " + 
                    mapDataType(field.getDataType(), field.getLength(), field.getPrecision(), field.getScale(), dialect);
            
            if (Boolean.FALSE.equals(field.getNullable())) {
                columnDef += " NOT NULL";
            }
            
            if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) {
                columnDef += " DEFAULT " + field.getDefaultValue();
            }
            
            if (Boolean.TRUE.equals(field.getIsUnique())) {
                columnDef += " UNIQUE";
            }
            
            columnDefs.add(columnDef);
            
            if (Boolean.TRUE.equals(field.getIsPrimaryKey())) {
                primaryKeys.add(field.getFieldName());
            }
        }
        
        // Append row_version for SUB tables (optimistic locking sentinel in generated DDL previews).
        if (table.getTableType() == com.developer.enums.TableType.SUB) {
            String rowVersionType = switch (dialect) {
                case POSTGRESQL -> "BIGINT";
                case MYSQL -> "BIGINT";
                case ORACLE -> "NUMBER(19)";
                case SQLSERVER -> "BIGINT";
            };
            columnDefs.add("    row_version " + rowVersionType + " NOT NULL DEFAULT 1");
        }
        
        ddl.append(String.join(",\n", columnDefs));
        
        if (!primaryKeys.isEmpty()) {
            ddl.append(",\n    PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        }
        
        ddl.append("\n)");
        
        // Dialect-specific table suffix (MySQL ENGINE/charset).
        if (dialect == DatabaseDialect.MYSQL) {
            ddl.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
        
        ddl.append(";");
        
        return ddl.toString();
    }
    
    private String mapDataType(DataType dataType, Integer length, Integer precision, Integer scale, DatabaseDialect dialect) {
        return switch (dialect) {
            case POSTGRESQL -> mapToPostgreSQL(dataType, length, precision, scale);
            case MYSQL -> mapToMySQL(dataType, length, precision, scale);
            case ORACLE -> mapToOracle(dataType, length, precision, scale);
            case SQLSERVER -> mapToSQLServer(dataType, length, precision, scale);
        };
    }
    
    private String mapToPostgreSQL(DataType dataType, Integer length, Integer precision, Integer scale) {
        return switch (dataType) {
            case VARCHAR -> "VARCHAR(" + (length != null ? length : 255) + ")";
            case TEXT -> "TEXT";
            case INTEGER -> "INTEGER";
            case BIGINT -> "BIGINT";
            case DECIMAL -> "DECIMAL(" + (precision != null ? precision : 10) + "," + (scale != null ? scale : 2) + ")";
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIMESTAMP -> "TIMESTAMP";
            case JSON -> "JSONB";
            case BYTEA -> "BYTEA";
            case FILE -> "VARCHAR(500)";
        };
    }
    
    private String mapToMySQL(DataType dataType, Integer length, Integer precision, Integer scale) {
        return switch (dataType) {
            case VARCHAR -> "VARCHAR(" + (length != null ? length : 255) + ")";
            case TEXT -> "TEXT";
            case INTEGER -> "INT";
            case BIGINT -> "BIGINT";
            case DECIMAL -> "DECIMAL(" + (precision != null ? precision : 10) + "," + (scale != null ? scale : 2) + ")";
            case BOOLEAN -> "TINYINT(1)";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIMESTAMP -> "DATETIME";
            case JSON -> "JSON";
            case BYTEA -> "BLOB";
            case FILE -> "VARCHAR(500)";
        };
    }
    
    private String mapToOracle(DataType dataType, Integer length, Integer precision, Integer scale) {
        return switch (dataType) {
            case VARCHAR -> "VARCHAR2(" + (length != null ? length : 255) + ")";
            case TEXT -> "CLOB";
            case INTEGER -> "NUMBER(10)";
            case BIGINT -> "NUMBER(19)";
            case DECIMAL -> "NUMBER(" + (precision != null ? precision : 10) + "," + (scale != null ? scale : 2) + ")";
            case BOOLEAN -> "NUMBER(1)";
            case DATE -> "DATE";
            case TIME -> "TIMESTAMP";
            case TIMESTAMP -> "TIMESTAMP";
            case JSON -> "CLOB";
            case BYTEA -> "BLOB";
            case FILE -> "VARCHAR2(500)";
        };
    }
    
    private String mapToSQLServer(DataType dataType, Integer length, Integer precision, Integer scale) {
        return switch (dataType) {
            case VARCHAR -> "NVARCHAR(" + (length != null ? length : 255) + ")";
            case TEXT -> "NVARCHAR(MAX)";
            case INTEGER -> "INT";
            case BIGINT -> "BIGINT";
            case DECIMAL -> "DECIMAL(" + (precision != null ? precision : 10) + "," + (scale != null ? scale : 2) + ")";
            case BOOLEAN -> "BIT";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIMESTAMP -> "DATETIME2";
            case JSON -> "NVARCHAR(MAX)";
            case BYTEA -> "VARBINARY(MAX)";
            case FILE -> "NVARCHAR(500)";
        };
    }

    @Override
    public boolean isTableNameAvailable(String tableName, Long excludeTableId) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        return !isTableNameTaken(tableName, excludeTableId);
    }

    private boolean isTableNameTaken(String tableName, Long excludeTableId) {
        if (excludeTableId != null) {
            if (tableDefinitionRepository.existsByTableNameAndIdNot(tableName, excludeTableId)) {
                return true;
            }
        } else if (tableDefinitionRepository.existsByTableName(tableName)) {
            return true;
        }
        return existsInRelationTables(tableName);
    }

    private boolean existsInRelationTables(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rt_table_definitions WHERE table_name = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }
}
