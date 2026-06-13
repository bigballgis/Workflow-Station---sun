package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.SubTablePhysicalColumnResolver;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.component.MiOverlaySupport.MiRowProgress;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.util.SubTableNestingSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merges persisted relation-table columns and engine MI progress into {@code variables.__subTables__}
 * rows (process/task detail hydration). Extracted from {@link ProcessComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubTableEnrichmentComponent {

    private final ProcessInstanceRepository processInstanceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MiOverlayComponent miOverlayComponent;
    private final SubTablePhysicalMetadataCache subTablePhysicalMetadataCache;

    /** Lazy: breaks cycle with {@link ProcessComponent}, which keeps the FU content cache used for binding lookups. */
    @Lazy
    @Autowired
    private ProcessComponent processComponent;

    /**
     * Merge persisted relation-table columns into {@code variables.__subTables__} rows (same as process detail).
     * Task detail previously merged PI variables without this step, so MI todo rows often stayed thin until reload elsewhere.
     */
    public void enrichSubTablesVariablesFromPhysicalTables(String processInstanceId, Map<String, Object> variables) {
        if (processInstanceId == null || processInstanceId.isBlank()
                || variables == null || variables.isEmpty()) {
            return;
        }
        // Collapse any geometrically bloated __subTables__ (rows that embed full nested copies of the whole
        // sub-table tree from prior task rounds) down to the canonical one-level nesting BEFORE enriching.
        // This both fixes already-persisted bloat on read and bounds the recursive overlay cost.
        int strippedNested = SubTableNestingSanitizer.stripDeepNestedSubTables(variables);
        if (strippedNested > 0) {
            log.info("[PERF] enrich(public) stripped {} deep nested __subTables__ for {}",
                    strippedNested, processInstanceId);
        }
        // Same process instance is enriched up to 3x per detail page load (detail endpoint, /form, /form-data).
        // The recursive __subTables__ overlay is expensive on large payloads, so reuse a freshly computed result
        // across those calls when the input __subTables__ is byte-identical (fingerprint) and recent. The short
        // TTL guards MI/runtime freshness (a participant advancing must re-run within seconds).
        Object baseSub = variables.get("__subTables__");
        String enrichFingerprint = fingerprintForEnrichCache(baseSub);
        if (enrichFingerprint != null) {
            EnrichedSubTablesCacheEntry hit = enrichedSubTablesCache.get(processInstanceId);
            if (hit != null
                    && enrichFingerprint.equals(hit.baseFingerprint())
                    && System.currentTimeMillis() - hit.timestampMs() < ENRICH_RESULT_TTL_MS) {
                Object restored = readEnrichCacheValue(hit.enrichedJson());
                if (restored != null) {
                    variables.put("__subTables__", restored);
                    log.info("[PERF] enrich(public) CACHE HIT for {}", processInstanceId);
                    return;
                }
            }
        }

        ProcessInstanceInfo synthetic = new ProcessInstanceInfo();
        synthetic.setId(processInstanceId);
        synthetic.setVariables(variables);
        processInstanceRepository.findById(processInstanceId).ifPresent(pi -> {
            synthetic.setFunctionUnitCatalogId(pi.getFunctionUnitCatalogId());
            synthetic.setFunctionUnitCode(pi.getFunctionUnitCode());
            synthetic.setProcessDefinitionKey(pi.getProcessDefinitionKey());
            synthetic.setStatus(pi.getStatus());
        });
        long __t = System.nanoTime();
        enrichSubTablesWithAssignmentData(synthetic);
        log.info("[PERF] enrichSubTablesWithAssignmentData(public) took {} ms", (System.nanoTime() - __t) / 1_000_000L);

        if (enrichFingerprint != null) {
            String enrichedJson = writeEnrichCacheValue(variables.get("__subTables__"));
            if (enrichedJson != null) {
                enrichedSubTablesCache.put(processInstanceId,
                        new EnrichedSubTablesCacheEntry(enrichFingerprint, enrichedJson, System.currentTimeMillis()));
            }
        }
    }

    private static final long ENRICH_RESULT_TTL_MS = 5000L;
    private final Map<String, EnrichedSubTablesCacheEntry> enrichedSubTablesCache = new ConcurrentHashMap<>();
    private final ObjectMapper enrichCacheMapper = new ObjectMapper();

    private record EnrichedSubTablesCacheEntry(String baseFingerprint, String enrichedJson, long timestampMs) {}

    private String fingerprintForEnrichCache(Object subTables) {
        if (subTables == null) {
            return null;
        }
        try {
            String json = enrichCacheMapper.writeValueAsString(subTables);
            return json.length() + ":" + Integer.toHexString(json.hashCode());
        } catch (Exception e) {
            return null;
        }
    }

    private String writeEnrichCacheValue(Object subTables) {
        if (subTables == null) {
            return null;
        }
        try {
            return enrichCacheMapper.writeValueAsString(subTables);
        } catch (Exception e) {
            return null;
        }
    }

    private Object readEnrichCacheValue(String json) {
        if (json == null) {
            return null;
        }
        try {
            return enrichCacheMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichSubTablesWithAssignmentData(ProcessInstanceInfo info) {
        Map<String, Object> variables = info.getVariables();
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?> subMap) || subMap.isEmpty()) {
            return;
        }
        enrichSubTablesWithAssignmentData(info,
                miOverlayComponent.resolveMiRowProgress(info.getId(), info.getStatus()));
    }

    /**
     * Uses pre-resolved MI overlay (same snapshot as {@link #enrichSubTablesMapPayload}) so initiator detail avoids a
     * duplicate workflow-engine MI HTTP round-trip.
     */
    @SuppressWarnings("unchecked")
    void enrichSubTablesWithAssignmentData(
            ProcessInstanceInfo info, Map<String, Map<String, MiRowProgress>> miProgressByTable) {
        Map<String, Object> variables = info.getVariables();
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
        long __t1 = System.nanoTime();
        Map<String, String> bindingTableNames = resolveSubTableBindingTableNames(info);
        log.info("[PERF] enrich.resolveSubTableBindingTableNames took {} ms", (System.nanoTime() - __t1) / 1_000_000L);
        long __t2 = System.nanoTime();
        ENRICH_SQL_STATS.set(new long[4]);
        enrichSubTablesMapPayload(info, subTables, bindingTableNames, miProgressByTable);
        long[] __s = ENRICH_SQL_STATS.get();
        log.info("[PERF] enrich.enrichSubTablesMapPayload took {} ms | perRowSelect={} (sum {} ms), subTableExists={}, resolvePk={}",
                (System.nanoTime() - __t2) / 1_000_000L, __s[0], __s[1], __s[2], __s[3]);
        // Numeric bindingIds (64/66) and legacy keys (90/subtable2) share the same MI rows; only slices with a
        // designer binding name were overlaid above — propagate engine state to every duplicate row in __subTables__.
        long __t3 = System.nanoTime();
        miOverlayComponent.propagateMiOverlayAcrossAllSubTableSlices(subTables, miProgressByTable, info);
        log.info("[PERF] enrich.propagateMiOverlayAcrossAllSubTableSlices took {} ms", (System.nanoTime() - __t3) / 1_000_000L);
    }

    /**
     * Physical-row merge + MI overlay for one {@code __subTables__}-shaped map (top-level or nested under a row).
     * Recurses into each row's {@code __subTables__} so link-form child slices persisted only under parent rows still hydrate.
     */
    private static final ThreadLocal<long[]> ENRICH_SQL_STATS = ThreadLocal.withInitial(() -> new long[4]);

    @SuppressWarnings("unchecked")
    private void enrichSubTablesMapPayload(
            ProcessInstanceInfo info,
            Map<String, Object> subTables,
            Map<String, String> bindingTableNames,
            Map<String, Map<String, MiRowProgress>> miProgressByTable) {
        if (subTables == null || subTables.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<String, Object> subTableEntry : subTables.entrySet()) {
                String sliceKey = subTableEntry.getKey();
                String tableName = bindingTableNames.get(sliceKey);
                if (tableName == null || tableName.isBlank()) {
                    tableName = bindingTableNames.get(MiOverlaySupport.normalizeMiTableKey(sliceKey));
                }
                if (tableName == null || tableName.isBlank() || !(subTableEntry.getValue() instanceof List<?> rows)) {
                    continue;
                }

                Map<String, MiRowProgress> miProgress =
                        miOverlayComponent.lookupMiProgressForDesignerTable(miProgressByTable, tableName);
                Set<String> protectedMiCols = MiOverlaySupport.miDashboardColumnsToProtect(miProgress);

                String safeTableName = SubTablePhysicalMetadataCache.requireSafeIdentifier(tableName);
                List<String> pkCols;
                try {
                    ENRICH_SQL_STATS.get()[3]++;
                    pkCols = subTablePhysicalMetadataCache.resolvePkColumnsCached(safeTableName);
                } catch (Exception e) {
                    log.debug("enrichSubTablesMapPayload: skip table {} (PK): {}", safeTableName, e.getMessage());
                    continue;
                }

                // Designer metadata can imply a PK for a logical name (e.g. MI token "subtable") while no physical
                // relation exists; SELECT against a missing relation aborts the whole PostgreSQL transaction and
                // the request later fails with UnexpectedRollbackException despite catch blocks here (see Docker
                // logs: ERROR relation "subtable" does not exist → current transaction is aborted).
                ENRICH_SQL_STATS.get()[2]++;
                final boolean physicalTablePresent = subTablePhysicalMetadataCache.subTableExists(safeTableName);

                // Legacy: physical table merge first (assignee, persisted field values). DB may still hold stale
                // task_status / task_current_node after a participant advances — those columns must not win over
                // the engine; MI overlay applied below overwrites them.
                for (Object rowObj : rows) {
                    if (!(rowObj instanceof Map<?, ?> rawRow)) {
                        continue;
                    }
                    Map<String, Object> row = (Map<String, Object>) rawRow;
                    Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
                    boolean canQueryDb = rowKey != null && physicalTablePresent;
                    if (canQueryDb) {
                        String where = SubTableRowKeySupport.buildPkWhereClause(pkCols);
                        Object[] args = SubTableRowKeySupport.orderedPkParams(pkCols, rowKey);
                        long __tsel = System.nanoTime();
                        ENRICH_SQL_STATS.get()[0]++;
                        List<Map<String, Object>> dbRows = jdbcTemplate.query(
                                "SELECT * FROM " + safeTableName + " WHERE " + where,
                                (rs, i) -> {
                                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                                    Map<String, Object> m = new HashMap<>();
                                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                                        m.put(meta.getColumnName(c), rs.getObject(c));
                                    }
                                    return m;
                                }, args);
                        ENRICH_SQL_STATS.get()[1] += (System.nanoTime() - __tsel) / 1_000_000L;
                        if (!dbRows.isEmpty()) {
                            Map<String, Object> dbRow = dbRows.get(0);
                            String displayName = (String) dbRow.get("assignee_display_name");
                            String userId = (String) dbRow.get("assignee_user_id");
                            if (displayName == null && userId != null && !userId.isBlank()) {
                                displayName = resolveUsernameById(userId);
                                dbRow.put("assignee_display_name", displayName);
                            }
                            repairStaleTaskStatus(safeTableName, dbRow, rowKey, pkCols);
                            for (Map.Entry<String, Object> entry : dbRow.entrySet()) {
                                if (entry.getValue() == null) {
                                    continue;
                                }
                                // Physical PG may still hold sub form1 after participant advances; engine overlay wins.
                                if (!protectedMiCols.isEmpty()
                                        && MiOverlaySupport.isProtectedMiDashboardColumn(protectedMiCols, entry.getKey())) {
                                    continue;
                                }
                                row.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    Object nestedRaw = row.get("__subTables__");
                    if (nestedRaw instanceof Map<?, ?> nestedMap && !nestedMap.isEmpty()) {
                        enrichSubTablesMapPayload(
                                info, (Map<String, Object>) nestedMap, bindingTableNames, miProgressByTable);
                    }
                }

                // Engine-driven MI status last so initiator My Request matches runtime tasks (not stale DB columns).
                if (!miProgress.isEmpty()) {
                    for (Object rowObj : rows) {
                        if (!(rowObj instanceof Map<?, ?> rawRow)) {
                            continue;
                        }
                        Map<String, Object> row = (Map<String, Object>) rawRow;
                        Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
                        if (rowKey == null) {
                            continue;
                        }
                        MiRowProgress p = MiOverlaySupport.lookupMiRowProgressForVariableRow(miProgress, pkCols, rowKey);
                        // Do not fabricate PENDING when no engine row matches — DB merge / variables may already
                        // hold COMPLETED for copied forms (subform_copy) or PK-canonical mismatch cases.
                        if (p != null) {
                            MiOverlaySupport.applyMiOverlayToVariableRow(row, p);
                        }
                        if (MiOverlaySupport.isPortalProcessCompleted(info)) {
                            MiOverlaySupport.normalizeStuckMiParticipantRowForCompletedProcess(row);
                        }
                    }
                } else if (MiOverlaySupport.isPortalProcessCompleted(info)) {
                    for (Object rowObj : rows) {
                        if (!(rowObj instanceof Map<?, ?> rawRow)) {
                            continue;
                        }
                        Map<String, Object> row = (Map<String, Object>) rawRow;
                        MiOverlaySupport.normalizeStuckMiParticipantRowForCompletedProcess(row);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("enrichSubTablesMapPayload skipped: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> resolveSubTableBindingTableNames(ProcessInstanceInfo info) {
        Map<String, String> result = new HashMap<>();
        String functionUnitRef = MiOverlaySupport.firstNonBlank(
                info.getFunctionUnitCatalogId(),
                info.getFunctionUnitCode(),
                info.getProcessDefinitionKey()
        );
        if (functionUnitRef == null || functionUnitRef.isBlank()) {
            return result;
        }
        try {
            Map<String, Object> content = processComponent.getFunctionUnitContent(functionUnitRef);
            Object formsObj = content.get("forms");
            if (!(formsObj instanceof List<?> forms)) {
                return result;
            }
            for (Object formObj : forms) {
                if (!(formObj instanceof Map<?, ?> form)) {
                    continue;
                }
                Object bindingsObj = form.get("tableBindings");
                if (!(bindingsObj instanceof List<?> bindings)) {
                    continue;
                }
                for (Object bindingObj : bindings) {
                    if (!(bindingObj instanceof Map<?, ?> binding)) {
                        continue;
                    }
                    Object bindingType = binding.get("bindingType");
                    Object bindingId = binding.get("bindingId");
                    Object tableName = binding.get("tableName");
                    // SUB and RELATED both participate in __subTables__ (designer + MI write-back).
                    // RELATED-only bindings were previously skipped, so initiator My Request never merged
                    // physical row data into variables and sub-task filled columns appeared empty.
                    String bt = bindingType != null ? String.valueOf(bindingType) : "";
                    if (("SUB".equals(bt) || "RELATED".equals(bt)) && bindingId != null && tableName != null) {
                        String phys = String.valueOf(tableName);
                        String bid = String.valueOf(bindingId);
                        result.put(bid, phys);
                        Object displayName = binding.get("tableDisplayName");
                        if (displayName != null && !String.valueOf(displayName).isBlank()) {
                            String label = String.valueOf(displayName).trim();
                            result.putIfAbsent(label, phys);
                            result.putIfAbsent(MiOverlaySupport.normalizeMiTableKey(label), phys);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("resolveSubTableBindingTableNames skipped: {}", e.getMessage());
        }
        return result;
    }

    /**
     * If a sub-table row task_status is PENDING but the engine's wf_extended_task_info
     * already records the task as COMPLETED, update the DB row to match AND recover
     * the form field values from Flowable's historical execution variables.
     * This self-heals rows that were stuck before the writeBack fix.
     */
    private void repairStaleTaskStatus(String tableName, Map<String, Object> dbRow, Map<String, Object> rowKey,
                                       List<String> pkCols) {
        Object ts = dbRow.get("task_status");
        if (ts != null && !"PENDING".equals(String.valueOf(ts))) {
            return;
        }
        if (!subTablePhysicalMetadataCache.columnExists(tableName, "task_status")) {
            return;
        }
        if (pkCols.size() != 1 || !(rowKey.get(pkCols.get(0)) instanceof Number)) {
            return;
        }
        long rowId = ((Number) rowKey.get(pkCols.get(0))).longValue();
        String pkColumn = pkCols.get(0);
        try {
            List<Map<String, Object>> taskEntries = jdbcTemplate.query(
                    "SELECT e.task_id, e.status FROM wf_extended_task_info e "
                            + "WHERE e.is_deleted = false "
                            + "AND (e.extended_properties LIKE '%\"subTableRowId\":' || ? || ',%' "
                            + "  OR e.extended_properties LIKE '%\"subTableRowId\":' || ? || '}%')",
                    (rs, i) -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("task_id", rs.getString("task_id"));
                        m.put("status", rs.getString("status"));
                        return m;
                    }, rowId, rowId);

            String completedTaskId = taskEntries.stream()
                    .filter(e -> "COMPLETED".equals(e.get("status")))
                    .map(e -> (String) e.get("task_id"))
                    .findFirst().orElse(null);

            if (completedTaskId == null) {
                return;
            }

            // 1. Fix task_status
            StringBuilder statusSql = new StringBuilder("UPDATE ").append(tableName)
                    .append(" SET task_status = 'COMPLETED'");
            if (subTablePhysicalMetadataCache.columnExists(tableName, "task_current_node")) {
                statusSql.append(", task_current_node = NULL");
                dbRow.put("task_current_node", null);
            }
            statusSql.append(" WHERE ").append(pkColumn).append(" = ? AND task_status = 'PENDING'");
            jdbcTemplate.update(statusSql.toString(), rowId);
            dbRow.put("task_status", "COMPLETED");

            // 2. Recover form field values from Flowable execution history.
            recoverFormFieldsFromHistory(tableName, dbRow, rowKey, pkCols, completedTaskId);

            log.info("repairStaleTaskStatus: fixed {} row {} -> COMPLETED (task {})", tableName, rowId, completedTaskId);
        } catch (Exception e) {
            log.debug("repairStaleTaskStatus skipped for {}#{}: {}", tableName, rowKey, e.getMessage());
        }
    }

    /**
     * Read the Flowable execution-scope variables that were saved when the subtask
     * was completed, and write matching columns back to the configured sub-table.
     */
    private void recoverFormFieldsFromHistory(String tableName, Map<String, Object> dbRow, Map<String, Object> rowKey,
                                              List<String> pkCols, String taskId) {
        try {
            List<String> execIds = jdbcTemplate.query(
                    "SELECT EXECUTION_ID_ FROM ACT_HI_TASKINST WHERE ID_ = ?",
                    (rs, i) -> rs.getString(1), taskId);
            if (execIds.isEmpty()) {
                return;
            }

            List<Map<String, Object>> histVars = jdbcTemplate.query(
                    "SELECT NAME_, TEXT_ FROM ACT_HI_VARINST WHERE EXECUTION_ID_ = ? AND TEXT_ IS NOT NULL",
                    (rs, i) -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("name", rs.getString("NAME_"));
                        m.put("value", rs.getString("TEXT_"));
                        return m;
                    }, execIds.get(0));

            Set<String> validCols = dbRow.keySet();
            Set<String> skipCols = new HashSet<>(List.of(
                    "row_version", "task_status", "task_current_node", "meeting_id", "sort_order"));
            for (String pk : pkCols) {
                skipCols.add(pk);
            }
            Map<String, Object> updates = new HashMap<>();
            for (Map<String, Object> hv : histVars) {
                String name = (String) hv.get("name");
                Object value = hv.get("value");
                if (name == null || value == null) {
                    continue;
                }
                String col = SubTablePhysicalColumnResolver.resolvePhysicalColumnKey(
                        jdbcTemplate, tableName, name, validCols);
                if (col != null && !skipCols.contains(col)) {
                    updates.put(col, value);
                }
            }
            if (updates.isEmpty()) {
                return;
            }

            StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                sql.append(entry.getKey()).append(" = ?, ");
                params.add(entry.getValue());
            }
            sql.setLength(sql.length() - 2);
            sql.append(" WHERE ").append(SubTableRowKeySupport.buildPkWhereClause(pkCols));
            params.addAll(Arrays.asList(SubTableRowKeySupport.orderedPkParams(pkCols, rowKey)));

            jdbcTemplate.update(sql.toString(), params.toArray());
            dbRow.putAll(updates);
            log.info("recoverFormFieldsFromHistory: recovered {} fields for {} rowKey {}", updates.size(), tableName, rowKey);
        } catch (Exception e) {
            log.debug("recoverFormFieldsFromHistory skipped for {} {}: {}", tableName, rowKey, e.getMessage());
        }
    }

    private String resolveUsernameById(String userId) {
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT COALESCE(username, display_name) FROM sys_users WHERE id = ? LIMIT 1",
                    (rs, i) -> rs.getString(1), userId);
            return names.isEmpty() ? userId : names.get(0);
        } catch (Exception e) {
            log.debug("resolveUsernameById failed for {}: {}", userId, e.getMessage());
            return userId;
        }
    }
}
