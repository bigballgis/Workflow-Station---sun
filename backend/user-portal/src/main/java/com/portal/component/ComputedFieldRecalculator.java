package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.computedfield.ComputedFieldContext;
import com.platform.common.computedfield.SubTableNormalizer;
import com.platform.common.computedfield.SubTableNormalizer.SliceIdentity;
import com.portal.component.ComputedFieldRowEvaluation.FieldMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
     * @throws com.portal.exception.PortalException when a formula fails and its {@code onError}
     *                                              mode is {@code fail}, when a stored definition
     *                                              is unusable, or when the Function Unit cannot
     *                                              be resolved
     */
    public void recalculate(String functionUnitIdOrCode, Map<String, Object> variables) {
        if (!enabled || variables == null || variables.isEmpty()) {
            return;
        }
        if (!hasAnyComputedField()) {
            return;
        }
        if (functionUnitIdOrCode == null || functionUnitIdOrCode.isBlank()) {
            return;
        }
        Long functionUnitId = portalPrimaryKeyAllocationComponent
                .resolveFunctionUnitIdForAllocation(functionUnitIdOrCode);
        FuComputedFields metadata = metadataFor(functionUnitId);
        if (metadata.isEmpty()) {
            return;
        }
        Map<String, List<Map<String, Object>>> subTables =
                recalculateSubTableRows(variables, metadata);
        ComputedFieldRowEvaluation.applyFields(metadata.mainFields(), variables,
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
                List<FieldMeta> fields =
                        metadata.subFieldsByTableId().get(toLong(identity.tableId()));
                if (fields == null || fields.isEmpty() || !(slice.getValue() instanceof List<?> rows)) {
                    continue;
                }
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> rowMap) {
                        Map<String, Object> typed = (Map<String, Object>) rowMap;
                        ComputedFieldRowEvaluation.applyFields(
                                fields, typed, ComputedFieldContext.ofRow(typed, metadata.parentRows(variables)));
                    }
                }
            }
        }
        return SubTableNormalizer.normalize(slices, metadata::identify);
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

        String mainTableName = null;
        List<FieldMeta> main = new ArrayList<>();
        Map<Long, List<FieldMeta>> subByTable = new LinkedHashMap<>();
        for (RawField field : raw) {
            if ("MAIN".equalsIgnoreCase(field.tableType()) && mainTableName == null) {
                mainTableName = field.tableName();
            }
            FieldMeta meta = ComputedFieldRowEvaluation.toMeta(field.fieldName(),
                    ComputedFieldRowEvaluation.parseJsonMap(objectMapper, field.json(), field.fieldName()));
            if ("MAIN".equalsIgnoreCase(field.tableType())) {
                main.add(meta);
            } else {
                subByTable.computeIfAbsent(field.tableId(), k -> new ArrayList<>()).add(meta);
            }
        }
        if (mainTableName == null) {
            mainTableName = loadMainTableName(functionUnitId);
        }
        main = ComputedFieldRowEvaluation.orderByDependency(main);
        subByTable.replaceAll((tableId, fields) -> ComputedFieldRowEvaluation.orderByDependency(fields));
        return new FuComputedFields(mainTableName, main, subByTable, loadSliceIdentities(functionUnitId));
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

    /**
     * Physical MAIN table name, used when this Function Unit only has computed fields on SUB
     * tables so the field-metadata query never returned a MAIN row.
     */
    private String loadMainTableName(Long functionUnitId) {
        List<String> names = jdbcTemplate.query(
                """
                SELECT table_name
                FROM dw_table_definitions
                WHERE function_unit_id = ? AND table_type = 'MAIN'
                ORDER BY id
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getString("table_name"),
                functionUnitId);
        return names.isEmpty() ? null : names.get(0);
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

    /** A row of the metadata query, before the definition JSON is understood. */
    private record RawField(Long tableId,
                            String tableName,
                            String tableType,
                            String fieldName,
                            String json) {
    }

    /** Everything needed to recompute one Function Unit's records. */
    private record FuComputedFields(String mainTableName,
                                    List<FieldMeta> mainFields,
                                    Map<Long, List<FieldMeta>> subFieldsByTableId,
                                    Map<String, SliceIdentity> sliceIdentities) {

        static final FuComputedFields EMPTY =
                new FuComputedFields(null, List.of(), Map.of(), Map.of());

        boolean isEmpty() {
            return mainFields.isEmpty() && subFieldsByTableId.isEmpty();
        }

        Map<String, Map<String, Object>> parentRows(Map<String, Object> variables) {
            if (mainTableName == null || mainTableName.isBlank() || variables == null) {
                return Map.of();
            }
            return Map.of(mainTableName.toLowerCase(Locale.ROOT), variables);
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
