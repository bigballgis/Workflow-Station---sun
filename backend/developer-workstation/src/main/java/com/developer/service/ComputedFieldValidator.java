package com.developer.service;

import com.developer.dto.FieldDefinitionRequest;
import com.developer.entity.FieldDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.exception.DeveloperBusinessException;
import com.platform.common.computedfield.ComputedFieldAstValidator;
import com.platform.common.computedfield.ComputedFieldTypeInference;
import com.platform.common.computedfield.ComputedFieldTypeInference.ResultKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates computed (formula) field definitions when a table design is saved.
 *
 * <p>Structural AST checks (node kinds, operators, function whitelist, budgets) are delegated to
 * {@link ComputedFieldAstValidator} in platform-common, which is also what the runtime interpreter
 * is built on. What this class adds is everything that needs the Function Unit's own metadata:
 * do the referenced fields exist, do the formulas form a cycle, is this column even allowed to be
 * computed, and does the formula's result fit the declared column type.
 *
 * <p>Everything here rejects loudly. A computed field that saves but cannot evaluate would fail on
 * every subsequent write to the record, far away from the mistake that caused it.
 */
@Slf4j
@Service
public class ComputedFieldValidator {

    /**
     * Per-table ceiling on computed fields. Every write to a record re-evaluates all of them, so
     * this is a latency budget as much as a sanity limit.
     */
    private static final int MAX_COMPUTED_FIELDS_PER_TABLE = 30;

    private static final String KEY_VERSION = "version";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_AST = "ast";
    private static final String KEY_DEPENDS_ON = "dependsOn";
    private static final String KEY_ON_ERROR = "onError";

    private static final Set<String> VALID_SCOPES = Set.of("row", "aggregate");
    private static final Set<String> VALID_ON_ERROR = Set.of("fail", "null");

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
        List<FieldDefinitionRequest> computed = fields.stream()
                .filter(f -> f != null && Boolean.TRUE.equals(f.getIsComputed()))
                .toList();
        if (computed.isEmpty()) {
            return;
        }
        if (computed.size() > MAX_COMPUTED_FIELDS_PER_TABLE) {
            throw new DeveloperBusinessException("COMPUTED_FIELD_COUNT_EXCEEDED",
                    "A table may define at most " + MAX_COMPUTED_FIELDS_PER_TABLE
                            + " computed fields but " + computed.size() + " were submitted");
        }

        Map<String, FieldDefinitionRequest> fieldsByName = new LinkedHashMap<>();
        for (FieldDefinitionRequest field : fields) {
            if (field != null && field.getFieldName() != null) {
                fieldsByName.put(normalize(field.getFieldName()), field);
            }
        }
        Map<String, TableDefinition> subTablesByName = indexSubTables(table, allTablesInFu);
        Map<String, Set<String>> dependencyGraph = new LinkedHashMap<>();

        for (FieldDefinitionRequest field : computed) {
            Set<String> sameTableDependencies =
                    validateOne(field, fieldsByName, subTablesByName);
            dependencyGraph.put(normalize(field.getFieldName()), sameTableDependencies);
        }
        detectCycles(dependencyGraph, fieldsByName);
    }

    /**
     * Validates one computed field and returns the same-row fields it depends on.
     */
    private Set<String> validateOne(FieldDefinitionRequest field,
                                    Map<String, FieldDefinitionRequest> fieldsByName,
                                    Map<String, TableDefinition> subTablesByName) {
        String fieldName = field.getFieldName();
        Map<String, Object> definition = field.getComputedField();
        if (definition == null || definition.isEmpty()) {
            throw reject(fieldName, "COMPUTED_FIELD_DEFINITION_REQUIRED",
                    "is marked as computed but carries no formula definition");
        }
        validateColumnEligibility(field);
        validateEnvelope(fieldName, definition);

        @SuppressWarnings("unchecked")
        Map<String, Object> ast = definition.get(KEY_AST) instanceof Map<?, ?> map
                ? (Map<String, Object>) map : null;
        if (ast == null || ast.isEmpty()) {
            throw reject(fieldName, "COMPUTED_FIELD_AST_REQUIRED",
                    "has no compiled expression; the designer must submit the parsed AST");
        }

        ComputedFieldAstValidator.Result structure = ComputedFieldAstValidator.validate(ast);
        if (!structure.isValid()) {
            throw reject(fieldName, "COMPUTED_FIELD_INVALID_AST",
                    "has an invalid formula: " + structure.errors().get(0));
        }
        validateScope(fieldName, definition, structure.hasAggregate());
        validateDeclaredDependencies(fieldName, definition, structure);

        Set<String> sameTableDependencies =
                validateFieldDependencies(field, structure, fieldsByName);
        validateTableDependencies(fieldName, structure, subTablesByName);
        validateResultType(field, ast, fieldsByName);
        return sameTableDependencies;
    }

    /**
     * A computed column is derived, so it cannot also be an identity, a constraint target or a
     * user-supplied value. Allowing any of these would create two competing sources of truth for
     * the same cell.
     */
    private void validateColumnEligibility(FieldDefinitionRequest field) {
        String fieldName = field.getFieldName();
        if (Boolean.TRUE.equals(field.getIsPrimaryKey())) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_BE_PK",
                    "cannot be a primary key: its value is recomputed whenever the record changes");
        }
        if (Boolean.TRUE.equals(field.getIsForeignKey())) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_BE_FK",
                    "cannot be a foreign key: FK values are filled from the referenced record");
        }
        if (Boolean.TRUE.equals(field.getIsUnique())) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_BE_UNIQUE",
                    "cannot carry a unique constraint: a formula may legitimately produce the same "
                            + "value for many records");
        }
        if (field.getDefaultValue() != null && !field.getDefaultValue().isBlank()) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_HAVE_DEFAULT",
                    "cannot have a default value: the formula always determines the value");
        }
        if (field.getPkGeneration() != null && !field.getPkGeneration().isEmpty()) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_GENERATE_PK",
                    "cannot use a primary key generation strategy");
        }
    }

    /** Checks the metadata envelope around the AST. */
    private void validateEnvelope(String fieldName, Map<String, Object> definition) {
        Object version = definition.get(KEY_VERSION);
        if (!(version instanceof Number number) || number.intValue() < 1) {
            throw reject(fieldName, "COMPUTED_FIELD_VERSION_INVALID",
                    "has no usable formula version");
        }
        Object source = definition.get(KEY_SOURCE);
        if (!(source instanceof String text) || text.isBlank()) {
            throw reject(fieldName, "COMPUTED_FIELD_SOURCE_REQUIRED",
                    "must keep the original formula text so the designer can redisplay it");
        }
        Object onError = definition.get(KEY_ON_ERROR);
        if (onError != null && !VALID_ON_ERROR.contains(String.valueOf(onError))) {
            throw reject(fieldName, "COMPUTED_FIELD_ON_ERROR_INVALID",
                    "has an unsupported onError mode '" + onError + "'; expected fail or null");
        }
    }

    private void validateScope(String fieldName, Map<String, Object> definition, boolean hasAggregate) {
        Object scope = definition.get(KEY_SCOPE);
        String value = scope == null ? "" : String.valueOf(scope);
        if (!VALID_SCOPES.contains(value)) {
            throw reject(fieldName, "COMPUTED_FIELD_SCOPE_INVALID",
                    "has an unsupported scope '" + value + "'; expected row or aggregate");
        }
        if (hasAggregate && !"aggregate".equals(value)) {
            throw reject(fieldName, "COMPUTED_FIELD_SCOPE_MISMATCH",
                    "aggregates over a sub-table, so its scope must be 'aggregate'");
        }
        if (!hasAggregate && "aggregate".equals(value)) {
            throw reject(fieldName, "COMPUTED_FIELD_SCOPE_MISMATCH",
                    "is declared with scope 'aggregate' but references no sub-table");
        }
    }

    /**
     * The client sends a dependency list; the server re-derives it from the AST and compares.
     * A mismatch means the payload was hand-edited, so it is rejected rather than reconciled.
     */
    private void validateDeclaredDependencies(String fieldName,
                                              Map<String, Object> definition,
                                              ComputedFieldAstValidator.Result structure) {
        Object declared = definition.get(KEY_DEPENDS_ON);
        if (declared == null) {
            return;
        }
        if (!(declared instanceof List<?> list)) {
            throw reject(fieldName, "COMPUTED_FIELD_DEPENDS_ON_INVALID",
                    "has a malformed dependsOn list");
        }
        Set<String> fromClient = new LinkedHashSet<>();
        for (Object entry : list) {
            fromClient.add(String.valueOf(entry));
        }
        Set<String> fromAst = new LinkedHashSet<>(structure.allDependencies());
        if (!fromClient.equals(fromAst)) {
            throw reject(fieldName, "COMPUTED_FIELD_DEPENDS_ON_MISMATCH",
                    "declares dependencies " + fromClient + " but its formula actually reads "
                            + fromAst);
        }
    }

    /** Same-row references must exist on this table and must not point at the field itself. */
    private Set<String> validateFieldDependencies(FieldDefinitionRequest field,
                                                  ComputedFieldAstValidator.Result structure,
                                                  Map<String, FieldDefinitionRequest> fieldsByName) {
        String fieldName = field.getFieldName();
        String self = normalize(fieldName);
        Set<String> dependencies = new LinkedHashSet<>();
        for (String dependency : structure.fieldDependencies()) {
            String key = normalize(dependency);
            if (key.equals(self)) {
                throw reject(fieldName, "COMPUTED_FIELD_SELF_REFERENCE",
                        "refers to itself");
            }
            if (!fieldsByName.containsKey(key)) {
                throw reject(fieldName, "COMPUTED_FIELD_UNKNOWN_DEPENDENCY",
                        "references '" + dependency + "', which is not a field of this table");
            }
            dependencies.add(key);
        }
        return dependencies;
    }

    /** Aggregates must name a sub-table of the same Function Unit and a column it really has. */
    private void validateTableDependencies(String fieldName,
                                           ComputedFieldAstValidator.Result structure,
                                           Map<String, TableDefinition> subTablesByName) {
        for (String reference : structure.tableDependencies()) {
            int separator = reference.indexOf('.');
            String tableName = separator < 0 ? reference : reference.substring(0, separator);
            String columnName = separator < 0 ? null : reference.substring(separator + 1);
            TableDefinition subTable = subTablesByName.get(normalize(tableName));
            if (subTable == null) {
                throw reject(fieldName, "COMPUTED_FIELD_UNKNOWN_SUB_TABLE",
                        "aggregates '" + tableName
                                + "', which is not a sub-table of this Function Unit");
            }
            if (columnName == null) {
                continue;
            }
            boolean columnExists = subTable.getFieldDefinitions() != null
                    && subTable.getFieldDefinitions().stream()
                    .map(FieldDefinition::getFieldName)
                    .filter(Objects::nonNull)
                    .anyMatch(name -> normalize(name).equals(normalize(columnName)));
            if (!columnExists) {
                throw reject(fieldName, "COMPUTED_FIELD_UNKNOWN_SUB_TABLE_COLUMN",
                        "aggregates '" + reference + "' but '" + tableName
                                + "' has no column '" + columnName + "'");
            }
        }
    }

    /**
     * Rejects a formula whose statically inferable result cannot be stored in the declared column.
     * Inference is conservative, so an UNKNOWN result skips the check rather than guessing.
     */
    private void validateResultType(FieldDefinitionRequest field,
                                    Map<String, Object> ast,
                                    Map<String, FieldDefinitionRequest> fieldsByName) {
        DataType declared = field.getDataType();
        if (declared == null) {
            return;
        }
        ResultKind inferred = ComputedFieldTypeInference.infer(ast,
                name -> kindOf(fieldsByName.get(normalize(name))));
        if (inferred == ResultKind.UNKNOWN) {
            return;
        }
        if (!accepts(declared, inferred)) {
            throw reject(field.getFieldName(), "COMPUTED_FIELD_TYPE_MISMATCH",
                    "produces " + inferred.name().toLowerCase(Locale.ROOT)
                            + " but the column is declared as " + declared);
        }
    }

    private ResultKind kindOf(FieldDefinitionRequest field) {
        if (field == null || field.getDataType() == null) {
            return ResultKind.UNKNOWN;
        }
        return switch (field.getDataType()) {
            case INTEGER, BIGINT, DECIMAL -> ResultKind.NUMBER;
            case VARCHAR, TEXT -> ResultKind.TEXT;
            case BOOLEAN -> ResultKind.BOOLEAN;
            // DATE/TIME/TIMESTAMP/JSON/BYTEA/FILE have no formula semantics yet; treating them as
            // UNKNOWN keeps inference conservative instead of silently coercing them to text.
            default -> ResultKind.UNKNOWN;
        };
    }

    private boolean accepts(DataType declared, ResultKind inferred) {
        return switch (inferred) {
            case NUMBER -> declared == DataType.INTEGER || declared == DataType.BIGINT
                    || declared == DataType.DECIMAL;
            case TEXT -> declared == DataType.VARCHAR || declared == DataType.TEXT;
            case BOOLEAN -> declared == DataType.BOOLEAN;
            case UNKNOWN -> true;
        };
    }

    /**
     * Sub-tables of the Function Unit, excluding the table being saved: a table cannot aggregate
     * over itself.
     */
    private Map<String, TableDefinition> indexSubTables(TableDefinition table,
                                                        List<TableDefinition> allTablesInFu) {
        Map<String, TableDefinition> result = new HashMap<>();
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
            result.put(normalize(candidate.getTableName()), candidate);
        }
        return result;
    }

    /**
     * Rejects formulas that depend on each other in a loop, which would otherwise be an infinite
     * recalculation at write time. Only computed fields can form a cycle, since a plain column
     * never reads anything.
     */
    private void detectCycles(Map<String, Set<String>> dependencyGraph,
                              Map<String, FieldDefinitionRequest> fieldsByName) {
        Set<String> settled = new HashSet<>();
        Set<String> inProgress = new LinkedHashSet<>();
        for (String node : dependencyGraph.keySet()) {
            List<String> path = new ArrayList<>();
            walk(node, dependencyGraph, settled, inProgress, path, fieldsByName);
        }
    }

    private void walk(String node,
                      Map<String, Set<String>> dependencyGraph,
                      Set<String> settled,
                      Set<String> inProgress,
                      List<String> path,
                      Map<String, FieldDefinitionRequest> fieldsByName) {
        if (settled.contains(node)) {
            return;
        }
        if (!inProgress.add(node)) {
            List<String> cycle = new ArrayList<>(path);
            cycle.add(node);
            throw new DeveloperBusinessException("COMPUTED_FIELD_CIRCULAR_DEPENDENCY",
                    "Computed fields form a dependency cycle: " + String.join(" -> ",
                            cycle.stream().map(key -> displayName(key, fieldsByName)).toList()));
        }
        path.add(node);
        for (String dependency : dependencyGraph.getOrDefault(node, Set.of())) {
            // Non-computed dependencies are leaves; only computed ones can continue a cycle.
            if (dependencyGraph.containsKey(dependency)) {
                walk(dependency, dependencyGraph, settled, inProgress, path, fieldsByName);
            }
        }
        path.remove(path.size() - 1);
        inProgress.remove(node);
        settled.add(node);
    }

    private String displayName(String key, Map<String, FieldDefinitionRequest> fieldsByName) {
        FieldDefinitionRequest field = fieldsByName.get(key);
        return field != null && field.getFieldName() != null ? field.getFieldName() : key;
    }

    private DeveloperBusinessException reject(String fieldName, String code, String problem) {
        return new DeveloperBusinessException(code,
                "Computed field '" + fieldName + "' " + problem);
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
