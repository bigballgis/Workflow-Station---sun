package com.portal.component;

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
    @org.springframework.beans.factory.annotation.Autowired
    private com.platform.common.functionunit.CatalogSnapshotReader catalogSnapshots;

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
        injectMiCollectionFromBpmn(processDefinitionKey, taskDefinitionKey, processInstanceId, variables, false);
    }

    List<SubTableBatchInitialMiOutcome.Expected> injectMiCollectionFromBpmn(String processDefinitionKey, String taskDefinitionKey,
                                    String processInstanceId, Map<String, Object> variables, Map<String, Object> instanceVariables) {
        if (instanceVariables == null || !instanceVariables.containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD)) {
            throw new com.portal.exception.PortalException("409", "Frozen assignment instance context is missing");
        }
        return injectMiCollectionFromBpmn(processDefinitionKey, taskDefinitionKey, processInstanceId, variables, true, instanceVariables, false);
    }

    List<SubTableBatchInitialMiOutcome.Expected> injectMiCollectionForFirstTask(String processDefinitionKey, String taskDefinitionKey,
                                        String processInstanceId, Map<String, Object> variables, Map<String, Object> instanceVariables) {
        if (instanceVariables == null || !instanceVariables.containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD)) {
            throw new com.portal.exception.PortalException("409", "Frozen assignment instance context is missing");
        }
        return injectMiCollectionFromBpmn(processDefinitionKey, taskDefinitionKey, processInstanceId, variables, true, instanceVariables, true);
    }

    void injectMiCollectionFromBpmn(String processDefinitionKey, String taskDefinitionKey,
                                    String processInstanceId, Map<String, Object> variables, boolean instanceBound) {
        injectMiCollectionFromBpmn(processDefinitionKey, taskDefinitionKey, processInstanceId, variables, instanceBound, null, false);
    }

    private List<SubTableBatchInitialMiOutcome.Expected> injectMiCollectionFromBpmn(String processDefinitionKey, String taskDefinitionKey,
                                            String processInstanceId, Map<String, Object> variables, boolean instanceBound,
                                            Map<String, Object> instanceVariables, boolean preserveUnfrozenPreparedCollections) {
        List<SubTableBatchInitialMiOutcome.Expected> expected = new ArrayList<>();
        try {
            if (processDefinitionKey == null || processDefinitionKey.isBlank()
                    || taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
                if (instanceBound) throw new IllegalStateException("Active MI task source is missing");
                log.warn("[MI] Missing processDefinitionKey or taskDefinitionKey, skip collection injection (procDef={}, taskDef={})",
                        processDefinitionKey, taskDefinitionKey);
                return List.of();
            }
            log.info("[MI] injectMiCollectionFromBpmn begin processDefinitionKey={} taskDefinitionKey={} processInstanceId={}",
                    processDefinitionKey, taskDefinitionKey, processInstanceId);
            // Always the BPMN this instance was deployed with: the latest of its key may be a newer release.
            Optional<String> bpmnOpt = processInstanceId == null || processInstanceId.isBlank()
                    ? Optional.empty()
                    : workflowEngineClient.getBpmnXml(processDefinitionKey, processInstanceId);
            if (bpmnOpt.isEmpty()) {
                if (instanceBound) throw new IllegalStateException("Instance deployed BPMN is unavailable");
                log.warn("[MI] Could not fetch BPMN XML for processDefinitionKey={}", processDefinitionKey);
                return List.of();
            }
            Document document = BpmnMiXmlSupport.parseBpmnSecurely(bpmnOpt.get());

            // 1. Locate current task node (bpmn:userTask / userTask)
            Element taskElement = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "userTask", taskDefinitionKey);
            if (taskElement == null) {
                if (instanceBound) throw new IllegalStateException("Active task node is missing from its deployed BPMN");
                log.warn("[MI] UserTask id={} not found in BPMN (check taskDefinitionKey vs XML). Skip MI injection.",
                        taskDefinitionKey);
                return List.of();
            }

            // The immutable deployed node, not a mutable stage/form lookup, owns this source id.
            String formId = BpmnMiXmlSupport.findFirstPropertyValue(taskElement, "formId");
            Map<String, Object> controls = instanceVariables == null ? Map.of()
                    : SubTableBatchRuntimeSnapshot.projectForForm(instanceVariables, formId,
                            com.platform.common.util.JsonUtils.getObjectMapper());
            SubTableBatchMiCollection.validateTargets(document, controls);
            if (!injectReachableCollection(document, taskElement, taskDefinitionKey, processInstanceId, variables, controls,
                    instanceVariables, preserveUnfrozenPreparedCollections, expected)) {
                log.warn("[MI] No usable reachable multi-instance subProcess found from task {}", taskDefinitionKey);
            }
        } catch (Exception e) {
            if (instanceBound) {
                throw new com.portal.exception.PortalException("409", "Instance MI collection source cannot be verified", e);
            }
            log.warn("[MI] injectMiCollectionFromBpmn failed for processDefinitionKey={}, taskDefinitionKey={}: {}",
                    processDefinitionKey, taskDefinitionKey, e.getMessage());
        }
        return List.copyOf(expected);
    }

    private boolean injectReachableCollection(Document document, Element task, String taskKey, String processInstanceId,
                                              Map<String, Object> variables,
                                              Map<String, Object> controls, Map<String, Object> baseline, boolean preserveUnfrozenPreparedCollections,
                                              List<SubTableBatchInitialMiOutcome.Expected> expected) {
        Deque<String> frontier = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        enqueueOutgoing(document, task, taskKey, frontier);
        while (!frontier.isEmpty()) {
            String nodeId = frontier.poll();
            if (nodeId == null || nodeId.isBlank() || !visited.add(nodeId)) continue;
            // A frozen instance must let the next human task configure its own future participants.
            if (baseline != null && BpmnMiXmlSupport.findElementByLocalNameAndId(document, "userTask", nodeId) != null) continue;
            Element subProcess = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "subProcess", nodeId);
            if (subProcess != null && BpmnMiXmlSupport.findMultiInstanceLoopInSubProcess(subProcess) != null) {
                Map<SubTableBatchRoleEligibility.Target, SubTableBatchRoleEligibility.Pool> pools = new HashMap<>();
                if (SubTableBatchMiCollection.inject(subProcess, variables, controls, baseline,
                        (people, roles) -> {
                            SubTableBatchUserEligibility.requireActive(people, jdbcTemplate);
                            pools.putAll(SubTableBatchRoleEligibility.resolvePools(roles, baseline, jdbcTemplate));
                        })) {
                    String name = BpmnMiXmlSupport.extractFlowableCollection(BpmnMiXmlSupport.findMultiInstanceLoopInSubProcess(subProcess));
                    expected.addAll(SubTableBatchInitialMiOutcome.plan(subProcess, controls, (List<?>) variables.get(name), pools));
                    return true;
                }
                String collection = BpmnMiXmlSupport.extractFlowableCollection(BpmnMiXmlSupport.findMultiInstanceLoopInSubProcess(subProcess));
                // Only the first-task legacy preparation contract keeps a prebuilt non-Batch-Assign collection.
                if (preserveUnfrozenPreparedCollections && collection != null && variables.containsKey(collection)) return true;
                if (injectSubProcess(subProcess, processInstanceId, variables)) return true;
                continue;
            }
            // Preserve existing look-ahead through call activities: the called instance cannot supply this one's collection.
            Element flowNode = subProcess != null ? subProcess : BpmnMiXmlSupport.findElementByBpmnId(document, nodeId);
            if (flowNode != null) enqueueOutgoing(document, flowNode, nodeId, frontier);
        }
        return false;
    }

    private void enqueueOutgoing(Document document, Element node, String nodeId, Deque<String> frontier) {
        List<String> outgoing = BpmnMiXmlSupport.getDirectChildTextValues(node, "outgoing");
        if (outgoing.isEmpty()) outgoing = BpmnMiXmlSupport.listSequenceFlowIdsWithSourceRef(document, nodeId);
        for (String flow : outgoing) enqueueSequenceFlowTargets(document, flow, frontier);
    }

    private boolean injectSubProcess(Element subProcess, String processInstanceId, Map<String, Object> variables) {
        String collection = BpmnMiXmlSupport.extractFlowableCollection(BpmnMiXmlSupport.findMultiInstanceLoopInSubProcess(subProcess));
        if (collection == null || collection.isBlank()) {
            log.warn("[MI] SubProcess {} has no collection configuration", subProcess.getAttribute("id"));
            return false;
        }
        String mode = BpmnMiXmlSupport.extractAssigneeModeFromSubProcess(subProcess);
        boolean roleAllowed = mode == null || "role".equalsIgnoreCase(mode.trim()) || "both".equalsIgnoreCase(mode.trim());
        boolean userAllowed = mode == null || "user".equalsIgnoreCase(mode.trim()) || "both".equalsIgnoreCase(mode.trim());
        String roleField = roleAllowed ? BpmnMiXmlSupport.extractRoleFieldFromSubProcess(subProcess) : null;
        String buField = roleAllowed ? BpmnMiXmlSupport.extractBuFieldFromSubProcess(subProcess) : null;
        String assigneeField = userAllowed ? BpmnMiXmlSupport.extractAssigneeFieldFromSubProcess(subProcess) : null;
        if ((assigneeField == null || assigneeField.isBlank()) && (roleField == null || roleField.isBlank())) {
            log.warn("[MI] SubProcess {} has neither assigneeField nor roleField", subProcess.getAttribute("id"));
            return false;
        }
        buildMiCollectionVariable(variables, processInstanceId, collection, assigneeField,
                BpmnMiXmlSupport.findFirstPropertyValue(subProcess, "subTableName"),
                BpmnMiXmlSupport.extractSubTableIdFromSubProcess(subProcess), roleField, buField);
        return true;
    }

    /**
     * Builds multi-instance collection variable from __subTables__.
     * PK columns are resolved solely from the BPMN-recorded {@code subTableId}, looked up in the release the
     * request was started on; table names are not used for lookup since they are not guaranteed globally unique.
     * <p>
     * __subTables__ often has multiple binding lists; naive flattening treats any row with target PK columns and assignee as an MI element
     * (e.g. multiple sub-tables with column {@code id}) creates far more child tasks than expected after the prerequisite task. Each map value list is scored separately,
     * Uses only the source list that best matches target PK + assignee (merge and dedupe on ties).
     */
    @SuppressWarnings("unchecked")
    private void buildMiCollectionVariable(Map<String, Object> variables, String processInstanceId,
                                          String collectionVariableName,
                                          String assigneeField, String bpmnSubTableName, String bpmnSubTableId,
                                          String roleField, String buField) {
        // 逐行分派：一行只要 assigneeField 或 roleField 任一有值即可纳入 MI（场景 C 混用）。
        boolean hasAssigneeField = assigneeField != null && !assigneeField.isBlank();
        boolean hasRoleField = roleField != null && !roleField.isBlank();
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map)) {
            throw new IllegalStateException(
                    "[MI] No __subTables__ found for collection '" + collectionVariableName + "'");
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;

        // The request's own pin, not variables: completion variables never carry the release id.
        String catalog = pinnedCatalogOf(processInstanceId);
        MiSubTablePkResult pkResult = resolvePublishedPk(catalog, bpmnSubTableId);
        if (pkResult == null || pkResult.pkCols() == null || pkResult.pkCols().isEmpty()) {
            throw new IllegalStateException(
                    "[MI] Cannot resolve primary key for multi-instance collection '" + collectionVariableName
                            + "' (subTableId='" + bpmnSubTableId + "', bpmnSubTableName='" + bpmnSubTableName
                            + "'). BPMN MI node has no valid subTableId — re-save the process design so the "
                            + "designer writes subTableId onto the MI subProcess.");
        }
        List<String> pkCols = pkResult.pkCols();

        Map<String, Object> sources = publishedSources(catalog, bpmnSubTableId, pkResult.resolvedTable(), subTables);
        List<Map<String, Object>> allRows = selectRowsForMiCollection(sources, pkCols, assigneeField, roleField);
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
        log.info("[MI] Built collection '{}' with {} items, assigneeField='{}', roleField='{}', resolvedTable='{}'",
                collectionVariableName, collection.size(), assigneeField, roleField, pkResult.resolvedTable());
    }

    /**
     * Result of resolving MI row identity: the designer's {@code table_name} and its PK columns.
     */
    private record MiSubTablePkResult(String resolvedTable, List<String> pkCols) {
    }

    private com.platform.common.functionunit.CatalogSnapshotReader snapshots() {
        return catalogSnapshots != null ? catalogSnapshots : new com.platform.common.functionunit.CatalogSnapshotReader(
                jdbcTemplate, com.platform.common.util.JsonUtils.getObjectMapper());
    }

    private MiSubTablePkResult resolvePublishedPk(String catalog, String sourceId) {
        var table = snapshots().table(catalog, sourceId);
        var keys = com.platform.common.functionunit.CatalogSnapshotReader.objects(table.get("fields")).stream()
                .filter(field -> Boolean.TRUE.equals(field.get("isPrimaryKey")))
                .map(field -> String.valueOf(field.get("fieldName"))).toList();
        if (keys.isEmpty()) throw com.platform.common.functionunit.CatalogSnapshotReader.invalid(catalog);
        return new MiSubTablePkResult(String.valueOf(table.get("tableName")), keys);
    }

    private Map<String, Object> publishedSources(String catalog, String sourceId, String tableName,
                                                  Map<String, Object> subTables) {
        String canonical = com.platform.common.subtable.SubTableStoreKeys.dwKey(tableName);
        if (subTables.containsKey(canonical)) return java.util.Collections.singletonMap(canonical, subTables.get(canonical));
        var aliases = snapshots().tableAliases(catalog);
        Map<String, Object> selected = new LinkedHashMap<>();
        subTables.forEach((key, rows) -> {
            var table = aliases.get(key);
            if (table != null && sourceId.equals(String.valueOf(table.get("tableId")))) selected.put(key, rows);
        });
        return selected;
    }

    /** The release this request was started on; MI row identity is read from it, never from the live designer. */
    private String pinnedCatalogOf(String processInstanceId) {
        List<String> pins = processInstanceId == null || processInstanceId.isBlank() ? List.of()
                : jdbcTemplate.queryForList("SELECT function_unit_catalog_id FROM up_process_instance WHERE id = ?",
                        String.class, processInstanceId);
        if (pins.size() != 1 || pins.get(0) == null || pins.get(0).isBlank()) {
            throw new com.platform.common.functionunit.FunctionUnitVersionException(
                    "VERSION_RECOVERY_REQUIRED", processInstanceId);
        }
        return pins.get(0);
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
