package com.developer.service;

import com.developer.dto.FieldDefinitionRequest;
import com.developer.entity.FieldDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.platform.common.computedfield.ComputedFieldCandidate;
import com.platform.common.computedfield.ComputedFieldDesignValidator;
import com.platform.common.computedfield.ComputedFieldParentTable;
import com.platform.common.computedfield.ComputedFieldSubTable;
import com.platform.common.computedfield.ComputedFieldTypeInference.ResultKind;
import com.platform.common.computedfield.ComputedFieldValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates computed (formula) field definitions when a Function Unit table design is saved.
 *
 * <p>The rules themselves live in {@link ComputedFieldDesignValidator} so that Relation Tables,
 * which are designed in admin-center against a different column model, cannot drift onto a
 * different set of rules. This class only adapts Developer Workstation's types into the shared
 * shape and translates the outcome into a {@link DeveloperBusinessException}.
 */
@Slf4j
@Service
public class ComputedFieldValidator {

    /**
     * Validates every computed field in an incoming table design.
     *
     * <p>Called from the same place as FK validation, so a table save either satisfies both sets of
     * rules or is rejected as a whole.
     *
     * @param table          the table being saved; may have a null id when it is being created
     * @param fields         the full incoming field list, which is the authority on what the table
     *                       will contain after the save
     * @param allTablesInFu  every table of the Function Unit, used to resolve sub-table aggregates
     * @throws DeveloperBusinessException when any computed field is invalid
     */
    public void validateIncomingFields(TableDefinition table,
                                       List<FieldDefinitionRequest> fields,
                                       List<TableDefinition> allTablesInFu) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        List<ComputedFieldCandidate> candidates = new ArrayList<>(fields.size());
        for (FieldDefinitionRequest field : fields) {
            candidates.add(toCandidate(field));
        }
        try {
            ComputedFieldDesignValidator.validate(
                    candidates, toSubTables(table, allTablesInFu), toParent(table, allTablesInFu));
        } catch (ComputedFieldValidationException e) {
            throw new DeveloperBusinessException(e.getCode(), e.getMessage());
        }
    }

    private ComputedFieldCandidate toCandidate(FieldDefinitionRequest field) {
        if (field == null) {
            return null;
        }
        DataType dataType = field.getDataType();
        return new ComputedFieldCandidate(
                field.getFieldName(),
                Boolean.TRUE.equals(field.getIsComputed()),
                field.getComputedField(),
                Boolean.TRUE.equals(field.getIsPrimaryKey()),
                Boolean.TRUE.equals(field.getIsForeignKey()),
                Boolean.TRUE.equals(field.getIsUnique()),
                field.getDefaultValue(),
                field.getPkGeneration() != null && !field.getPkGeneration().isEmpty(),
                dataType == null ? null : dataType.name(),
                toResultKind(dataType));
    }

    /**
     * Sub-tables of the Function Unit, excluding the table being saved: a table cannot aggregate
     * over itself.
     */
    private List<ComputedFieldSubTable> toSubTables(TableDefinition table,
                                                    List<TableDefinition> allTablesInFu) {
        List<ComputedFieldSubTable> result = new ArrayList<>();
        if (allTablesInFu == null) {
            return result;
        }
        for (TableDefinition candidate : allTablesInFu) {
            if (candidate == null || candidate.getTableName() == null) {
                continue;
            }
            boolean isSelf = table != null && table.getId() != null
                    && Objects.equals(table.getId(), candidate.getId());
            if (isSelf) {
                continue;
            }
            List<String> columns = candidate.getFieldDefinitions() == null
                    ? List.of()
                    : candidate.getFieldDefinitions().stream()
                            .map(FieldDefinition::getFieldName)
                            .filter(Objects::nonNull)
                            .toList();
            result.add(ComputedFieldSubTable.of(candidate.getTableName(), columns));
        }
        return result;
    }

    /**
     * The Function Unit MAIN table, only when the table being saved is a SUB table. MAIN and
     * Relation-like saves pass null so a qualified {@code table.column} is rejected.
     */
    private ComputedFieldParentTable toParent(TableDefinition table,
                                              List<TableDefinition> allTablesInFu) {
        if (table == null || table.getTableType() != TableType.SUB || allTablesInFu == null) {
            return null;
        }
        for (TableDefinition candidate : allTablesInFu) {
            if (candidate == null || candidate.getTableType() != TableType.MAIN
                    || candidate.getTableName() == null) {
                continue;
            }
            List<ComputedFieldCandidate> columns = new ArrayList<>();
            if (candidate.getFieldDefinitions() != null) {
                for (FieldDefinition field : candidate.getFieldDefinitions()) {
                    columns.add(toCandidateFromEntity(field));
                }
            }
            return ComputedFieldParentTable.of(candidate.getTableName(), columns);
        }
        return null;
    }

    private ComputedFieldCandidate toCandidateFromEntity(FieldDefinition field) {
        if (field == null) {
            return null;
        }
        DataType dataType = field.getDataType();
        return new ComputedFieldCandidate(
                field.getFieldName(),
                Boolean.TRUE.equals(field.getIsComputed()),
                field.getComputedFieldJson(),
                Boolean.TRUE.equals(field.getIsPrimaryKey()),
                Boolean.TRUE.equals(field.getIsForeignKey()),
                Boolean.TRUE.equals(field.getIsUnique()),
                field.getDefaultValue(),
                field.getPkGenerationJson() != null && !field.getPkGenerationJson().isEmpty(),
                dataType == null ? null : dataType.name(),
                toResultKind(dataType));
    }

    private ResultKind toResultKind(DataType dataType) {
        if (dataType == null) {
            return null;
        }
        return switch (dataType) {
            case INTEGER, BIGINT, DECIMAL -> ResultKind.NUMBER;
            case VARCHAR, TEXT -> ResultKind.TEXT;
            case BOOLEAN -> ResultKind.BOOLEAN;
            // DATE/TIME/TIMESTAMP/JSON/BYTEA/FILE have no formula semantics yet; treating them as
            // UNKNOWN keeps inference conservative instead of silently coercing them to text.
            default -> ResultKind.UNKNOWN;
        };
    }
}
