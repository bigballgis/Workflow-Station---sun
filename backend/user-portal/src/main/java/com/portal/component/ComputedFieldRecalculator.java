package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.computedfield.ComputedFieldAstValidator;
import com.platform.common.computedfield.ComputedFieldContext;
import com.platform.common.computedfield.ComputedFieldEvaluator;
import com.platform.common.computedfield.ComputedValue;
import com.platform.common.computedfield.EvalOutcome;
import com.platform.common.computedfield.SubTableNormalizer;
import com.platform.common.computedfield.SubTableNormalizer.SliceIdentity;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-side authority for computed (formula) field values in process variables.
 *
 * <p>The browser computes the same formulas for live preview, but whatever it submits is discarded
 * and recomputed here before the record is persisted or sent to Flowable. Without this, editing the
 * request body would move a sub-table total under an approval threshold and change which BPMN
 * gateway branch a request takes.
 *
 * <p><b>Cost when nothing uses the feature.</b> This runs on every write path, so a deployment that
 * has never designed a computed field must pay essentially nothing: {@link #hasAnyComputedField()}
 * short-circuits on a cached, partial-index-backed existence probe before any per-record work,
 * metadata lookup or map traversal happens. {@code portal.computed-fields.enabled} turns the whole
 * thing off without a redeploy.
 *
 * <p><b>Evaluation order matters.</b> Sub-table row formulas are computed first, because a main
 * table aggregate such as {@code SUM(request_items.amount)} may be summing a column that is itself
 * a formula ({@code quantity * unit_price}). Within one table, fields are evaluated in dependency
 * order so a formula can read another formula's fresh result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComputedFieldRecalculator {

    /** Slice map key holding sub-table rows, both at the top level and nested on a row. */
    private static final String SUB_TABLES_KEY = "__subTables__";

    /**
     * How long the "does any computed field exist anywhere" answer is trusted. Short, because it is
     * what makes a newly designed computed field start taking effect without a restart.
     */
    private static final long EXISTENCE_TTL_MS = 30_000L;

    /** Field metadata TTL, matching the 5 minutes used by the other portal metadata caches. */
    private static final long METADATA_TTL_MS = 5 * 60 * 1000L;

    private static final int MAX_CACHE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PortalPrimaryKeyAllocationComponent portalPrimaryKeyAllocationComponent;

    @Value("${portal.computed-fields.enabled:true}")
    private boolean enabled;

    private final Map<Long, CachedMetadata> metadataCache = Collections.synchronizedMap(
            new LinkedHashMap<Long, CachedMetadata>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CachedMetadata> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    private volatile boolean anyComputedField;
    private volatile long anyComputedFieldCheckedAt;

    /**
     * Recomputes every computed field of a Function Unit into the given variable map, in place.
     *
     * <p>The map must be the record's full variable set (existing values plus this submission), not
     * just the submitted delta: a formula may read a field the current form never showed.
     *
     * @param functionUnitIdOrCode function unit id or code, as carried on the process instance
     * @param variables            process variables to recompute into; mutated in place
     * @throws PortalException when a formula fails and its {@code onError} mode is {@code fail}
     */
    public void recalculate(String functionUnitIdOrCode, Map<String, Object> variables) {
        if (!enabled || variables == null || variables.isEmpty()) {
            return;
        }
        if (!hasAnyComputedField()) {
            return;
        }
        Long functionUnitId = resolveFunctionUnitIdOrNull(functionUnitIdOrCode);
        if (functionUnitId == null) {
            log.debug("Skip computed field recalculation: function unit not resolved for {}",
                    functionUnitIdOrCode);
            return;
        }
        FuComputedFields metadata = metadataFor(functionUnitId);
        if (metadata.isEmpty()) {
            return;
        }
        Map<String, List<Map<String, Object>>> subTables =
                recalculateSubTableRows(variables, metadata);
        applyFields(metadata.mainFields(), variables,
                new ComputedFieldContext(variables, subTables));
    }

    /**
     * Whether this deployment has any computed field at all.
     *
     * <p>This is the guard that keeps the feature free for every Function Unit that does not use
     * it. Backed by {@code idx_dw_field_definitions_computed}, so the probe touches no rows until
     * the first computed field is designed.
     *
     * @return true when at least one field definition is marked computed
     */
    public boolean hasAnyComputedField() {
        long now = System.currentTimeMillis();
        if (now - anyComputedFieldCheckedAt < EXISTENCE_TTL_MS) {
            return anyComputedField;
        }
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM dw_field_definitions WHERE is_computed = true)",
                Boolean.class);
        anyComputedField = Boolean.TRUE.equals(exists);
        anyComputedFieldCheckedAt = now;
        return anyComputedField;
    }

    /**
     * Recomputes row-scope formulas on sub-table rows and returns the de-duplicated slice view that
     * main table aggregates read.
     *
     * <p>Every alias of a slice is updated, not just the canonical one. Unlike primary key
     * allocation — where visiting an alias would mint a second, different key for the same logical
     * row — a formula is a pure function of the row, so recomputing it on each copy yields the same
     * value and leaves no stale duplicate behind in the stored variables.
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> recalculateSubTableRows(
            Map<String, Object> variables, FuComputedFields metadata) {
        Object raw = variables.get(SUB_TABLES_KEY);
        if (!(raw instanceof Map<?, ?> rawSlices)) {
            return Map.of();
        }
        Map<String, Object> slices = (Map<String, Object>) rawSlices;
        if (!metadata.subFieldsByTableId().isEmpty()) {
            for (Map.Entry<String, Object> slice : slices.entrySet()) {
                SliceIdentity identity = metadata.identify(slice.getKey());
                if (identity == null || identity.tableId() == null) {
                    continue;
                }
                List<ComputedFieldMeta> fields =
                        metadata.subFieldsByTableId().get(toLong(identity.tableId()));
                if (fields == null || fields.isEmpty() || !(slice.getValue() instanceof List<?> rows)) {
                    continue;
                }
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> rowMap) {
                        Map<String, Object> typed = (Map<String, Object>) rowMap;
                        applyFields(fields, typed, ComputedFieldContext.ofRow(typed));
                    }
                }
            }
        }
        return SubTableNormalizer.normalize(slices, metadata::identify);
    }

    /** Evaluates fields in dependency order and writes each result into the target map. */
    private void applyFields(List<ComputedFieldMeta> fields,
                             Map<String, Object> target,
                             ComputedFieldContext context) {
        for (ComputedFieldMeta field : fields) {
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
     * Converts an evaluated value into what goes into the variable map.
     *
     * <p>Numbers stay {@link BigDecimal} rather than being rounded to the column's scale: the
     * browser preview would have to round identically or the two ends would visibly disagree, and
     * that shared rounding contract does not exist yet. Storing the exact result also keeps a BPMN
     * gateway comparing a real number rather than a string.
     */
    private Object toStoredValue(ComputedValue value) {
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

    private Long resolveFunctionUnitIdOrNull(String functionUnitIdOrCode) {
        if (functionUnitIdOrCode == null || functionUnitIdOrCode.isBlank()) {
            return null;
        }
        try {
            return portalPrimaryKeyAllocationComponent
                    .resolveFunctionUnitIdForAllocation(functionUnitIdOrCode);
        } catch (PortalException e) {
            return null;
        }
    }

    private FuComputedFields metadataFor(Long functionUnitId) {
        CachedMetadata cached = metadataCache.get(functionUnitId);
        if (cached != null && !cached.isExpired()) {
            return cached.metadata();
        }
        FuComputedFields loaded = loadMetadata(functionUnitId);
        metadataCache.put(functionUnitId, new CachedMetadata(loaded, System.currentTimeMillis()));
        return loaded;
    }

    private FuComputedFields loadMetadata(Long functionUnitId) {
        List<RawField> raw = jdbcTemplate.query(
                """
                SELECT td.id AS table_id, td.table_name, td.table_type,
                       fd.field_name, fd.computed_field_json::text AS json
                FROM dw_field_definitions fd
                INNER JOIN dw_table_definitions td ON td.id = fd.table_id
                WHERE td.function_unit_id = ? AND fd.is_computed = true
                ORDER BY fd.sort_order, fd.id
                """,
                (rs, rowNum) -> new RawField(
                        rs.getLong("table_id"),
                        rs.getString("table_name"),
                        rs.getString("table_type"),
                        rs.getString("field_name"),
                        rs.getString("json")),
                functionUnitId);
        if (raw.isEmpty()) {
            return FuComputedFields.EMPTY;
        }

        List<ComputedFieldMeta> main = new ArrayList<>();
        Map<Long, List<ComputedFieldMeta>> subByTable = new LinkedHashMap<>();
        for (RawField field : raw) {
            ComputedFieldMeta meta = toMeta(field);
            if (meta == null) {
                continue;
            }
            if ("MAIN".equalsIgnoreCase(field.tableType())) {
                main.add(meta);
            } else {
                subByTable.computeIfAbsent(field.tableId(), k -> new ArrayList<>()).add(meta);
            }
        }
        main = orderByDependency(main);
        subByTable.replaceAll((tableId, fields) -> orderByDependency(fields));
        return new FuComputedFields(main, subByTable, loadSliceIdentities(functionUnitId));
    }

    private ComputedFieldMeta toMeta(RawField field) {
        Map<String, Object> definition = parseJsonMap(field.json());
        if (definition.isEmpty()) {
            log.warn("Field '{}' is marked computed but has no definition; skipping it",
                    field.fieldName());
            return null;
        }
        Object rawAst = definition.get("ast");
        if (!(rawAst instanceof Map<?, ?> astMap) || astMap.isEmpty()) {
            log.warn("Computed field '{}' has no AST; skipping it", field.fieldName());
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ast = (Map<String, Object>) astMap;
        // Dependencies are re-derived from the tree rather than read from the stored dependsOn,
        // so an edited row cannot make a field claim it depends on nothing and be evaluated first.
        Set<String> dependencies = ComputedFieldAstValidator.validate(ast).fieldDependencies();
        boolean failOnError = !"null".equals(String.valueOf(definition.get("onError")));
        return new ComputedFieldMeta(field.fieldName(), ast, dependencies, failOnError);
    }

    /**
     * Orders fields so a formula runs after the formulas it reads.
     *
     * <p>The design-time validator rejects cycles, but this reads persisted rows that could have
     * been edited around it, so an unresolvable order is reported rather than looped on.
     */
    private List<ComputedFieldMeta> orderByDependency(List<ComputedFieldMeta> fields) {
        Map<String, ComputedFieldMeta> byName = new LinkedHashMap<>();
        for (ComputedFieldMeta field : fields) {
            byName.put(field.fieldName().toLowerCase(Locale.ROOT), field);
        }
        List<ComputedFieldMeta> ordered = new ArrayList<>(fields.size());
        Set<String> placed = new HashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (ComputedFieldMeta field : fields) {
            visit(field, byName, placed, visiting, ordered);
        }
        return ordered;
    }

    private void visit(ComputedFieldMeta field,
                       Map<String, ComputedFieldMeta> byName,
                       Set<String> placed,
                       Set<String> visiting,
                       List<ComputedFieldMeta> ordered) {
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
            ComputedFieldMeta upstream = byName.get(dependency.toLowerCase(Locale.ROOT));
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
     * Maps every {@code __subTables__} key a record might use — binding id, table name, and the
     * lower-cased table name — onto the table it belongs to.
     */
    private Map<String, SliceIdentity> loadSliceIdentities(Long functionUnitId) {
        Map<String, SliceIdentity> identities = new HashMap<>();
        // Named RowCallbackHandler on purpose: an inline void lambda here is ambiguous against the
        // ResultSetExtractor overload, and javac only reports it when the tests compile.
        RowCallbackHandler collect = rs -> {
            long tableId = rs.getLong("table_id");
            String tableName = rs.getString("table_name");
            SliceIdentity identity = new SliceIdentity(tableId, tableName);
            identities.put(String.valueOf(rs.getLong("binding_id")), identity);
            if (tableName != null && !tableName.isBlank()) {
                identities.putIfAbsent(tableName, identity);
                identities.putIfAbsent(tableName.toLowerCase(Locale.ROOT), identity);
            }
        };
        jdbcTemplate.query(
                """
                SELECT ftb.id AS binding_id, ftb.table_id, td.table_name
                FROM dw_form_table_bindings ftb
                INNER JOIN dw_form_definitions fd ON fd.id = ftb.form_id
                INNER JOIN dw_table_definitions td ON td.id = ftb.table_id
                WHERE fd.function_unit_id = ? AND ftb.table_id IS NOT NULL
                """,
                collect,
                functionUnitId);
        return identities;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("Unreadable computed_field_json, treating the field as undefined", e);
            return Map.of();
        }
    }

    private static Long toLong(Object value) {
        if (value instanceof Long id) {
            return id;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    /** One computed field, ready to evaluate. */
    private record ComputedFieldMeta(String fieldName,
                                     Map<String, Object> ast,
                                     Set<String> dependencies,
                                     boolean failOnError) {
    }

    /** A row of the metadata query, before the definition JSON is understood. */
    private record RawField(Long tableId,
                            String tableName,
                            String tableType,
                            String fieldName,
                            String json) {
    }

    /** Everything needed to recompute one Function Unit's records. */
    private record FuComputedFields(List<ComputedFieldMeta> mainFields,
                                    Map<Long, List<ComputedFieldMeta>> subFieldsByTableId,
                                    Map<String, SliceIdentity> sliceIdentities) {

        static final FuComputedFields EMPTY =
                new FuComputedFields(List.of(), Map.of(), Map.of());

        boolean isEmpty() {
            return mainFields.isEmpty() && subFieldsByTableId.isEmpty();
        }

        SliceIdentity identify(String sliceKey) {
            SliceIdentity identity = sliceIdentities.get(sliceKey);
            return identity != null
                    ? identity
                    : sliceIdentities.get(sliceKey.toLowerCase(Locale.ROOT));
        }
    }

    private record CachedMetadata(FuComputedFields metadata, long cachedAt) {

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > METADATA_TTL_MS;
        }
    }
}
