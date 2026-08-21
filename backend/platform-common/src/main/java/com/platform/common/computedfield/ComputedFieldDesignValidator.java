package com.platform.common.computedfield;

import com.platform.common.computedfield.ComputedFieldTypeInference.ResultKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates computed (formula) field definitions when a table design is saved.
 *
 * <p>Structural AST checks (node kinds, operators, function whitelist, budgets) are delegated to
 * {@link ComputedFieldAstValidator}, which is also what the runtime interpreter is built on. What
 * this class adds is everything that needs the surrounding table metadata: do the referenced
 * fields exist, do the formulas form a cycle, is this column even allowed to be computed, and does
 * the formula's result fit the declared column type.
 *
 * <p>Shared by every service that lets a designer author formulas — Developer Workstation tables
 * and Relation Tables alike — so the rules cannot drift apart between them. Callers adapt their
 * own column model into {@link ComputedFieldCandidate} and translate
 * {@link ComputedFieldValidationException} into their own business exception.
 *
 * <p>Everything here rejects loudly. A computed field that saves but cannot evaluate would fail on
 * every subsequent write to the record, far away from the mistake that caused it.
 */
public final class ComputedFieldDesignValidator {

    /**
     * Per-table ceiling on computed fields. Every write to a record re-evaluates all of them, so
     * this is a latency budget as much as a sanity limit.
     */
    public static final int MAX_COMPUTED_FIELDS_PER_TABLE = 30;

    private static final String KEY_VERSION = "version";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_AST = "ast";
    private static final String KEY_DEPENDS_ON = "dependsOn";
    private static final String KEY_ON_ERROR = "onError";

    private static final Set<String> VALID_SCOPES = Set.of("row", "aggregate");
    private static final Set<String> VALID_ON_ERROR = Set.of("fail", "null");

    private ComputedFieldDesignValidator() {
    }

    /**
     * Validates every computed field in an incoming table design.
     *
     * <p>Called from the same place as foreign-key validation, so a table save either satisfies
     * both sets of rules or is rejected as a whole.
     *
     * @param fields    the full incoming column list, which is the authority on what the table
     *                  will contain after the save
     * @param subTables tables an aggregate may reach into, excluding the table being saved
     * @throws ComputedFieldValidationException when any computed field is invalid
     */
    public static void validate(List<ComputedFieldCandidate> fields,
                                Collection<ComputedFieldSubTable> subTables) {
        validate(fields, subTables, null);
    }

    /**
     * Validates every computed field, including optional MAIN-table lookups from a SUB table.
     *
     * @param fields    the full incoming column list
     * @param subTables tables an aggregate may reach into
     * @param parent    the Function Unit MAIN table when the table being saved is a SUB table;
     *                  null for MAIN tables and Relation Tables
     * @throws ComputedFieldValidationException when any computed field is invalid
     */
    public static void validate(List<ComputedFieldCandidate> fields,
                                Collection<ComputedFieldSubTable> subTables,
                                ComputedFieldParentTable parent) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        List<ComputedFieldCandidate> computed = fields.stream()
                .filter(f -> f != null && f.computed())
                .toList();
        if (computed.isEmpty()) {
            return;
        }
        if (computed.size() > MAX_COMPUTED_FIELDS_PER_TABLE) {
            throw new ComputedFieldValidationException("COMPUTED_FIELD_COUNT_EXCEEDED",
                    "A table may define at most " + MAX_COMPUTED_FIELDS_PER_TABLE
                            + " computed fields but " + computed.size() + " were submitted");
        }

        Map<String, ComputedFieldCandidate> fieldsByName = new LinkedHashMap<>();
        for (ComputedFieldCandidate field : fields) {
            if (field != null && field.fieldName() != null) {
                fieldsByName.put(normalize(field.fieldName()), field);
            }
        }
        Map<String, ComputedFieldSubTable> subTablesByName = indexSubTables(subTables);
        Map<String, Set<String>> dependencyGraph = new LinkedHashMap<>();

        for (ComputedFieldCandidate field : computed) {
            Set<String> sameTableDependencies =
                    validateOne(field, fieldsByName, subTablesByName, parent);
            dependencyGraph.put(normalize(field.fieldName()), sameTableDependencies);
        }
        detectCycles(dependencyGraph, fieldsByName);
    }

    /**
     * Validates one computed field and returns the same-row fields it depends on.
     */
    private static Set<String> validateOne(ComputedFieldCandidate field,
                                           Map<String, ComputedFieldCandidate> fieldsByName,
                                           Map<String, ComputedFieldSubTable> subTablesByName,
                                           ComputedFieldParentTable parent) {
        String fieldName = field.fieldName();
        Map<String, Object> definition = field.definition();
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
        validateParentFieldDependencies(fieldName, structure, parent);
        validateTableDependencies(fieldName, structure, subTablesByName);
        validateResultType(field, ast, fieldsByName, parent);
        return sameTableDependencies;
    }

    /**
     * A computed column is derived, so it cannot also be an identity, a constraint target or a
     * user-supplied value. Allowing any of these would create two competing sources of truth for
     * the same cell.
     */
    private static void validateColumnEligibility(ComputedFieldCandidate field) {
        String fieldName = field.fieldName();
        if (field.primaryKey()) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_BE_PK",
                    "cannot be a primary key: its value is recomputed whenever the record changes");
        }
        if (field.foreignKey()) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_BE_FK",
                    "cannot be a foreign key: FK values are filled from the referenced record");
        }
        if (field.unique()) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_BE_UNIQUE",
                    "cannot carry a unique constraint: a formula may legitimately produce the same "
                            + "value for many records");
        }
        if (field.defaultValue() != null && !field.defaultValue().isBlank()) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_HAVE_DEFAULT",
                    "cannot have a default value: the formula always determines the value");
        }
        if (field.pkGenerationPresent()) {
            throw reject(fieldName, "COMPUTED_FIELD_CANNOT_GENERATE_PK",
                    "cannot use a primary key generation strategy");
        }
    }

    /** Checks the metadata envelope around the AST. */
    private static void validateEnvelope(String fieldName, Map<String, Object> definition) {
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

    private static void validateScope(String fieldName,
                                      Map<String, Object> definition,
                                      boolean hasAggregate) {
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
    private static void validateDeclaredDependencies(String fieldName,
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
    private static Set<String> validateFieldDependencies(
            ComputedFieldCandidate field,
            ComputedFieldAstValidator.Result structure,
            Map<String, ComputedFieldCandidate> fieldsByName) {
        String fieldName = field.fieldName();
        String self = normalize(fieldName);
        Set<String> dependencies = new LinkedHashSet<>();
        for (String dependency : structure.fieldDependencies()) {
            if (dependency.indexOf('.') >= 0) {
                continue;
            }
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

    /**
     * Qualified field refs ({@code table.column}) may only name the Function Unit MAIN table,
     * and only from a SUB-table formula. The target column must exist and must not itself be
     * computed (MVP: avoids a cycle with a MAIN aggregate that sums this sub-table).
     */
    private static void validateParentFieldDependencies(
            String fieldName,
            ComputedFieldAstValidator.Result structure,
            ComputedFieldParentTable parent) {
        for (String dependency : structure.fieldDependencies()) {
            int separator = dependency.indexOf('.');
            if (separator < 0) {
                continue;
            }
            String tableName = dependency.substring(0, separator);
            String columnName = dependency.substring(separator + 1);
            if (parent == null || parent.tableName() == null) {
                throw reject(fieldName, "COMPUTED_FIELD_PARENT_REF_NOT_ALLOWED",
                        "references '" + dependency
                                + "', but only a sub-table formula may read the main table as table.column");
            }
            if (!normalize(tableName).equals(normalize(parent.tableName()))) {
                throw reject(fieldName, "COMPUTED_FIELD_UNKNOWN_PARENT_TABLE",
                        "references '" + tableName
                                + "', which is not the main table of this Function Unit");
            }
            ComputedFieldCandidate column = parent.column(columnName);
            if (column == null) {
                throw reject(fieldName, "COMPUTED_FIELD_UNKNOWN_PARENT_COLUMN",
                        "references '" + dependency + "', but '" + parent.tableName()
                                + "' has no column '" + columnName + "'");
            }
            if (column.computed()) {
                throw reject(fieldName, "COMPUTED_FIELD_PARENT_COMPUTED_DEPENDENCY",
                        "references computed column '" + dependency
                                + "'; a sub-table formula may only read plain main-table columns");
            }
        }
    }

    /** Aggregates must name a reachable sub-table and a column it really has. */
    private static void validateTableDependencies(
            String fieldName,
            ComputedFieldAstValidator.Result structure,
            Map<String, ComputedFieldSubTable> subTablesByName) {
        for (String reference : structure.tableDependencies()) {
            int separator = reference.indexOf('.');
            String tableName = separator < 0 ? reference : reference.substring(0, separator);
            String columnName = separator < 0 ? null : reference.substring(separator + 1);
            ComputedFieldSubTable subTable = subTablesByName.get(normalize(tableName));
            if (subTable == null) {
                throw reject(fieldName, "COMPUTED_FIELD_UNKNOWN_SUB_TABLE",
                        "aggregates '" + tableName
                                + "', which is not a sub-table of this Function Unit");
            }
            if (columnName == null) {
                continue;
            }
            if (!subTable.hasColumn(columnName)) {
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
    private static void validateResultType(ComputedFieldCandidate field,
                                           Map<String, Object> ast,
                                           Map<String, ComputedFieldCandidate> fieldsByName,
                                           ComputedFieldParentTable parent) {
        ResultKind declared = field.declaredKind();
        if (declared == null) {
            return;
        }
        ResultKind inferred = ComputedFieldTypeInference.infer(ast,
                name -> kindOf(resolveTypeDependency(name, fieldsByName, parent)));
        if (inferred == ResultKind.UNKNOWN) {
            return;
        }
        if (inferred != declared) {
            throw reject(field.fieldName(), "COMPUTED_FIELD_TYPE_MISMATCH",
                    typeMismatchProblem(field, inferred));
        }
    }

    /**
     * Names the mismatch and what to change. The formula is not the problem when a date difference
     * lands in a VARCHAR column — the column type is.
     */
    private static String typeMismatchProblem(ComputedFieldCandidate field, ResultKind inferred) {
        String declared = field.declaredTypeName() == null ? "unknown" : field.declaredTypeName();
        String kind = inferred.name().toLowerCase(Locale.ROOT);
        return "produces a " + kind + " but the column is declared as " + declared + ". "
                + typeMismatchHint(field.fieldName(), inferred, field.declaredKind());
    }

    private static String typeMismatchHint(String fieldName, ResultKind inferred, ResultKind declared) {
        if (inferred == ResultKind.NUMBER && declared == ResultKind.TEXT) {
            return "Change the Data Type of '" + fieldName + "' to INTEGER or DECIMAL.";
        }
        if (inferred == ResultKind.TEXT && declared == ResultKind.NUMBER) {
            return "Change the Data Type of '" + fieldName + "' to VARCHAR, or use a numeric formula.";
        }
        if (inferred == ResultKind.BOOLEAN) {
            return "Change the Data Type of '" + fieldName + "' to BOOLEAN.";
        }
        return "Change the Data Type of '" + fieldName + "' to match the formula result.";
    }

    private static ResultKind kindOf(ComputedFieldCandidate field) {
        if (field == null || field.declaredKind() == null) {
            return ResultKind.UNKNOWN;
        }
        return field.declaredKind();
    }

    private static ComputedFieldCandidate resolveTypeDependency(
            String name,
            Map<String, ComputedFieldCandidate> fieldsByName,
            ComputedFieldParentTable parent) {
        int separator = name.indexOf('.');
        if (separator < 0) {
            return fieldsByName.get(normalize(name));
        }
        if (parent == null) {
            return null;
        }
        String tableName = name.substring(0, separator);
        if (!normalize(tableName).equals(normalize(parent.tableName()))) {
            return null;
        }
        return parent.column(name.substring(separator + 1));
    }

    private static Map<String, ComputedFieldSubTable> indexSubTables(
            Collection<ComputedFieldSubTable> subTables) {
        Map<String, ComputedFieldSubTable> result = new HashMap<>();
        if (subTables == null) {
            return result;
        }
        for (ComputedFieldSubTable candidate : subTables) {
            if (candidate == null || candidate.tableName() == null) {
                continue;
            }
            result.put(normalize(candidate.tableName()), candidate);
        }
        return result;
    }

    /**
     * Rejects formulas that depend on each other in a loop, which would otherwise be an infinite
     * recalculation at write time. Only computed fields can form a cycle, since a plain column
     * never reads anything.
     */
    private static void detectCycles(Map<String, Set<String>> dependencyGraph,
                                     Map<String, ComputedFieldCandidate> fieldsByName) {
        Set<String> settled = new HashSet<>();
        Set<String> inProgress = new LinkedHashSet<>();
        for (String node : dependencyGraph.keySet()) {
            List<String> path = new ArrayList<>();
            walk(node, dependencyGraph, settled, inProgress, path, fieldsByName);
        }
    }

    private static void walk(String node,
                             Map<String, Set<String>> dependencyGraph,
                             Set<String> settled,
                             Set<String> inProgress,
                             List<String> path,
                             Map<String, ComputedFieldCandidate> fieldsByName) {
        if (settled.contains(node)) {
            return;
        }
        if (!inProgress.add(node)) {
            List<String> cycle = new ArrayList<>(path);
            cycle.add(node);
            throw new ComputedFieldValidationException("COMPUTED_FIELD_CIRCULAR_DEPENDENCY",
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

    private static String displayName(String key,
                                      Map<String, ComputedFieldCandidate> fieldsByName) {
        ComputedFieldCandidate field = fieldsByName.get(key);
        return field != null && field.fieldName() != null ? field.fieldName() : key;
    }

    private static ComputedFieldValidationException reject(String fieldName,
                                                           String code,
                                                           String problem) {
        return new ComputedFieldValidationException(code,
                "Computed field '" + fieldName + "' " + problem);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
