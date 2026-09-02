package com.portal.component;

import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.client.WorkflowEngineClient;
import com.portal.component.MiOverlaySupport.MiRowProgress;
import com.portal.dto.ProcessInstanceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine-driven multi-instance (MI) row progress resolution and overlay propagation across
 * {@code __subTables__} variable slices. Extracted from {@link ProcessComponent}; static helpers
 * live in {@link MiOverlaySupport}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiOverlayComponent {

    private final WorkflowEngineClient workflowEngineClient;
    private final SubTablePhysicalMetadataCache subTablePhysicalMetadataCache;

    private static final long MI_STATUS_CACHE_TTL_MS = 5000L;
    private final Map<String, MiStatusCacheEntry> miStatusCache = new ConcurrentHashMap<>();

    private record MiStatusCacheEntry(Map<String, Object> payload, long timestampMs) {}

    /**
     * Drop the cached MI status for a process instance so the next {@link #resolveMiRowProgress}
     * re-queries the engine. Called when a task in this process completes/transfers/returns, since
     * those change sub-task {@code task_status} but the {@value #MI_STATUS_CACHE_TTL_MS}ms TTL would
     * otherwise serve a stale snapshot — surfacing as "My Request needs two refreshes to update".
     */
    public void invalidate(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        miStatusCache.remove(processInstanceId);
    }

    @SuppressWarnings("unchecked")
    Map<String, Map<String, MiRowProgress>> resolveMiRowProgress(String processInstanceId, String processInstanceStatus) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return Collections.emptyMap();
        }
        if (!workflowEngineClient.isAvailable()) {
            return Collections.emptyMap();
        }
        try {
            long __t = System.nanoTime();
            Map<String, Object> data;
            MiStatusCacheEntry cached = miStatusCache.get(processInstanceId);
            if (cached != null
                    && System.currentTimeMillis() - cached.timestampMs() < MI_STATUS_CACHE_TTL_MS) {
                data = cached.payload();
                log.info("[PERF] resolveMiRowProgress.getMultiInstanceStatus CACHE HIT for {}", processInstanceId);
            } else {
                Optional<Map<String, Object>> opt = workflowEngineClient.getMultiInstanceStatus(processInstanceId);
                log.info("[PERF] resolveMiRowProgress.getMultiInstanceStatus(engine) took {} ms",
                        (System.nanoTime() - __t) / 1_000_000L);
                if (opt.isEmpty()) {
                    return Collections.emptyMap();
                }
                data = opt.get();
                miStatusCache.put(processInstanceId, new MiStatusCacheEntry(data, System.currentTimeMillis()));
            }
            Object tasksObj = data.get("tasks");
            if (!(tasksObj instanceof List<?> tasks)) {
                return Collections.emptyMap();
            }
            long __tpk = System.nanoTime();
            int[] __pkCalls = {0};
            Map<String, Map<String, List<Map<String, Object>>>> byTableRow = new HashMap<>();
            for (Object o : tasks) {
                if (!(o instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) raw;
                Object tableObj = t.get("subTableName");
                String tn = tableObj != null ? String.valueOf(tableObj).trim() : "";
                if (tn.isEmpty()) {
                    continue;
                }
                List<String> pkCols;
                try {
                    __pkCalls[0]++;
                    pkCols = subTablePhysicalMetadataCache.resolvePkColumnsCached(tn);
                } catch (Exception e) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> rowKey = t.get("subTableRowKey") instanceof Map<?, ?>
                        ? SubTableRowKeySupport.normalizeStringKeyMap((Map<?, ?>) t.get("subTableRowKey"))
                        : null;
                if (rowKey == null || !SubTableRowKeySupport.isComplete(pkCols, rowKey)) {
                    rowKey = SubTableRowKeySupport.rowKeyFromExtendedProps(t, pkCols);
                }
                if (rowKey == null) {
                    continue;
                }
                String canon = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rowKey);
                byTableRow.computeIfAbsent(tn, k -> new HashMap<>())
                        .computeIfAbsent(canon, k -> new ArrayList<>())
                        .add(t);
            }
            log.info("[PERF] resolveMiRowProgress: {} MI tasks, {} resolvePrimaryKeyColumns(JDBC) calls, loop took {} ms",
                    tasks.size(), __pkCalls[0], (System.nanoTime() - __tpk) / 1_000_000L);

            Map<String, Map<String, MiRowProgress>> out = new HashMap<>();
            boolean processEndedCompleted = processInstanceStatus != null
                    && "COMPLETED".equalsIgnoreCase(processInstanceStatus.trim());
            for (var e : byTableRow.entrySet()) {
                String tableName = e.getKey();
                Map<String, List<Map<String, Object>>> rows = e.getValue();
                Map<String, MiRowProgress> rowProgress = new HashMap<>();
                for (var re : rows.entrySet()) {
                    String rowCanon = re.getKey();
                    List<Map<String, Object>> rowTasks = re.getValue();
                    if (rowTasks == null || rowTasks.isEmpty()) {
                        continue;
                    }

                    String statusCol = MiOverlaySupport.firstNonBlank(
                            MiOverlaySupport.stringVal(rowTasks.get(0).get("miTaskStatusField")), "task_status");
                    String nodeCol = MiOverlaySupport.firstNonBlank(
                            MiOverlaySupport.stringVal(rowTasks.get(0).get("miTaskCurrentNodeField")), "task_current_node");

                    MiRowProgress computed;
                    if (processEndedCompleted) {
                        // Runtime is gone; wf_extended_task_info may leave stray non-terminal rows. Never show MI as in-flight.
                        computed = new MiRowProgress(statusCol, nodeCol, "COMPLETED", "end");
                    } else {
                        List<Map<String, Object>> saneRowTasks =
                                MiOverlaySupport.dedupeMiTasksPreferCompletedPerStepKey(rowTasks);
                        Map<String, Object> active = MiOverlaySupport.pickLatestActiveTask(saneRowTasks);
                        if (active != null) {
                            String node = MiOverlaySupport.firstNonBlank(
                                    MiOverlaySupport.stringVal(active.get("taskName")), "-");
                            computed = new MiRowProgress(statusCol, nodeCol, "IN_PROGRESS", node);
                        } else {
                            computed = new MiRowProgress(statusCol, nodeCol, "COMPLETED", "end");
                        }
                    }
                    rowProgress.put(rowCanon, computed);
                }
                if (!rowProgress.isEmpty()) {
                    out.put(tableName, rowProgress);
                }
            }

            return out;
        } catch (Exception e) {
            log.debug("resolveMiRowProgress skipped: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    Map<String, MiRowProgress> lookupMiProgressForDesignerTable(
            Map<String, Map<String, MiRowProgress>> byTable,
            String designerTableName) {
        if (designerTableName == null || designerTableName.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, MiRowProgress> direct = byTable.get(designerTableName);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        for (Map.Entry<String, Map<String, MiRowProgress>> e : byTable.entrySet()) {
            if (e.getKey() != null && designerTableName.equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        String dn = MiOverlaySupport.normalizeMiTableKey(designerTableName);
        for (Map.Entry<String, Map<String, MiRowProgress>> e : byTable.entrySet()) {
            if (e.getKey() != null && MiOverlaySupport.normalizeMiTableKey(e.getKey()).equals(dn)) {
                return e.getValue();
            }
        }
        /*
         * Designer binding labels often suffix the BPMN MI scope token (subtable vs subtable2).
         * Prefer longest engine-table prefix so unrelated tables never inherit overlay (replaces blind singleton).
         */
        Map<String, MiRowProgress> bestPrefix = null;
        int bestPrefixLen = -1;
        for (Map.Entry<String, Map<String, MiRowProgress>> e : byTable.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            String ek = MiOverlaySupport.normalizeMiTableKey(e.getKey());
            if (ek.length() < 8) {
                continue;
            }
            if (dn.startsWith(ek) && dn.length() > ek.length() && ek.length() > bestPrefixLen) {
                bestPrefixLen = ek.length();
                bestPrefix = e.getValue();
            }
        }
        if (bestPrefix != null) {
            return bestPrefix;
        }
        /*
         * Prefixed logical names (dw_* / scope segments) embed the MI token — pick longest engine key contained in dn.
         */
        Map<String, MiRowProgress> bestContain = null;
        int bestContainLen = -1;
        for (Map.Entry<String, Map<String, MiRowProgress>> e : byTable.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            String ek = MiOverlaySupport.normalizeMiTableKey(e.getKey());
            if (ek.length() < 8) {
                continue;
            }
            if (dn.contains(ek) && ek.length() > bestContainLen) {
                bestContainLen = ek.length();
                bestContain = e.getValue();
            }
        }
        if (bestContain != null) {
            return bestContain;
        }
        return Collections.emptyMap();
    }

    /**
     * Apply resolved MI progress to every sub-table row in variables, regardless of {@code __subTables__} slice key.
     * Fixes initiator My Request when the first merged row comes from an unmapped slice (still showing sub form1).
     */
    @SuppressWarnings("unchecked")
    void propagateMiOverlayAcrossAllSubTableSlices(
            Map<String, Object> subTables,
            Map<String, Map<String, MiRowProgress>> miProgressByTable,
            ProcessInstanceInfo info) {
        if (subTables == null || subTables.isEmpty() || miProgressByTable == null || miProgressByTable.isEmpty()) {
            return;
        }
        Map<String, List<String>> pkColsByTable = new HashMap<>();
        for (String tableName : miProgressByTable.keySet()) {
            if (tableName == null || tableName.isBlank()) {
                continue;
            }
            try {
                String safe = SubTablePhysicalMetadataCache.requireSafeIdentifier(tableName);
                pkColsByTable.put(tableName, subTablePhysicalMetadataCache.resolvePkColumnsCached(safe));
            } catch (Exception e) {
                log.debug("propagateMiOverlay: skip PK for {}: {}", tableName, e.getMessage());
            }
        }
        Map<Long, MiRowProgress> byNumericRowId = buildMiProgressIndexByNumericRowId(miProgressByTable);
        propagateMiOverlayWalkSubTables(subTables, miProgressByTable, pkColsByTable, byNumericRowId, info);
    }

    private Map<Long, MiRowProgress> buildMiProgressIndexByNumericRowId(
            Map<String, Map<String, MiRowProgress>> miProgressByTable) {
        Map<Long, MiRowProgress> out = new HashMap<>();
        if (miProgressByTable == null) {
            return out;
        }
        for (Map<String, MiRowProgress> tableMap : miProgressByTable.values()) {
            if (tableMap == null) {
                continue;
            }
            for (var e : tableMap.entrySet()) {
                Long id = MiOverlaySupport.parseCanonicalSinglePkSuffixLong(e.getKey());
                if (id == null) {
                    continue;
                }
                out.merge(id, e.getValue(), MiOverlaySupport::preferMiRowProgressOverlay);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void propagateMiOverlayWalkSubTables(
            Map<String, Object> subTables,
            Map<String, Map<String, MiRowProgress>> miProgressByTable,
            Map<String, List<String>> pkColsByTable,
            Map<Long, MiRowProgress> byNumericRowId,
            ProcessInstanceInfo info) {
        for (Object sliceVal : subTables.values()) {
            if (!(sliceVal instanceof List<?> rows)) {
                continue;
            }
            for (Object rowObj : rows) {
                if (!(rowObj instanceof Map<?, ?> rawRow)) {
                    continue;
                }
                Map<String, Object> row = (Map<String, Object>) rawRow;
                MiOverlaySupport.normalizeVariableRowPkEnvelope(row);
                MiRowProgress best = resolveBestMiProgressForVariableRow(row, miProgressByTable, pkColsByTable);
                if (best == null) {
                    Long numericId = MiOverlaySupport.extractNumericSubTableRowId(row);
                    if (numericId != null) {
                        best = byNumericRowId.get(numericId);
                    }
                }
                if (best != null) {
                    MiOverlaySupport.applyMiOverlayToVariableRow(row, best);
                }
                if (MiOverlaySupport.isPortalProcessCompleted(info)) {
                    // best carries this FU's configured status / node column names when an engine row matched
                    MiOverlaySupport.normalizeStuckMiParticipantRowForCompletedProcess(row, best);
                }
                Object nestedRaw = row.get("__subTables__");
                if (nestedRaw instanceof Map<?, ?> nestedMap && !nestedMap.isEmpty()) {
                    propagateMiOverlayWalkSubTables(
                            (Map<String, Object>) nestedMap,
                            miProgressByTable,
                            pkColsByTable,
                            byNumericRowId,
                            info);
                }
            }
        }
    }

    private MiRowProgress resolveBestMiProgressForVariableRow(
            Map<String, Object> row,
            Map<String, Map<String, MiRowProgress>> miProgressByTable,
            Map<String, List<String>> pkColsByTable) {
        MiRowProgress best = null;
        for (var tableEntry : miProgressByTable.entrySet()) {
            List<String> pkCols = pkColsByTable.get(tableEntry.getKey());
            if (pkCols == null || pkCols.isEmpty()) {
                continue;
            }
            Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
            if (rowKey == null) {
                continue;
            }
            MiRowProgress p = MiOverlaySupport.lookupMiRowProgressForVariableRow(tableEntry.getValue(), pkCols, rowKey);
            if (p != null) {
                best = MiOverlaySupport.preferMiRowProgressOverlay(best, p);
            }
        }
        return best;
    }

    /**
     * Parallel MI rows may sit on different user tasks; Flowable / portal DB {@link com.portal.entity.ProcessInstance#getCurrentNode()}
     * reflects one arbitrary active task (often {@code tasks.get(0)}). Align headline {@link ProcessInstanceInfo#getCurrentNode()}
     * with the numerically greatest {@code sub form N} among in-flight MI rows (matches sub-table overlay semantics).
     */
    void reconcileCurrentNodeWithMiOverlay(
            ProcessInstanceInfo info, Map<String, Map<String, MiRowProgress>> byTable) {
        if (info == null || byTable == null || byTable.isEmpty()) {
            return;
        }
        String bestNode = null;
        int bestOrd = Integer.MIN_VALUE;
        for (Map<String, MiRowProgress> rows : byTable.values()) {
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            for (MiRowProgress p : rows.values()) {
                if (p == null || p.currentNode() == null || p.currentNode().isBlank()) {
                    continue;
                }
                String st = p.status();
                if (st == null || st.isBlank()) {
                    continue;
                }
                String u = st.trim().toUpperCase(Locale.ROOT);
                if (!("IN_PROGRESS".equals(u) || "ASSIGNED".equals(u) || "CREATED".equals(u))) {
                    continue;
                }
                int ord = MiOverlaySupport.miSubFormOrdinalHint(p.currentNode());
                if (ord == Integer.MIN_VALUE) {
                    continue;
                }
                if (ord > bestOrd) {
                    bestOrd = ord;
                    bestNode = p.currentNode();
                }
            }
        }
        if (bestNode == null || bestOrd == Integer.MIN_VALUE) {
            return;
        }
        String prev = Optional.ofNullable(info.getCurrentNode()).orElse("").trim();
        int existingOrd = MiOverlaySupport.miSubFormOrdinalHint(prev);
        boolean prevLooksMiSubForm =
                prev.toLowerCase(Locale.ROOT).replace(" ", "").contains("subform");

        boolean upgrade =
                (existingOrd != Integer.MIN_VALUE && bestOrd > existingOrd)
                        || (prevLooksMiSubForm && bestOrd > existingOrd)
                        || (prevLooksMiSubForm && bestOrd == existingOrd && !bestNode.equalsIgnoreCase(prev));
        if (!upgrade) {
            return;
        }
        info.setCurrentNode(bestNode);
    }

    /**
     * 外层多实例 subProcess 的 name（如 "multi"），取自 MI 状态响应的 {@code multiInstanceActivityName}。
     * 读 {@link #resolveMiRowProgress} 已填充的 MI 状态缓存（同一次详情请求内已调用过），无缓存/无值返回 null。
     * 用于详情「当前步骤」在流程处于 MI 内部时展示多实例节点名而非具体内层子任务名。
     */
    String getMiActivityName(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return null;
        }
        MiStatusCacheEntry cached = miStatusCache.get(processInstanceId);
        if (cached == null || cached.payload() == null) {
            return null;
        }
        Object name = cached.payload().get("multiInstanceActivityName");
        String s = name != null ? String.valueOf(name).trim() : "";
        return s.isEmpty() ? null : s;
    }
}
