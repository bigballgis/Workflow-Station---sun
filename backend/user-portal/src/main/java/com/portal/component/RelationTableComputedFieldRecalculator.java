package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.computedfield.ComputedFieldContext;
import com.portal.component.ComputedFieldRowEvaluation.FieldMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side authority for computed (formula) field values in Relation Table rows.
 *
 * <p>The Function Unit counterpart is {@link ComputedFieldRecalculator}. Relation Tables are a
 * separate write path with a much simpler shape: a row is one flat map, and a Relation Table has no
 * sub-tables, so aggregates are rejected at design time and only row-scope formulas reach here.
 * What both share — ordering, evaluation and error handling — lives in
 * {@link ComputedFieldRowEvaluation}.
 *
 * <p><b>Cost when nothing uses the feature.</b> This runs on every relation-table write, so a
 * deployment that has never designed a computed field must pay essentially nothing:
 * {@link #hasAnyComputedField()} short-circuits on a cached, partial-index-backed existence probe
 * before any metadata lookup happens. {@code portal.computed-fields.enabled} turns it off without a
 * redeploy, matching the Function Unit path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationTableComputedFieldRecalculator {

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

    @Value("${portal.computed-fields.enabled:true}")
    private boolean enabled;

    private final Map<Long, CachedFields> metadataCache = Collections.synchronizedMap(
            new LinkedHashMap<Long, CachedFields>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CachedFields> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    private volatile boolean anyComputedField;
    private volatile long anyComputedFieldCheckedAt;

    /**
     * Recomputes every computed column of a Relation Table row, in place.
     *
     * <p>The map must be the row's full contents after the caller has merged the submission into
     * whatever was already stored, not just the submitted delta: a formula may read a column this
     * request never touched.
     *
     * @param tableId relation table the row belongs to
     * @param row     row data to recompute into; mutated in place
     * @throws com.portal.exception.PortalException when a formula fails and its {@code onError}
     *                                              mode is {@code fail}
     */
    public void recalculate(Long tableId, Map<String, Object> row) {
        if (!enabled || tableId == null || row == null || row.isEmpty()) {
            return;
        }
        if (!hasAnyComputedField()) {
            return;
        }
        List<FieldMeta> fields = fieldsFor(tableId);
        if (fields.isEmpty()) {
            return;
        }
        ComputedFieldRowEvaluation.applyFields(fields, row, ComputedFieldContext.ofRow(row));
    }

    /**
     * Whether this deployment has any computed column on any Relation Table.
     *
     * <p>Backed by {@code idx_rt_field_computed}, so the probe touches no rows until the first one
     * is designed.
     *
     * @return true when at least one relation field definition is marked computed
     */
    public boolean hasAnyComputedField() {
        long now = System.currentTimeMillis();
        if (now - anyComputedFieldCheckedAt < EXISTENCE_TTL_MS) {
            return anyComputedField;
        }
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM rt_field_definitions WHERE is_computed = true)",
                Boolean.class);
        anyComputedField = Boolean.TRUE.equals(exists);
        anyComputedFieldCheckedAt = now;
        return anyComputedField;
    }

    private List<FieldMeta> fieldsFor(Long tableId) {
        CachedFields cached = metadataCache.get(tableId);
        if (cached != null && !cached.isExpired()) {
            return cached.fields();
        }
        List<FieldMeta> loaded = loadFields(tableId);
        metadataCache.put(tableId, new CachedFields(loaded, System.currentTimeMillis()));
        return loaded;
    }

    private List<FieldMeta> loadFields(Long tableId) {
        List<RawField> raw = jdbcTemplate.query(
                """
                SELECT field_name, computed_field_json::text AS json
                FROM rt_field_definitions
                WHERE table_id = ? AND is_computed = true
                ORDER BY sort_order, id
                """,
                (rs, rowNum) -> new RawField(rs.getString("field_name"), rs.getString("json")),
                tableId);
        if (raw.isEmpty()) {
            return List.of();
        }
        List<FieldMeta> fields = new ArrayList<>(raw.size());
        for (RawField field : raw) {
            fields.add(ComputedFieldRowEvaluation.toMeta(field.fieldName(),
                    ComputedFieldRowEvaluation.parseJsonMap(objectMapper, field.json(), field.fieldName())));
        }
        return ComputedFieldRowEvaluation.orderByDependency(fields);
    }

    /** A row of the metadata query, before the definition JSON is understood. */
    private record RawField(String fieldName, String json) {
    }

    private record CachedFields(List<FieldMeta> fields, long cachedAt) {

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > METADATA_TTL_MS;
        }
    }
}
