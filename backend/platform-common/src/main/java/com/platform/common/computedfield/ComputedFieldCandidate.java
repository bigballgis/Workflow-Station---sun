package com.platform.common.computedfield;

import com.platform.common.computedfield.ComputedFieldTypeInference.ResultKind;

import java.util.Map;

/**
 * One column of a table being saved, reduced to what computed-field validation needs.
 *
 * <p>Developer Workstation tables and Relation Tables model columns with different classes and
 * different data-type enums. Both map into this shape so the validation rules exist once. The
 * caller is responsible for the mapping, including translating its own data type into a
 * {@link ResultKind}.
 *
 * @param fieldName           column name as the designer typed it, used in error messages
 * @param computed            whether the designer marked this column as formula-driven
 * @param definition          raw {@code computed_field_json} contents; null or empty when absent
 * @param primaryKey          whether the column is part of the primary key
 * @param foreignKey          whether the column is a foreign key
 * @param unique              whether the column carries a unique constraint
 * @param defaultValue        configured default, or null when none
 * @param pkGenerationPresent whether a primary key generation strategy is configured
 * @param declaredTypeName    data type as shown to the designer, used in error messages
 * @param declaredKind        data type reduced to the kinds formulas can produce
 */
public record ComputedFieldCandidate(
        String fieldName,
        boolean computed,
        Map<String, Object> definition,
        boolean primaryKey,
        boolean foreignKey,
        boolean unique,
        String defaultValue,
        boolean pkGenerationPresent,
        String declaredTypeName,
        ResultKind declaredKind) {

    /**
     * Builds a plain, non-computed column, which is all a dependency target needs to be.
     *
     * @param fieldName        column name
     * @param declaredTypeName data type as shown to the designer
     * @param declaredKind     data type reduced to a formula result kind
     * @return a candidate that can be depended on but is not itself validated as a formula
     */
    public static ComputedFieldCandidate plain(String fieldName,
                                               String declaredTypeName,
                                               ResultKind declaredKind) {
        return new ComputedFieldCandidate(fieldName, false, null, false, false, false,
                null, false, declaredTypeName, declaredKind);
    }
}
