package com.admin.service;

import com.admin.exception.AdminBusinessException;
import com.platform.common.computedfield.ComputedFieldCandidate;
import com.platform.common.computedfield.ComputedFieldDesignValidator;
import com.platform.common.computedfield.ComputedFieldTypeInference.ResultKind;
import com.platform.common.computedfield.ComputedFieldValidationException;
import com.platform.common.enums.RelationDataType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates computed (formula) field definitions when a Relation Table structure is saved.
 *
 * <p>The rules live in {@link ComputedFieldDesignValidator}, shared with Developer Workstation, so
 * a formula that is legal on a Function Unit table is legal here too. This class adapts the
 * Relation Table column model into the shared shape.
 *
 * <p>One rule is specific to Relation Tables: a Relation Table stands on its own and has no
 * sub-tables, so aggregate formulas have nothing to aggregate over. They are rejected here with a
 * message that says so, rather than reaching the shared validator and coming back as an unresolved
 * sub-table reference.
 */
@Slf4j
@Service
public class RelationComputedFieldValidator {

    /**
     * One column of an incoming Relation Table structure, as the caller sees it.
     *
     * @param fieldName    column name
     * @param dataType     declared data type; null when the request leaves it unchanged
     * @param computed     whether the designer marked this column as formula-driven
     * @param definition   raw computed field definition; null when absent
     * @param primaryKey   whether the column is part of the primary key
     * @param foreignKey   whether the column is a foreign key
     * @param defaultValue configured default, or null when none
     * @param pkGeneration configured primary key generation strategy, or null when none
     */
    public record IncomingField(String fieldName,
                                RelationDataType dataType,
                                Boolean computed,
                                Map<String, Object> definition,
                                Boolean primaryKey,
                                Boolean foreignKey,
                                String defaultValue,
                                Map<String, Object> pkGeneration) {
    }

    /**
     * Validates every computed field in an incoming Relation Table structure.
     *
     * @param fields the full incoming column list, which is the authority on what the table will
     *               contain after the save
     * @throws AdminBusinessException when any computed field is invalid
     */
    public void validateIncomingFields(List<IncomingField> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        List<ComputedFieldCandidate> candidates = new ArrayList<>(fields.size());
        for (IncomingField field : fields) {
            if (field == null) {
                candidates.add(null);
                continue;
            }
            if (Boolean.TRUE.equals(field.computed())) {
                rejectAggregateScope(field);
            }
            candidates.add(toCandidate(field));
        }
        try {
            // A Relation Table has no sub-tables, so nothing is reachable for aggregation.
            ComputedFieldDesignValidator.validate(candidates, List.of());
        } catch (ComputedFieldValidationException e) {
            throw new AdminBusinessException(e.getCode(), e.getMessage());
        }
    }

    /**
     * Aggregates need sub-table rows, which a Relation Table does not have. Rejecting here keeps
     * the designer from saving a formula that could never evaluate.
     */
    private void rejectAggregateScope(IncomingField field) {
        Map<String, Object> definition = field.definition();
        if (definition == null) {
            return;
        }
        Object scope = definition.get("scope");
        if ("aggregate".equals(String.valueOf(scope))) {
            throw new AdminBusinessException("COMPUTED_FIELD_AGGREGATE_NOT_SUPPORTED",
                    "Computed field '" + field.fieldName() + "' uses scope 'aggregate', but a "
                            + "Relation Table has no sub-tables to aggregate over; use scope 'row'");
        }
    }

    private ComputedFieldCandidate toCandidate(IncomingField field) {
        RelationDataType dataType = field.dataType();
        return new ComputedFieldCandidate(
                field.fieldName(),
                Boolean.TRUE.equals(field.computed()),
                field.definition(),
                Boolean.TRUE.equals(field.primaryKey()),
                Boolean.TRUE.equals(field.foreignKey()),
                // Relation Table columns carry no unique constraint of their own.
                false,
                field.defaultValue(),
                field.pkGeneration() != null && !field.pkGeneration().isEmpty(),
                dataType == null ? null : dataType.name(),
                toResultKind(dataType));
    }

    private ResultKind toResultKind(RelationDataType dataType) {
        if (dataType == null) {
            return null;
        }
        return switch (dataType) {
            case INTEGER, BIGINT, DECIMAL -> ResultKind.NUMBER;
            case VARCHAR, TEXT -> ResultKind.TEXT;
            case BOOLEAN -> ResultKind.BOOLEAN;
            // DATE/TIME/TIMESTAMP/JSON/BYTEA/FILE/LOOKUP have no formula semantics yet; treating
            // them as UNKNOWN keeps inference conservative instead of silently coercing to text.
            default -> ResultKind.UNKNOWN;
        };
    }
}
