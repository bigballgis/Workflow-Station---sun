package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.computedfield.ComputedFieldAstValidator;
import com.platform.common.computedfield.ComputedFieldContext;
import com.platform.common.computedfield.ComputedFieldEvaluator;
import com.platform.common.computedfield.ComputedValue;
import com.platform.common.computedfield.EvalOutcome;
import com.portal.exception.PortalException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Evaluating a set of computed fields into one row, shared by every kind of record that can carry
 * them.
 *
 * <p>Function Unit records and Relation Table rows are stored and written along completely
 * different paths, but once the metadata is loaded the work is identical: order the fields so a
 * formula runs after the formulas it reads, evaluate, and write the result back. Keeping that here
 * means the two recalculators cannot drift into different error handling or different ordering.
 */
@Slf4j
final class ComputedFieldRowEvaluation {

    private ComputedFieldRowEvaluation() {
    }

    /** One computed field, ready to evaluate. */
    record FieldMeta(String fieldName,
                     Map<String, Object> ast,
                     Set<String> dependencies,
                     boolean failOnError) {
    }

    /**
     * Turns a stored computed field definition into something evaluable.
     *
     * @param fieldName  the column the formula writes to
     * @param definition parsed {@code computed_field_json}
     * @return the field metadata
     * @throws PortalException when the definition or AST is missing
     */
    static FieldMeta toMeta(String fieldName, Map<String, Object> definition) {
        if (definition == null || definition.isEmpty()) {
            throw new PortalException("COMPUTED_FIELD_DEFINITION_INVALID",
                    "Field '" + fieldName + "' is marked computed but has no definition");
        }
        Object rawAst = definition.get("ast");
        if (!(rawAst instanceof Map<?, ?> astMap) || astMap.isEmpty()) {
            throw new PortalException("COMPUTED_FIELD_DEFINITION_INVALID",
                    "Computed field '" + fieldName + "' has no AST");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ast = (Map<String, Object>) astMap;
        // Dependencies are re-derived from the tree rather than read from the stored dependsOn,
        // so an edited row cannot make a field claim it depends on nothing and be evaluated first.
        Set<String> dependencies = ComputedFieldAstValidator.validate(ast).fieldDependencies();
        boolean failOnError = !"null".equals(String.valueOf(definition.get("onError")));
        return new FieldMeta(fieldName, ast, dependencies, failOnError);
    }

    /** Evaluates fields in order and writes each result into the target map. */
    static void applyFields(List<FieldMeta> fields,
                            Map<String, Object> target,
                            ComputedFieldContext context) {
        for (FieldMeta field : fields) {
            EvalOutcome outcome = ComputedFieldEvaluator.evaluate(field.ast(), context);
            if (outcome instanceof EvalOutcome.Success success) {
                target.put(field.fieldName(), toStoredValue(success.value()));
                continue;
            }
            EvalOutcome.Failure failure = (EvalOutcome.Failure) outcome;
            String detail = "Computed field '" + field.fieldName() + "' failed: "
                    + failure.error().code() + " " + failure.error().message();
            if (field.failOnError()) {
                throw new PortalException("COMPUTED_FIELD_EVALUATION_FAILED", detail);
            }
            // onError=null is the designer's explicit choice to store a blank instead of blocking
            // the submission. Still logged with the field name, because Power Platform's silent
            // blank on failure is precisely the diagnosability problem this feature exists to fix.
            log.warn("{}; storing blank because onError=null", detail);
            target.put(field.fieldName(), null);
        }
    }

    /**
     * Converts an evaluated value into what goes into the stored record.
     *
     * <p>Numbers stay {@link BigDecimal} rather than being rounded to the column's scale: the
     * browser preview would have to round identically or the two ends would visibly disagree, and
     * that shared rounding contract does not exist yet. Storing the exact result also keeps a BPMN
     * gateway comparing a real number rather than a string.
     */
    static Object toStoredValue(ComputedValue value) {
        if (value instanceof ComputedValue.Number number) {
            return number.value();
        }
        if (value instanceof ComputedValue.Text text) {
            return text.value();
        }
        if (value instanceof ComputedValue.Bool bool) {
            return bool.value();
        }
        return null;
    }

    /**
     * Orders fields so a formula runs after the formulas it reads.
     *
     * <p>The design-time validator rejects cycles, but this reads persisted rows that could have
     * been edited around it, so an unresolvable order is reported rather than looped on.
     *
     * @param fields fields of one table, in any order
     * @return the same fields in evaluation order
     */
    static List<FieldMeta> orderByDependency(List<FieldMeta> fields) {
        Map<String, FieldMeta> byName = new LinkedHashMap<>();
        for (FieldMeta field : fields) {
            byName.put(field.fieldName().toLowerCase(Locale.ROOT), field);
        }
        List<FieldMeta> ordered = new ArrayList<>(fields.size());
        Set<String> placed = new HashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (FieldMeta field : fields) {
            visit(field, byName, placed, visiting, ordered);
        }
        return ordered;
    }

    private static void visit(FieldMeta field,
                              Map<String, FieldMeta> byName,
                              Set<String> placed,
                              Set<String> visiting,
                              List<FieldMeta> ordered) {
        String key = field.fieldName().toLowerCase(Locale.ROOT);
        if (placed.contains(key)) {
            return;
        }
        if (!visiting.add(key)) {
            throw new PortalException("COMPUTED_FIELD_CIRCULAR_DEPENDENCY",
                    "Computed fields form a dependency cycle: " + String.join(" -> ", visiting)
                            + " -> " + key);
        }
        for (String dependency : field.dependencies()) {
            FieldMeta upstream = byName.get(dependency.toLowerCase(Locale.ROOT));
            // Plain columns are leaves: they already hold their value and need no ordering.
            if (upstream != null) {
                visit(upstream, byName, placed, visiting, ordered);
            }
        }
        visiting.remove(key);
        placed.add(key);
        ordered.add(field);
    }

    /**
     * Reads a stored JSON object column.
     *
     * @param objectMapper mapper to parse with
     * @param json         raw JSON text, possibly null
     * @param fieldName    column this JSON belongs to, used in the error message
     * @return the parsed map, empty when the column is absent
     * @throws PortalException when the stored text is not a JSON object
     */
    static Map<String, Object> parseJsonMap(ObjectMapper objectMapper, String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new PortalException("COMPUTED_FIELD_DEFINITION_INVALID",
                    "Computed field '" + fieldName + "' has unreadable JSON", e);
        }
    }
}
