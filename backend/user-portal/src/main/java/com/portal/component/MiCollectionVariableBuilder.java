package com.portal.component;

import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the multi-instance (MI) collection variable for sub-process prerequisites: merges
 * {@code __subTables__} from {@link TaskInfo}, reads collection variable name and assignee field from
 * the BPMN definition, resolves the sub-table primary key, and injects the row collection.
 * Extracted from {@link TaskProcessComponent}; XML helpers live in {@link BpmnMiXmlSupport} and
 * stateless {@code __subTables__} helpers in {@link MiSubTableVariableSupport}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiCollectionVariableBuilder {

    private final WorkflowEngineClient workflowEngineClient;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Approval completion body often lacks full {@code __subTables__}; todo detail {@link TaskInfo#getVariables()} is merged with local ProcessInstance,
     * Merge before building MI collection or zero child tasks are created.
     */
    @SuppressWarnings("unchecked")
    void mergeSubTablesFromTaskInfoForMi(TaskInfo task, Map<String, Object> variables) {
        if (task == null || variables == null) {
            return;
        }
        Map<String, Object> taskVars = task.getVariables();
        if (taskVars == null || taskVars.isEmpty()) {
            return;
        }
        Object fromTask = taskVars.get("__subTables__");
        if (!(fromTask instanceof Map<?, ?> taskSubMap) || taskSubMap.isEmpty()) {
            return;
        }
        Object cur = variables.get("__subTables__");
        if (!(cur instanceof Map<?, ?>) || ((Map<?, ?>) cur).isEmpty()) {
            Map<String, Object> hydrated = new LinkedHashMap<>((Map<String, Object>) fromTask);
            variables.put("__subTables__", MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(hydrated));
            log.info("[MI] Hydrated __subTables__ from TaskInfo for task {} (processInstanceId={})",
                    task.getTaskId(), task.getProcessInstanceId());
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) cur);
        for (Map.Entry<?, ?> e : taskSubMap.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String k = String.valueOf(e.getKey());
            if (!merged.containsKey(k)) {
                merged.put(k, e.getValue());
            }
        }
        variables.put("__subTables__", MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(merged));
    }

    /**
     * Designer assignee column may be {@code assignee}; stored rows often use {@code assignee_user_id}; BPMN still uses configured assigneeField.
     */
    private static final List<String> MI_ASSIGNEE_ALTERNATE_KEYS = List.of(
            "assignee_user_id", "assigneeUserId", "assignee_id", "assigneeId", "assignee", "user_id", "userId");

    /**
     * MI 行「是否可纳入」取值：user 模式走 {@link #resolveMiAssigneeRaw}（带 assignee 兜底键），
     * role 模式直接读配置列（role code 不该套用 assignee 兜底键）。
     */
    private Object resolveMiEligibilityRaw(Map<String, Object> row, String field, boolean roleMode) {
        if (roleMode) {
            if (row == null || field == null || field.isBlank()) {
                return null;
            }
            return SubTableRowKeySupport.getRowValueIgnoreCase(row, field.trim());
        }
        return resolveMiAssigneeRaw(row, field);
    }

    /**
     * 逐行分派下「该行是否可纳入 MI」：assigneeField（带兜底键）或 roleField（直接读列）任一非空即合格。
     */
    private boolean rowHasAssigneeOrRole(Map<String, Object> row, String assigneeField, String roleField) {
        if (assigneeField != null && !assigneeField.isBlank()) {
            Object v = resolveMiEligibilityRaw(row, assigneeField, false);
            if (v != null && !String.valueOf(v).trim().isEmpty()) {
                return true;
            }
        }
        if (roleField != null && !roleField.isBlank()) {
            Object v = resolveMiEligibilityRaw(row, roleField, true);
            if (v != null && !String.valueOf(v).trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Object resolveMiAssigneeRaw(Map<String, Object> row, String configuredAssigneeField) {
        if (row == null || configuredAssigneeField == null || configuredAssigneeField.isBlank()) {
            return null;
        }
        Object direct = SubTableRowKeySupport.getRowValueIgnoreCase(row, configuredAssigneeField);
        if (direct != null && !String.valueOf(direct).trim().isEmpty()) {
            return direct;
        }
        String trimmed = configuredAssigneeField.trim();
        for (String alt : MI_ASSIGNEE_ALTERNATE_KEYS) {
            if (alt.equalsIgnoreCase(trimmed)) {
                continue;
            }
            Object v = SubTableRowKeySupport.getRowValueIgnoreCase(row, alt);
            if (v != null && !String.valueOf(v).trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }

    /**
     * When the completed task is a prerequisite for a multi-instance sub-process, reads collection variable name and assignee field from BPMN,
     * Builds collection variable from __subTables__ and injects into variables.
     * <p>
     * Replaces hard-coded {@code Task_AssignParticipants} checks; adapts to any BPMN multi-instance configuration.
     */
    void injectMiCollectionFromBpmn(String processDefinitionKey, String taskDefinitionKey,
                                    String processInstanceId, Map<String, Object> variables) {
        try {
            if (processDefinitionKey == null || processDefinitionKey.isBlank()
                    || taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
                log.warn("[MI] Missing processDefinitionKey or taskDefinitionKey, skip collection injection (procDef={}, taskDef={})",
                        processDefinitionKey, taskDefinitionKey);
                return;
            }
            log.info("[MI] injectMiCollectionFromBpmn begin processDefinitionKey={} taskDefinitionKey={} processInstanceId={}",
                    processDefinitionKey, taskDefinitionKey, processInstanceId);
            Optional<String> bpmnOpt = workflowEngineClient.getBpmnXml(processDefinitionKey);
            if (bpmnOpt.isEmpty()) {
                log.warn("[MI] Could not fetch BPMN XML for processDefinitionKey={}", processDefinitionKey);
                return;
            }
            Document document = BpmnMiXmlSupport.parseBpmnSecurely(bpmnOpt.get());

            // 1. Locate current task node (bpmn:userTask / userTask)
            Element taskElement = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "userTask", taskDefinitionKey);
            if (taskElement == null) {
                log.warn("[MI] UserTask id={} not found in BPMN (check taskDefinitionKey vs XML). Skip MI injection.",
                        taskDefinitionKey);
                return;
            }

            // 2. Outgoing: many exported BPMN files only have sequenceFlow@sourceRef, no <outgoing> under userTask
            List<String> outgoingFlowIds = BpmnMiXmlSupport.getDirectChildTextValues(taskElement, "outgoing");
            if (outgoingFlowIds.isEmpty()) {
                outgoingFlowIds = BpmnMiXmlSupport.listSequenceFlowIdsWithSourceRef(document, taskDefinitionKey);
                if (!outgoingFlowIds.isEmpty()) {
                    log.info("[MI] Task {} has no <outgoing> children; using {} sequenceFlow(s) via sourceRef",
                            taskDefinitionKey, outgoingFlowIds.size());
                }
            }
            if (outgoingFlowIds.isEmpty()) {
                log.warn("[MI] No outgoing from userTask {} (no child <outgoing> and no sequenceFlow with matching sourceRef). Skip MI injection.",
                        taskDefinitionKey);
                return;
            }

            Deque<String> frontier = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            for (String flowId : outgoingFlowIds) {
                enqueueSequenceFlowTargets(document, flowId, frontier);
            }

            while (!frontier.isEmpty()) {
                String nodeId = frontier.poll();
                if (nodeId == null || nodeId.isBlank()) {
                    continue;
                }
                if (!visited.add(nodeId)) {
                    continue;
                }

                Element subProcess = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "subProcess", nodeId);
                if (subProcess != null) {
                    Element loopCharacteristics = BpmnMiXmlSupport.findMultiInstanceLoopInSubProcess(subProcess);
                    if (loopCharacteristics != null) {
                        String collectionVariableName = BpmnMiXmlSupport.extractFlowableCollection(loopCharacteristics);
                        if (collectionVariableName == null || collectionVariableName.isBlank()) {
                            log.warn("[MI] SubProcess {} has no flowable:collection configuration", nodeId);
                            continue;
                        }
                        // 逐行分派：assigneeField / roleField / buField 都读出来，运行时按每行填了什么决定。
                        // assigneeMode 只标节点允许了哪些方式（user|role|both），不再决定整表模式。
                        String assigneeMode = BpmnMiXmlSupport.extractAssigneeModeFromSubProcess(subProcess);
                        boolean roleAllowed = assigneeMode == null
                                || "role".equalsIgnoreCase(assigneeMode.trim())
                                || "both".equalsIgnoreCase(assigneeMode.trim());
                        boolean userAllowed = assigneeMode == null
                                || "user".equalsIgnoreCase(assigneeMode.trim())
                                || "both".equalsIgnoreCase(assigneeMode.trim());
                        String roleField = roleAllowed ? BpmnMiXmlSupport.extractRoleFieldFromSubProcess(subProcess) : null;
                        String buField = roleAllowed ? BpmnMiXmlSupport.extractBuFieldFromSubProcess(subProcess) : null;
                        String assigneeField = userAllowed
                                ? BpmnMiXmlSupport.extractAssigneeFieldFromSubProcess(subProcess) : null;
                        boolean hasAssignee = assigneeField != null && !assigneeField.isBlank();
                        boolean hasRole = roleField != null && !roleField.isBlank();
                        if (!hasAssignee && !hasRole) {
                            log.warn("[MI] subProcess {} inner UserTask has neither assigneeField nor roleField", nodeId);
                            continue;
                        }
                        String bpmnSubTableName = BpmnMiXmlSupport.findFirstPropertyValue(subProcess, "subTableName");
                        buildMiCollectionVariable(variables, collectionVariableName, assigneeField, bpmnSubTableName,
                                roleField, buField);
                        return;
                    }
                    List<String> spOut = BpmnMiXmlSupport.getDirectChildTextValues(subProcess, "outgoing");
                    for (String outFlow : spOut) {
                        enqueueSequenceFlowTargets(document, outFlow, frontier);
                    }
                    if (spOut.isEmpty()) {
                        for (String sfId : BpmnMiXmlSupport.listSequenceFlowIdsWithSourceRef(document, nodeId)) {
                            enqueueSequenceFlowTargets(document, sfId, frontier);
                        }
                    }
                    continue;
                }

                Element flowNode = BpmnMiXmlSupport.findElementByBpmnId(document, nodeId);
                if (flowNode != null) {
                    List<String> outs = BpmnMiXmlSupport.getDirectChildTextValues(flowNode, "outgoing");
                    if (outs.isEmpty()) {
                        outs = BpmnMiXmlSupport.listSequenceFlowIdsWithSourceRef(document, nodeId);
                    }
                    for (String outFlow : outs) {
                        enqueueSequenceFlowTargets(document, outFlow, frontier);
                    }
                }
            }

            log.warn("[MI] No reachable multi-instance subProcess found from task {} (BFS exhausted). Skip MI injection.",
                    taskDefinitionKey);
        } catch (Exception e) {
            log.warn("[MI] injectMiCollectionFromBpmn failed for processDefinitionKey={}, taskDefinitionKey={}: {}",
                    processDefinitionKey, taskDefinitionKey, e.getMessage());
        }
    }

    /**
     * Builds multi-instance collection variable from __subTables__.
     * collectionVariableName is often {@code multiInstance_{subTableName}_collection}; PK resolved from PG / designer metadata first.
     * For JSON-only sub-tables (no physical table), fuzzy-match table name via {@code dw_table_definitions}; else infer single {@code id} from {@code __subTables__}.
     * <p>
     * __subTables__ often has multiple binding lists; naive flattening treats any row with target PK columns and assignee as an MI element
     * (e.g. multiple sub-tables with column {@code id}) creates far more child tasks than expected after the prerequisite task. Each map value list is scored separately,
     * Uses only the source list that best matches target PK + assignee (merge and dedupe on ties).
     */
    @SuppressWarnings("unchecked")
    private void buildMiCollectionVariable(Map<String, Object> variables, String collectionVariableName,
                                          String assigneeField, String bpmnSubTableName,
                                          String roleField, String buField) {
        // 逐行分派：一行只要 assigneeField 或 roleField 任一有值即可纳入 MI（场景 C 混用）。
        boolean hasAssigneeField = assigneeField != null && !assigneeField.isBlank();
        boolean hasRoleField = roleField != null && !roleField.isBlank();
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map)) {
            log.warn("[MI] No __subTables__ found, setting empty collection for {}", collectionVariableName);
            variables.put(collectionVariableName, List.of());
            return;
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;

        String tokenFromCollectionVar = parseSubTableNameFromMiCollectionVariable(collectionVariableName);
        MiSubTablePkResult pkResult = resolveMiSubTablePk(tokenFromCollectionVar);
        if (pkResult == null && bpmnSubTableName != null && !bpmnSubTableName.isBlank()) {
            String trimmed = bpmnSubTableName.trim();
            if (tokenFromCollectionVar == null || !trimmed.equalsIgnoreCase(tokenFromCollectionVar)) {
                pkResult = resolveMiSubTablePk(trimmed);
            }
        }
        if ((pkResult == null || pkResult.pkCols() == null || pkResult.pkCols().isEmpty())
                && tokenFromCollectionVar == null
                && collectionVariableName != null
                && collectionVariableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            pkResult = resolveMiSubTablePk(collectionVariableName.trim());
        }
        if ((pkResult == null || pkResult.pkCols() == null || pkResult.pkCols().isEmpty())) {
            pkResult = inferMiPkFromJsonSubTables(subTables, assigneeField, roleField, collectionVariableName);
        }

        if (pkResult == null || pkResult.pkCols() == null || pkResult.pkCols().isEmpty()) {
            log.warn(
                    "[MI] Cannot resolve primary key for multi-instance collection '{}' (parsed token='{}', bpmnSubTableName='{}'). "
                            + "No PG table match, designer metadata match, or inferable JSON id column. Setting empty collection.",
                    collectionVariableName,
                    tokenFromCollectionVar,
                    bpmnSubTableName);
            variables.put(collectionVariableName, List.of());
            return;
        }
        List<String> pkCols = pkResult.pkCols();

        List<Map<String, Object>> allRows = selectRowsForMiCollection(subTables, pkCols, assigneeField, roleField);
        if (allRows.isEmpty()) {
            log.warn(
                    "[MI] No eligible sub-table rows for '{}' (resolvedTable={}, pk={}, assigneeField='{}', roleField='{}'); setting empty collection",
                    collectionVariableName,
                    pkResult.resolvedTable(),
                    pkCols,
                    assigneeField,
                    roleField);
            variables.put(collectionVariableName, List.of());
            return;
        }

        List<Map<String, Object>> collection = new ArrayList<>();
        List<Integer> emptyEligibilityRows = new ArrayList<>();
        Set<String> seenRowKeys = new LinkedHashSet<>();
        int skippedUnmappedPk = 0;
        for (int i = 0; i < allRows.size(); i++) {
            Map<String, Object> row = allRows.get(i);
            Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
            if (rowKey == null) {
                skippedUnmappedPk++;
                log.warn(
                        "[MI] Row {} omitted from '{}': sub-table row does not contain values for primary key columns {} (available keys: {})",
                        i + 1,
                        collectionVariableName,
                        pkCols,
                        row.keySet());
                continue;
            }
            Object rowId = null;
            if (pkCols.size() == 1) {
                rowId = rowKey.get(pkCols.get(0));
            }
            // 逐行：分别取该行的 assignee 值（带兜底键）与 role 值（直接读列），任一非空即合格。
            String assigneeText = "";
            if (hasAssigneeField) {
                assigneeText = MiSubTableVariableSupport.normalizeMiAssigneeText(
                        resolveMiEligibilityRaw(row, assigneeField, false));
            }
            String roleText = "";
            if (hasRoleField) {
                roleText = MiSubTableVariableSupport.normalizeMiAssigneeText(
                        resolveMiEligibilityRaw(row, roleField, true));
            }
            if (assigneeText.isEmpty() && roleText.isEmpty()) {
                emptyEligibilityRows.add(i + 1);
                continue;
            }
            String dedupKey = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rowKey);
            if (!seenRowKeys.add(dedupKey)) {
                log.debug("[MI] Duplicate row identity skipped for collection {}: {}", collectionVariableName, dedupKey);
                continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("rowKey", new LinkedHashMap<>(rowKey));
            if (rowId instanceof Number) {
                item.put("rowId", ((Number) rowId).longValue());
            } else if (rowId != null && pkCols.size() == 1) {
                item.put("rowId", rowId);
            }
            // 该行填了 role → 带 role/bu code（引擎逐行判定优先走 role 分支）；填了 assignee → 带 assignee。
            if (!roleText.isEmpty()) {
                item.put(roleField, roleText);
                if (buField != null && !buField.isBlank()) {
                    Object buValue = SubTableRowKeySupport.getRowValueIgnoreCase(row, buField.trim());
                    String buText = MiSubTableVariableSupport.normalizeMiAssigneeText(buValue);
                    if (!buText.isEmpty()) {
                        item.put(buField, buText);
                    }
                }
            }
            if (!assigneeText.isEmpty()) {
                item.put(assigneeField, assigneeText);
            }

            collection.add(item);
        }

        if (skippedUnmappedPk > 0) {
            log.warn("[MI] {} row(s) omitted from '{}' because PK values were missing in form data; collection size={}",
                    skippedUnmappedPk, collectionVariableName, collection.size());
        }

        if (!emptyEligibilityRows.isEmpty()) {
            String rowNumbers = String.join(", ",
                    emptyEligibilityRows.stream().map(String::valueOf).toArray(String[]::new));
            log.warn("[MI] Rows {} have neither assigneeField '{}' nor roleField '{}' for collection {}",
                    rowNumbers, assigneeField, roleField, collectionVariableName);
        }

        variables.put(collectionVariableName, collection);
        log.info("[MI] Built collection '{}' with {} items, assigneeField='{}', roleField='{}', collectionVarMiddleToken='{}', resolvedTable='{}'",
                collectionVariableName, collection.size(), assigneeField, roleField,
                tokenFromCollectionVar, pkResult.resolvedTable());
    }

    /**
     * Result of resolving MI row identity: Postgres table id, designer table_name, or JSON-inferred sentinel.
     */
    private record MiSubTablePkResult(String resolvedTable, List<String> pkCols) {
    }

    /**
     * Resolves the logical segment in a BPMN collection variable (e.g. {@code participants}) to primary-key column names.
     * Order: physical table exact/fuzzy → {@code dw_table_definitions} fuzzy (JSON-only sub-tables often have designer metadata only).
     */
    private MiSubTablePkResult resolveMiSubTablePk(String middleSegment) {
        if (middleSegment == null || middleSegment.isBlank()) {
            return null;
        }
        String token = middleSegment.trim();
        if (!token.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }

        try {
            List<String> pk = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, token);
            return new MiSubTablePkResult(token, pk);
        } catch (Exception ignored) {
        }

        try {
            List<String> names = jdbcTemplate.queryForList(
                    """
                            SELECT table_name FROM information_schema.tables
                            WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
                              AND lower(table_name) = lower(?)
                            LIMIT 1
                            """,
                    String.class,
                    token);
            if (!names.isEmpty()) {
                String physical = names.get(0);
                List<String> pk = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, physical);
                if (!physical.equals(token)) {
                    log.info("[MI] Resolved MI sub-table token '{}' to physical table '{}' (case variant)", token, physical);
                }
                return new MiSubTablePkResult(physical, pk);
            }
        } catch (Exception e) {
            log.debug("[MI] Case-insensitive exact match failed for token={}: {}", token, e.getMessage());
        }

        if (token.length() < 4) {
            log.debug("[MI] Skip fuzzy table search for very short token '{}'", token);
            return null;
        }

        try {
            List<String> names = jdbcTemplate.queryForList(
                    """
                            SELECT table_name FROM information_schema.tables
                            WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
                              AND (
                                lower(table_name) LIKE '%' || lower(?)
                                OR lower(table_name) LIKE lower(?) || '%'
                              )
                            ORDER BY
                              CASE WHEN lower(table_name) = lower(?) THEN 0 ELSE 1 END,
                              length(table_name) ASC
                            LIMIT 24
                            """,
                    String.class,
                    token,
                    token,
                    token);
            for (String physical : names) {
                try {
                    List<String> pk = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, physical);
                    log.info("[MI] Resolved MI sub-table token '{}' to physical table '{}' (fuzzy schema match)", token, physical);
                    return new MiSubTablePkResult(physical, pk);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.debug("[MI] Fuzzy table search failed for token={}: {}", token, e.getMessage());
        }

        if (token.length() >= 4) {
            try {
                List<String> designerNames = jdbcTemplate.query(
                        """
                                SELECT td.table_name
                                FROM dw_table_definitions td
                                WHERE lower(td.table_name) LIKE '%' || lower(?) || '%'
                                   OR lower(?) LIKE '%' || lower(td.table_name) || '%'
                                ORDER BY
                                  CASE WHEN lower(td.table_name) = lower(?) THEN 0 ELSE 1 END,
                                  length(td.table_name) ASC,
                                  td.id DESC
                                LIMIT 24
                                """,
                        (rs, i) -> rs.getString(1),
                        token,
                        token,
                        token);
                Set<String> tried = new HashSet<>();
                for (String designerTable : designerNames) {
                    if (designerTable == null || !tried.add(designerTable.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    try {
                        List<String> pk = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, designerTable);
                        log.info("[MI] Resolved MI token '{}' to designer table '{}' (dw_table_definitions / no physical table required)",
                                token, designerTable);
                        return new MiSubTablePkResult(designerTable, pk);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                log.debug("[MI] Designer metadata fuzzy match failed for token={}: {}", token, e.getMessage());
            }
        }

        return null;
    }

    /**
     * When physical/designer table names do not match: if {@code __subTables__} rows have non-empty {@code id} and assignee, use single-column id as row key (JSON sub-table).
     */
    private MiSubTablePkResult inferMiPkFromJsonSubTables(Map<String, Object> subTables, String assigneeField,
                                                          String roleField, String collectionVariableName) {
        boolean hasAssignee = assigneeField != null && !assigneeField.isBlank();
        boolean hasRole = roleField != null && !roleField.isBlank();
        if (subTables == null || subTables.isEmpty() || (!hasAssignee && !hasRole)) {
            return null;
        }
        List<String> idPk = List.of("id");
        // JSON id 推断只依据「行是否含 id + (assignee 或 role) 资格字段」。
        List<Map<String, Object>> rows = selectRowsForMiCollection(subTables, idPk, assigneeField, roleField);
        if (rows.isEmpty()) {
            log.debug("[MI] JSON id inference found no eligible rows for collection '{}'", collectionVariableName);
            return null;
        }
        log.info("[MI] Inferred PK [id] for '{}': {} eligible JSON sub-table row(s)", collectionVariableName, rows.size());
        return new MiSubTablePkResult("__json_id__", idPk);
    }

    /**
     * Picks the sub-table list most likely for the current MI physical table, avoiding cross-table flattening that explodes instance count.
     * When multiple {@code __subTables__} slices tie (same table, e.g. binding 64 vs 66), merge rows by PK and let later numeric binding keys win field conflicts (Edit on canvas binding must not lose to stale sibling slice).
     */
    private List<Map<String, Object>> selectRowsForMiCollection(Map<String, Object> subTables,
                                                                List<String> pkCols,
                                                                String assigneeField,
                                                                String roleField) {
        if (subTables == null || subTables.isEmpty() || pkCols == null || pkCols.isEmpty()) {
            return List.of();
        }
        record ScoredSlice(String sliceKey, List<Map<String, Object>> rows, int score) {
        }
        List<ScoredSlice> scored = new ArrayList<>();
        for (Map.Entry<String, Object> entry : subTables.entrySet()) {
            if (!(entry.getValue() instanceof List<?> rawList)) {
                continue;
            }
            List<Map<String, Object>> typed = new ArrayList<>();
            for (Object rowObj : rawList) {
                if (rowObj instanceof Map<?, ?> m) {
                    typed.add((Map<String, Object>) m);
                }
            }
            int score = scoreRowsEligibleForMi(typed, pkCols, assigneeField, roleField);
            if (score > 0) {
                scored.add(new ScoredSlice(entry.getKey(), typed, score));
            }
        }
        if (scored.isEmpty()) {
            return List.of();
        }
        int bestScore = scored.stream().mapToInt(ScoredSlice::score).max().orElse(-1);
        if (bestScore <= 0) {
            return List.of();
        }
        List<ScoredSlice> best = scored.stream().filter(s -> s.score == bestScore).toList();
        int bestTotalSize = best.stream().mapToInt(s -> s.rows.size()).min().orElse(Integer.MAX_VALUE);
        best = best.stream().filter(s -> s.rows.size() == bestTotalSize).toList();

        List<ScoredSlice> mergeOrder = best.stream()
                .sorted(Comparator.comparingInt(s -> MiSubTableVariableSupport.parseNumericSubTableSliceKey(s.sliceKey())))
                .toList();

        Map<String, Map<String, Object>> mergedByPk = new LinkedHashMap<>();
        for (ScoredSlice slice : mergeOrder) {
            for (Map<String, Object> row : slice.rows) {
                Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
                if (rowKey == null) {
                    continue;
                }
                if (!rowHasAssigneeOrRole(row, assigneeField, roleField)) {
                    continue;
                }
                String dedup = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rowKey);
                if (dedup.isEmpty()) {
                    continue;
                }
                mergedByPk.merge(dedup, new LinkedHashMap<>(row), MiSubTableVariableSupport::mergeMiCollectionRowPreferIncoming);
            }
        }
        return new ArrayList<>(mergedByPk.values());
    }

    private int scoreRowsEligibleForMi(List<Map<String, Object>> rows, List<String> pkCols,
                                       String assigneeField, String roleField) {
        int n = 0;
        for (Map<String, Object> row : rows) {
            if (SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols) == null) {
                continue;
            }
            if (!rowHasAssigneeOrRole(row, assigneeField, roleField)) {
                continue;
            }
            n++;
        }
        return n;
    }

    /**
     * {@code multiInstance_{subTableName}_collection} → physical sub-table name.
     */
    private static String parseSubTableNameFromMiCollectionVariable(String collectionVariableName) {
        if (collectionVariableName == null
                || !collectionVariableName.startsWith("multiInstance_")
                || !collectionVariableName.endsWith("_collection")) {
            return null;
        }
        return collectionVariableName.substring(
                "multiInstance_".length(),
                collectionVariableName.length() - "_collection".length());
    }

    private void enqueueSequenceFlowTargets(Document document, String flowId, Deque<String> frontier) {
        if (document == null || flowId == null || flowId.isBlank()) {
            return;
        }
        Element sequenceFlow = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "sequenceFlow", flowId);
        if (sequenceFlow == null) {
            sequenceFlow = BpmnMiXmlSupport.findElementByBpmnId(document, flowId);
        }
        if (sequenceFlow == null) {
            log.debug("[MI] sequenceFlow id={} not found", flowId);
            return;
        }
        String targetRef = sequenceFlow.getAttribute("targetRef");
        if (targetRef != null && !targetRef.isBlank()) {
            frontier.add(targetRef);
        }
    }
}
