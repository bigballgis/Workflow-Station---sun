package com.developer.service;

import com.developer.dto.FieldDefinitionRequest;
import com.developer.dto.TableRelationDTO;
import com.developer.entity.FieldDefinition;
import com.developer.entity.ForeignKey;
import com.developer.entity.TableDefinition;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.ForeignKeyRepository;
import com.developer.repository.TableDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates field FK/PK metadata and syncs {@code dw_foreign_keys} from field definitions (PRD §5).
 */
@Service
@RequiredArgsConstructor
public class FieldFkPkSyncService {

    private final TableDefinitionRepository tableDefinitionRepository;
    private final ForeignKeyRepository foreignKeyRepository;

    public void validateIncomingFields(
            TableDefinition table,
            List<FieldDefinitionRequest> fields,
            List<TableDefinition> allTablesInFu) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        Map<Long, TableDefinition> tableById = allTablesInFu.stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(TableDefinition::getId, t -> t, (a, b) -> a));

        for (FieldDefinitionRequest f : fields) {
            if (f == null || !Boolean.TRUE.equals(f.getIsForeignKey())) {
                continue;
            }
            validateFkField(table, f, tableById);
        }
    }

    private void validateFkField(
            TableDefinition sourceTable,
            FieldDefinitionRequest field,
            Map<Long, TableDefinition> tableById) {
        Long refTableId = field.getRefTableId();
        if (refTableId == null) {
            throw new DeveloperBusinessException("FK_REF_TABLE_REQUIRED",
                    "Foreign key field must specify refTableId: " + field.getFieldName());
        }
        if (Objects.equals(sourceTable.getId(), refTableId)) {
            throw new DeveloperBusinessException("FK_SELF_REFERENCE",
                    "Foreign key cannot reference the same table: " + field.getFieldName());
        }
        TableDefinition refTable = tableById.get(refTableId);
        if (refTable == null) {
            throw new DeveloperBusinessException("FK_REF_TABLE_NOT_FOUND",
                    "Referenced table not found in Function Unit: " + refTableId);
        }
        List<String> refPk = field.getRefPrimaryKeyFields();
        if (refPk == null || refPk.isEmpty()) {
            throw new DeveloperBusinessException("FK_REF_PK_REQUIRED",
                    "Foreign key must reference primary key field(s): " + field.getFieldName());
        }
        Set<String> refPkSet = refTable.getFieldDefinitions().stream()
                .filter(fd -> Boolean.TRUE.equals(fd.getIsPrimaryKey()))
                .map(FieldDefinition::getFieldName)
                .collect(Collectors.toSet());
        for (String pkCol : refPk) {
            if (!refPkSet.contains(pkCol)) {
                throw new DeveloperBusinessException("FK_REF_NOT_PK",
                        "Referenced column is not a primary key: " + pkCol);
            }
        }
        if (field.getDataType() != null && field.getDataType().name().equals("VARCHAR")) {
            int minLen = estimateCompositeFkLength(refPk);
            if (field.getLength() != null && field.getLength() < minLen) {
                throw new DeveloperBusinessException("FK_LENGTH_TOO_SHORT",
                        "VARCHAR length too short for composite FK on " + field.getFieldName());
            }
        }
    }

    private int estimateCompositeFkLength(List<String> refPk) {
        if (refPk.size() <= 1) {
            return 64;
        }
        return refPk.stream().mapToInt(c -> c.length() + 16).sum();
    }

    @Transactional
    public void syncForeignKeysForFunctionUnit(Long functionUnitId) {
        List<TableDefinition> tables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
        Map<Long, FieldDefinition> fieldById = new HashMap<>();
        for (TableDefinition t : tables) {
            for (FieldDefinition f : t.getFieldDefinitions()) {
                if (f.getId() != null) {
                    fieldById.put(f.getId(), f);
                }
            }
        }

        List<ForeignKey> existing = foreignKeyRepository.findByFunctionUnitId(functionUnitId);
        if (!existing.isEmpty()) {
            foreignKeyRepository.deleteAll(existing);
            foreignKeyRepository.flush();
        }

        for (TableDefinition table : tables) {
            for (FieldDefinition field : table.getFieldDefinitions()) {
                if (!Boolean.TRUE.equals(field.getIsForeignKey())) {
                    continue;
                }
                TableDefinition refTable = tableDefinitionRepository.findById(field.getRefTableId()).orElse(null);
                if (refTable == null) {
                    continue;
                }
                FieldDefinition refField = resolveFirstRefPkField(refTable, field.getRefPrimaryKeyFields());
                if (refField == null) {
                    continue;
                }
                ForeignKey fk = ForeignKey.builder()
                        .tableDefinition(table)
                        .fieldDefinition(field)
                        .refTableDefinition(refTable)
                        .refFieldDefinition(refField)
                        .onDelete("NO ACTION")
                        .onUpdate("NO ACTION")
                        .build();
                foreignKeyRepository.save(fk);
            }
        }
    }

    private FieldDefinition resolveFirstRefPkField(TableDefinition refTable, List<String> refPkFields) {
        if (refPkFields == null || refPkFields.isEmpty()) {
            return null;
        }
        String first = refPkFields.get(0);
        return refTable.getFieldDefinitions().stream()
                .filter(f -> first.equals(f.getFieldName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Apply relations dialog rows to field FK metadata (bidirectional sync write path).
     */
    @Transactional
    public void applyRelationsToFieldMetadata(Long functionUnitId, List<TableRelationDTO> relations) {
        List<TableDefinition> tables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
        Map<Long, TableDefinition> byId = tables.stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(TableDefinition::getId, t -> t));

        // Clear FK flags on all fields first
        for (TableDefinition t : tables) {
            for (FieldDefinition f : t.getFieldDefinitions()) {
                f.setIsForeignKey(false);
                f.setRefTableId(null);
                f.setRefPrimaryKeyFields(null);
                f.setFkDisplayMode("readonly");
                f.setRelationCardinality(null);
            }
        }

        if (relations != null) {
            for (TableRelationDTO rel : relations) {
                if (rel == null || rel.getSourceTableId() == null || rel.getTargetTableId() == null) {
                    continue;
                }
                TableDefinition source = byId.get(rel.getSourceTableId());
                if (source == null || rel.getSourceFieldName() == null || rel.getTargetFieldName() == null) {
                    continue;
                }
                FieldDefinition sourceField = source.getFieldDefinitions().stream()
                        .filter(f -> rel.getSourceFieldName().equals(f.getFieldName()))
                        .findFirst()
                        .orElse(null);
                if (sourceField == null) {
                    throw new DeveloperBusinessException("FK_SOURCE_FIELD_MISSING",
                            "Create field before relation: " + rel.getSourceFieldName());
                }
                sourceField.setIsForeignKey(true);
                sourceField.setRefTableId(rel.getTargetTableId());
                sourceField.setRefPrimaryKeyFields(List.of(rel.getTargetFieldName()));
                sourceField.setFkDisplayMode(
                        sourceField.getFkDisplayMode() != null ? sourceField.getFkDisplayMode() : "readonly");
                if (rel.getRelationType() != null) {
                    sourceField.setRelationCardinality(mapRelationType(rel.getRelationType()));
                }
            }
        }

        tableDefinitionRepository.saveAll(tables);
        syncForeignKeysForFunctionUnit(functionUnitId);
    }

    private String mapRelationType(String relationType) {
        return switch (relationType) {
            case "ONE_TO_ONE" -> "oneToOne";
            case "MANY_TO_MANY" -> "manyToMany";
            default -> "oneToMany";
        };
    }

    /** Derive relation rows from field FK metadata for relations dialog read path. */
    public List<TableRelationDTO> deriveRelationsFromFields(Long functionUnitId) {
        List<TableDefinition> tables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
        List<TableRelationDTO> out = new ArrayList<>();
        for (TableDefinition source : tables) {
            for (FieldDefinition f : source.getFieldDefinitions()) {
                if (!Boolean.TRUE.equals(f.getIsForeignKey()) || f.getRefTableId() == null) {
                    continue;
                }
                List<String> refPk = f.getRefPrimaryKeyFields() != null ? f.getRefPrimaryKeyFields() : List.of();
                String targetField = refPk.isEmpty() ? "" : refPk.get(0);
                out.add(TableRelationDTO.builder()
                        .sourceTableId(source.getId())
                        .sourceFieldName(f.getFieldName())
                        .targetTableId(f.getRefTableId())
                        .targetFieldName(targetField)
                        .relationType(mapCardinalityToRelationType(f.getRelationCardinality()))
                        .build());
            }
        }
        return out;
    }

    private String mapCardinalityToRelationType(String cardinality) {
        if (cardinality == null) {
            return "ONE_TO_MANY";
        }
        return switch (cardinality) {
            case "oneToOne" -> "ONE_TO_ONE";
            case "manyToMany" -> "MANY_TO_MANY";
            default -> "ONE_TO_MANY";
        };
    }
}
