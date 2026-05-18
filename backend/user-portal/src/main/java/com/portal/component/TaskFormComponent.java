package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.*;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.platform.common.util.ApiResponseBodyUnwrap;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Task Form 组件
 * 负责 Task Form 数据的获取、提交、快照捕获和已完成任务数据查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskFormComponent {

    private final ProcessFormComponent processFormComponent;
    private final ChangeHistoryComponent changeHistoryComponent;
    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager platformTransactionManager;

    private volatile TransactionTemplate taskFormWriteTxTemplate;

    /** Single write txn for task-form persistence — avoids UnexpectedRollbackException vs nested listeners/history. */
    private TransactionTemplate taskFormWriteTx() {
        TransactionTemplate t = taskFormWriteTxTemplate;
        if (t == null) {
            synchronized (this) {
                t = taskFormWriteTxTemplate;
                if (t == null) {
                    t = new TransactionTemplate(platformTransactionManager);
                    taskFormWriteTxTemplate = t;
                }
            }
        }
        return t;
    }

    /** Lazy: merges physical relation-table rows into task-form variable payloads without widening ctor for tests. */
    @Lazy
    @Autowired
    private ProcessComponent processComponent;

    @Value("${developer-workstation.url:http://localhost:8091}")
    private String developerWorkstationUrl;

    /**
     * 获取 Task Form 布局 + 当前流程变量值（字段子集）
     * 根据 taskDefinitionKey 查找 FormStageBinding → 获取 Task Form 布局
     * 无绑定时返回 Process Form 只读数据（fallback）
     *
     * @param taskId 任务实例 ID
     * @return TaskFormData DTO
     */
    public TaskFormData getTaskFormData(String taskId) {
        log.debug("Getting task form data for task: {}", taskId);

        // Get task info (taskDefinitionKey, processInstanceId) from ProcessInstance
        // TODO: In production, get task info from Flowable TaskService
        TaskInfo taskInfo = getTaskInfo(taskId);

        // Find FormStageBinding by taskDefinitionKey
        Map<String, Object> formDefinition = fetchTaskFormByStageId(taskInfo.taskDefinitionKey);

        // Get process instance for variables
        ProcessInstance processInstance = processInstanceRepository.findById(taskInfo.processInstanceId)
                .orElseThrow(() -> new PortalException("404",
                        "Process instance not found: " + taskInfo.processInstanceId));

        Map<String, Object> allVariables = processInstance.getVariables() != null
                ? processInstance.getVariables()
                : Collections.emptyMap();
        Map<String, Object> hydratedVariables = new HashMap<>(allVariables);
        if (processComponent != null) {
            processComponent.enrichSubTablesVariablesFromPhysicalTables(taskInfo.processInstanceId, hydratedVariables);
        }

        // Get Process Form reference data
        ProcessFormData processFormRef = processFormComponent.getProcessFormData(taskInfo.processInstanceId);

        if (formDefinition == null) {
            // Fallback: no Task Form binding, return only ProcessFormData in read-only mode
            log.info("No Task Form binding found for stage '{}', falling back to Process Form",
                    taskInfo.taskDefinitionKey);
            return TaskFormData.builder()
                    .taskId(taskId)
                    .taskDefinitionKey(taskInfo.taskDefinitionKey)
                    .formName(null)
                    .configJson(null)
                    .fieldPermissions(null)
                    .fieldValues(null)
                    .subTableBindings(null)
                    .processFormRef(processFormRef)
                    .build();
        }

        // Extract Task Form layout and field permissions
        Map<String, Object> configJson = extractMapField(formDefinition, "configJson");
        Map<String, String> fieldPermissions = extractFieldPermissions(formDefinition);
        String formName = formDefinition.get("formName") != null
                ? (String) formDefinition.get("formName")
                : "Task Form";
        Boolean formReadOnly = formDefinition.get("readOnly") instanceof Boolean
                ? (Boolean) formDefinition.get("readOnly")
                : false;

        // Get field values from process variables (subset based on fieldPermissions keys)
        Map<String, Object> fieldValues = extractFieldSubset(hydratedVariables, fieldPermissions.keySet());
        // Mirror persistTaskFormSnapshot: always attach live __subTables__ when present so nested /
        // copied-task bindings hydrate even if fieldPermissions omits or carries a stale __subTables__ entry.
        if (hydratedVariables.containsKey("__subTables__")) {
            fieldValues.put("__subTables__", hydratedVariables.get("__subTables__"));
        }

        return TaskFormData.builder()
                .taskId(taskId)
                .taskDefinitionKey(taskInfo.taskDefinitionKey)
                .formName(formName)
                .configJson(configJson)
                .fieldPermissions(fieldPermissions)
                .fieldValues(fieldValues)
                .subTableBindings(Collections.emptyList())
                .processFormRef(processFormRef)
                .formReadOnly(formReadOnly)
                .build();
    }

    /**
     * 提交 Task Form 更新（仅可编辑字段）
     * 过滤只读字段，仅更新 EDITABLE 字段的流程变量
     *
     * @param taskId   任务实例 ID
     * @param userId   操作用户 ID
     * @param formData 表单数据（可能包含只读字段，会被过滤）
     */
    public void submitTaskForm(String taskId, String userId, Map<String, Object> formData) {
        submitTaskForm(taskId, userId, formData, null);
    }

    /**
     * 提交 Task Form 更新（仅可编辑字段），支持并发修改检测
     *
     * @param taskId         任务实例 ID
     * @param userId         操作用户 ID
     * @param formData       表单数据（可能包含只读字段，会被过滤）
     * @param baselineValues 基准值（前端加载时的字段快照），用于并发检测；null 表示不检测
     */
    public void submitTaskForm(String taskId, String userId, Map<String, Object> formData,
                               Map<String, Object> baselineValues) {
        log.info("Submitting task form for task: {}, user: {}", taskId, userId);

        TaskInfo taskInfo = getTaskInfo(taskId);

        // Get field permissions to filter out READONLY fields
        Map<String, Object> formDefinition = fetchTaskFormByStageId(taskInfo.taskDefinitionKey);
        Map<String, String> fieldPermissions = formDefinition != null
                ? extractFieldPermissions(formDefinition)
                : Collections.emptyMap();

        // Filter: only accept EDITABLE fields
        Map<String, Object> editableData = filterEditableFields(formData, fieldPermissions);

        if (editableData.isEmpty()) {
            log.debug("No editable fields to update for task: {}", taskId);
            return;
        }

        AtomicReference<Map<String, Object>> snapshotOldVarsRef = new AtomicReference<>();
        AtomicReference<Set<String>> concurrentFieldsRef = new AtomicReference<>(Set.of());

        taskFormWriteTx().executeWithoutResult(status -> {
            ProcessInstance processInstance = processInstanceRepository.findById(taskInfo.processInstanceId)
                    .orElseThrow(() -> new PortalException("404",
                            "Process instance not found: " + taskInfo.processInstanceId));

            Map<String, Object> currentVariables = processInstance.getVariables() != null
                    ? new HashMap<>(processInstance.getVariables())
                    : new HashMap<>();

            Set<String> concurrentFields = detectConcurrentModifications(
                    baselineValues, currentVariables, editableData.keySet());

            if (!concurrentFields.isEmpty()) {
                log.warn("Concurrent modification detected on process {}, task {}, fields: {}, user: {}",
                        taskInfo.processInstanceId, taskId, concurrentFields, userId);
            }

            snapshotOldVarsRef.set(new HashMap<>(currentVariables));
            concurrentFieldsRef.set(Set.copyOf(concurrentFields));

            Map<String, Object> updatedVariables = new HashMap<>(currentVariables);
            updatedVariables.putAll(editableData);
            processInstance.setVariables(updatedVariables);
            processInstanceRepository.save(processInstance);

            log.info("Process variables updated for task: {}, fields: {}", taskId, editableData.keySet());
        });

        /*
         * Change history runs after the write TransactionTemplate commits so failures cannot mark it rollback-only.
         */
        ChangeHistoryContext context = ChangeHistoryContext.builder()
                .processInstanceId(taskInfo.processInstanceId)
                .taskInstanceId(taskId)
                .stageId(taskInfo.taskDefinitionKey)
                .userId(userId)
                .build();

        Map<String, Object> snapshotOldVars = snapshotOldVarsRef.get();
        if (snapshotOldVars == null) {
            snapshotOldVars = Collections.emptyMap();
        }
        Set<String> concurrentSnapshot = concurrentFieldsRef.get();
        if (concurrentSnapshot == null) {
            concurrentSnapshot = Set.of();
        }

        try {
            for (String field : concurrentSnapshot) {
                changeHistoryComponent.recordConcurrentModificationWarning(
                        taskInfo.processInstanceId, field, "unknown", userId);
            }
            changeHistoryComponent.recordFieldChanges(context, snapshotOldVars, editableData);
            recordSubTableChangeHistory(context,
                    snapshotOldVars.get("__subTables__"),
                    editableData.get("__subTables__"));
        } catch (RuntimeException ex) {
            log.warn("task form change-history skipped for task {}: {}", taskId, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void recordSubTableChangeHistory(ChangeHistoryContext context,
                                              Object oldSubTablesObj,
                                              Object newSubTablesObj) {
        if (newSubTablesObj == null) {
            return;
        }
        try {
            Map<String, Object> oldMap = oldSubTablesObj instanceof Map
                    ? (Map<String, Object>) oldSubTablesObj
                    : Collections.emptyMap();
            Map<String, Object> newMap = (Map<String, Object>) newSubTablesObj;

            for (Map.Entry<String, Object> subTableEntry : newMap.entrySet()) {
                String subTableKey = subTableEntry.getKey();
                List<Map<String, Object>> newRows = subTableEntry.getValue() instanceof List
                        ? (List<Map<String, Object>>) subTableEntry.getValue()
                        : Collections.emptyList();
                List<Map<String, Object>> oldRows = oldMap.get(subTableKey) instanceof List
                        ? (List<Map<String, Object>>) oldMap.get(subTableKey)
                        : Collections.emptyList();

                List<SubTableChange> changes = computeSubTableRowChanges(oldRows, newRows);
                if (!changes.isEmpty()) {
                    changeHistoryComponent.recordSubTableChanges(
                            context, subTableKey, changes);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to record sub-table changes for process {}: {}",
                    context.getProcessInstanceId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<SubTableChange> computeSubTableRowChanges(
            List<Map<String, Object>> oldRows,
            List<Map<String, Object>> newRows) {
        List<SubTableChange> changes = new ArrayList<>();

        // Build row lookup maps by row id
        Map<Object, Map<String, Object>> oldRowMap = new HashMap<>();
        for (Map<String, Object> row : oldRows) {
            Object rowId = row.get("id");
            if (rowId != null) {
                oldRowMap.put(rowId, row);
            }
        }
        Map<Object, Map<String, Object>> newRowMap = new HashMap<>();
        for (Map<String, Object> row : newRows) {
            Object rowId = row.get("id");
            if (rowId != null) {
                newRowMap.put(rowId, row);
            }
        }

        // Detect ROW_ADD (in new but not in old)
        for (Map.Entry<Object, Map<String, Object>> entry : newRowMap.entrySet()) {
            Object rowId = entry.getKey();
            if (!oldRowMap.containsKey(rowId)) {
                changes.add(SubTableChange.builder()
                        .changeType("ROW_ADD")
                        .rowIdentifier(String.valueOf(rowId))
                        .oldValues(null)
                        .newValues(entry.getValue())
                        .build());
            }
        }

        // Detect ROW_DELETE (in old but not in new)
        for (Map.Entry<Object, Map<String, Object>> entry : oldRowMap.entrySet()) {
            Object rowId = entry.getKey();
            if (!newRowMap.containsKey(rowId)) {
                changes.add(SubTableChange.builder()
                        .changeType("ROW_DELETE")
                        .rowIdentifier(String.valueOf(rowId))
                        .oldValues(entry.getValue())
                        .newValues(null)
                        .build());
            }
        }

        // Detect ROW_UPDATE (in both but field values differ)
        for (Map.Entry<Object, Map<String, Object>> entry : newRowMap.entrySet()) {
            Object rowId = entry.getKey();
            Map<String, Object> oldRow = oldRowMap.get(rowId);
            if (oldRow != null) {
                Map<String, Object> newRow = entry.getValue();
                Map<String, Object> changedFields = new HashMap<>();
                Map<String, Object> oldChangedFields = new HashMap<>();
                boolean hasChanges = false;
                // Compare all fields except 'id' (the row key)
                for (Map.Entry<String, Object> field : newRow.entrySet()) {
                    if ("id".equals(field.getKey())) continue;
                    Object oldFieldVal = oldRow.get(field.getKey());
                    if (!Objects.equals(oldFieldVal, field.getValue())) {
                        changedFields.put(field.getKey(), field.getValue());
                        oldChangedFields.put(field.getKey(), oldFieldVal);
                        hasChanges = true;
                    }
                }
                if (hasChanges) {
                    changes.add(SubTableChange.builder()
                            .changeType("ROW_UPDATE")
                            .rowIdentifier(String.valueOf(rowId))
                            .oldValues(oldChangedFields)
                            .newValues(changedFields)
                            .build());
                }
            }
        }

        return changes;
    }

    /**
     * 检测并发修改：对比基准值与当前流程变量值。
     * 如果某个字段的当前值 != 基准值，说明在用户编辑期间被其他用户修改了。
     *
     * @param baselineValues 前端加载时的字段快照（可为 null）
     * @param currentVariables 当前流程变量值
     * @param submittedFieldNames 本次提交的字段名集合
     * @return 被并发修改的字段名集合
     */
    public Set<String> detectConcurrentModifications(Map<String, Object> baselineValues,
                                                      Map<String, Object> currentVariables,
                                                      Set<String> submittedFieldNames) {
        Set<String> concurrentFields = new java.util.HashSet<>();

        if (baselineValues == null || baselineValues.isEmpty()) {
            return concurrentFields;
        }

        for (String fieldName : submittedFieldNames) {
            if (baselineValues.containsKey(fieldName)) {
                Object baselineVal = baselineValues.get(fieldName);
                Object currentVal = currentVariables.get(fieldName);
                if (!Objects.equals(baselineVal, currentVal)) {
                    concurrentFields.add(fieldName);
                }
            }
        }

        return concurrentFields;
    }

    /**
     * 获取已完成 Task 的快照 + 实时值
     *
     * @param taskId 任务实例 ID
     * @return CompletedTaskFormData DTO
     */
    public CompletedTaskFormData getCompletedTaskFormData(String taskId) {
        log.debug("Getting completed task form data for task: {}", taskId);

        TaskInfo taskInfo = getTaskInfo(taskId);

        ProcessInstance processInstance = processInstanceRepository.findById(taskInfo.processInstanceId)
                .orElseThrow(() -> new PortalException("404",
                        "Process instance not found: " + taskInfo.processInstanceId));

        Map<String, Object> allVariables = processInstance.getVariables() != null
                ? processInstance.getVariables()
                : Collections.emptyMap();

        // Get snapshot from process variable _snapshot_{taskId}
        String snapshotKey = "_snapshot_" + taskId;
        TaskFormSnapshot snapshot = extractSnapshot(allVariables, snapshotKey);

        // Get current live values from process variables
        Map<String, Object> liveValues;
        if (snapshot != null && snapshot.getFieldValues() != null) {
            // Get live values for the same field subset as the snapshot
            liveValues = extractFieldSubset(allVariables, snapshot.getFieldValues().keySet());
        } else {
            liveValues = Collections.emptyMap();
        }

        // Get showLiveValues config from form definition
        boolean showLiveValues = true;
        Map<String, Object> formDefinition = fetchTaskFormByStageId(taskInfo.taskDefinitionKey);
        if (formDefinition != null && formDefinition.containsKey("showLiveValues")) {
            Object slv = formDefinition.get("showLiveValues");
            if (slv instanceof Boolean) {
                showLiveValues = (Boolean) slv;
            }
        }

        // Get Process Form reference data
        ProcessFormData processFormRef = processFormComponent.getProcessFormData(taskInfo.processInstanceId);

        return CompletedTaskFormData.builder()
                .snapshot(snapshot)
                .liveValues(liveValues)
                .showLiveValues(showLiveValues)
                .processFormRef(processFormRef)
                .build();
    }

    /**
     * 在完成审批写入前，把 Task Form 字段子集快照以 {@code _snapshot_{taskId}} 并入流程变量。
     * <p>由调用方对 {@link ProcessInstance} 只做<strong>一次</strong> {@code save}，避免与 {@link ProcessInstance#lockVersion}
     * 乐观锁冲突（接连两次 UPDATE 同一行易导致 UnexpectedRollback）。</p>
     *
     * @param mergedVariables 已合并的流程变量 map（会被原地写入快照键）
     * @return 快照中包含的表单字段名集合（用于日志）
     */
    public Set<String> mergeCompletedTaskSnapshotIntoVariables(String taskId, String userId, String taskDefinitionKey,
                                                               Map<String, Object> mergedVariables) {
        if (mergedVariables == null || taskId == null || taskDefinitionKey == null) {
            return Set.of();
        }
        Map<String, Object> formDefinition = fetchTaskFormByStageId(taskDefinitionKey);
        Map<String, String> fieldPermissions = formDefinition != null
                ? extractFieldPermissions(formDefinition)
                : Collections.emptyMap();

        Map<String, Object> fieldValues = extractFieldSubset(mergedVariables, fieldPermissions.keySet());
        if (mergedVariables.containsKey("__subTables__")) {
            fieldValues.put("__subTables__", mergedVariables.get("__subTables__"));
        }

        TaskFormSnapshot snapshot = TaskFormSnapshot.builder()
                .taskId(taskId)
                .taskDefinitionKey(taskDefinitionKey)
                .assignee(userId)
                .completedAt(Instant.now())
                .fieldValues(fieldValues)
                .build();

        mergedVariables.put("_snapshot_" + taskId, snapshotToMap(snapshot));
        log.debug("Merged snapshot keys into variables for task {}, fields {}", taskId, fieldValues.keySet());
        return Set.copyOf(fieldValues.keySet());
    }

    /**
     * Task 完成时捕获快照
     * 获取当前流程变量值（Task Form 字段子集），存储为 _snapshot_{taskId}
     *
     * @param taskId 任务实例 ID
     * @param userId 操作用户 ID（assignee）
     */
    @Transactional
    public void captureTaskFormSnapshot(String taskId, String userId) {
        log.info("Capturing task form snapshot for task: {}, user: {}", taskId, userId);

        TaskInfo taskInfo = getTaskInfo(taskId);
        persistTaskFormSnapshot(taskId, userId, taskInfo.taskDefinitionKey, taskInfo.processInstanceId, Collections.emptyMap());
    }

    /**
     * Task 完成后捕获快照。
     * <p>任务完成后 Flowable 运行时任务已不存在，调用方需要传入完成前拿到的 stage/process 信息。</p>
     */
    @Transactional
    public void captureTaskFormSnapshot(String taskId, String userId, String taskDefinitionKey,
                                        String processInstanceId, Map<String, Object> completedVariables) {
        log.info("Capturing completed task form snapshot for task: {}, stage: {}", taskId, taskDefinitionKey);

        persistTaskFormSnapshot(taskId, userId, taskDefinitionKey, processInstanceId,
                completedVariables != null ? completedVariables : Collections.emptyMap());
    }

    private void persistTaskFormSnapshot(String taskId, String userId, String taskDefinitionKey,
                                         String processInstanceId, Map<String, Object> completedVariables) {
        ProcessInstance processInstance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new PortalException("404",
                        "Process instance not found: " + processInstanceId));

        Map<String, Object> merged = new HashMap<>();
        if (processInstance.getVariables() != null) {
            merged.putAll(processInstance.getVariables());
        }
        merged.putAll(completedVariables != null ? completedVariables : Collections.emptyMap());

        Set<String> snapshotFieldKeys = mergeCompletedTaskSnapshotIntoVariables(taskId, userId, taskDefinitionKey, merged);
        processInstance.setVariables(merged);
        processInstanceRepository.save(processInstance);

        log.info("Task form snapshot captured for task: {}, fields: {}", taskId, snapshotFieldKeys);
    }

    // ==================== Public utility methods for testing ====================

    /**
     * 过滤只读字段，仅保留 EDITABLE 字段
     * 如果 fieldPermissions 为空，则接受所有字段（向后兼容）
     */
    public Map<String, Object> filterEditableFields(Map<String, Object> formData,
                                                     Map<String, String> fieldPermissions) {
        if (fieldPermissions == null || fieldPermissions.isEmpty()) {
            return new HashMap<>(formData);
        }

        return formData.entrySet().stream()
                .filter(entry -> "__subTables__".equals(entry.getKey())
                        || "EDITABLE".equals(fieldPermissions.get(entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 从全量流程变量中提取字段子集
     */
    public Map<String, Object> extractFieldSubset(Map<String, Object> allVariables,
                                                   Set<String> fieldNames) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return new HashMap<>(allVariables);
        }

        Map<String, Object> subset = new HashMap<>();
        for (String fieldName : fieldNames) {
            if (allVariables.containsKey(fieldName)) {
                subset.put(fieldName, allVariables.get(fieldName));
            }
        }
        return subset;
    }

    /**
     * 计算快照与实时值之间的差异字段数
     */
    public int countSnapshotDiffs(Map<String, Object> snapshotValues, Map<String, Object> liveValues) {
        if (snapshotValues == null || liveValues == null) {
            return 0;
        }

        int diffCount = 0;
        for (Map.Entry<String, Object> entry : snapshotValues.entrySet()) {
            Object snapshotVal = entry.getValue();
            Object liveVal = liveValues.get(entry.getKey());
            if (!Objects.equals(snapshotVal, liveVal)) {
                diffCount++;
            }
        }
        return diffCount;
    }

    /**
     * 将快照 DTO 转换为 Map（用于存储到流程变量）
     */
    public Map<String, Object> snapshotToMap(TaskFormSnapshot snapshot) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", snapshot.getTaskId());
        map.put("taskDefinitionKey", snapshot.getTaskDefinitionKey());
        map.put("assignee", snapshot.getAssignee());
        map.put("completedAt", snapshot.getCompletedAt() != null
                ? snapshot.getCompletedAt().toString() : null);
        map.put("fieldValues", snapshot.getFieldValues());
        return map;
    }

    /**
     * 从 Map 还原快照 DTO（从流程变量读取）
     */
    @SuppressWarnings("unchecked")
    public TaskFormSnapshot mapToSnapshot(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return TaskFormSnapshot.builder()
                .taskId((String) map.get("taskId"))
                .taskDefinitionKey((String) map.get("taskDefinitionKey"))
                .assignee((String) map.get("assignee"))
                .completedAt(map.get("completedAt") != null
                        ? Instant.parse((String) map.get("completedAt")) : null)
                .fieldValues(map.get("fieldValues") instanceof Map
                        ? (Map<String, Object>) map.get("fieldValues") : Collections.emptyMap())
                .build();
    }

    // ==================== Private Helper Methods ====================

    /**
     * 获取任务信息（taskDefinitionKey, processInstanceId）
     * 通过 WorkflowEngineClient 从 Flowable 获取任务详情
     */
    @SuppressWarnings("unchecked")
    protected TaskInfo getTaskInfo(String taskId) {
        if (workflowEngineClient.isAvailable()) {
            Optional<Map<String, Object>> result = workflowEngineClient.getTaskById(taskId);
            if (result.isPresent()) {
                Map<String, Object> body = result.get();
                Map<String, Object> data = body.containsKey("data") 
                        ? (Map<String, Object>) body.get("data") : body;
                
                String taskDefinitionKey = (String) data.get("taskDefinitionKey");
                String processInstanceId = (String) data.get("processInstanceId");
                
                if (taskDefinitionKey != null && processInstanceId != null) {
                    return new TaskInfo(taskDefinitionKey, processInstanceId);
                }
            }
        }
        
        throw new PortalException("404", "Task not found: " + taskId);
    }

    /**
     * 根据 stageId (taskDefinitionKey) 获取 Task Form 定义。
     * 优先请求 developer-workstation；Docker/网络不可达时从本库 {@code dw_form_stage_bindings} 回退（与 DW 共用同一 PostgreSQL）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchTaskFormByStageId(String stageId) {
        Map<String, Object> fromRemote = fetchTaskFormFromDeveloperWorkstation(stageId);
        if (fromRemote != null) {
            return fromRemote;
        }
        Map<String, Object> fromDb = fetchTaskFormFromLocalDw(stageId);
        if (fromDb != null) {
            log.debug("Resolved task form for stage '{}' from local dw_form_stage_bindings", stageId);
        }
        return fromDb;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchTaskFormFromDeveloperWorkstation(String stageId) {
        try {
            String base = normalizeDeveloperWorkstationBase(developerWorkstationUrl);
            String url = UriComponentsBuilder
                    .fromHttpUrl(base + "/api/v1/form-stage-bindings")
                    .queryParam("stageId", stageId)
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();
            log.debug("Fetching Task Form definition from: {}", url);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                return null;
            }
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response);
            if (payload.containsKey("form")) {
                return (Map<String, Object>) payload.get("form");
            }
            if (response.containsKey("form")) {
                return (Map<String, Object>) response.get("form");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Task Form definition for stage {}: {}", stageId, e.getMessage());
        }
        return null;
    }

    /**
     * 与 developer-workstation 共用库表时的本地解析（避免容器内 developer-workstation.url 误配为 localhost 导致始终失败）。
     */
    private Map<String, Object> fetchTaskFormFromLocalDw(String stageId) {
        if (stageId == null || stageId.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query(
                    """
                            SELECT fd.form_name, fd.config_json, fd.field_permissions, b.read_only
                            FROM dw_form_stage_bindings b
                            INNER JOIN dw_form_definitions fd ON fd.id = b.form_id
                            WHERE b.stage_id = ?
                            LIMIT 1
                            """,
                    (ResultSet rs, int rowNum) -> mapRowToTaskFormDefinition(rs),
                    stageId.trim());
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.debug("Local dw_form_stage_bindings lookup failed for stage {}: {}", stageId, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> mapRowToTaskFormDefinition(ResultSet rs) throws SQLException {
        Map<String, Object> form = new HashMap<>();
        form.put("formName", rs.getString("form_name"));
        form.put("configJson", readJsonObjectMap(rs, "config_json"));
        form.put("fieldPermissions", readJsonStringMap(rs, "field_permissions"));
        form.put("readOnly", rs.getBoolean("read_only"));
        return form;
    }

    private Map<String, Object> readJsonObjectMap(ResultSet rs, String col) throws SQLException {
        String raw = rs.getString(col);
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("Could not parse JSON object column {}: {}", col, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, String> readJsonStringMap(ResultSet rs, String col) throws SQLException {
        String raw = rs.getString(col);
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.debug("Could not parse JSON string map column {}: {}", col, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isEmpty()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** 支持 {@code DEVELOPER_WORKSTATION_URL} 仅含 host:port，或误带 {@code /api/v1} 后缀。 */
    private static String normalizeDeveloperWorkstationBase(String url) {
        String b = trimTrailingSlash(url != null ? url : "");
        if (b.endsWith("/api/v1")) {
            return trimTrailingSlash(b.substring(0, b.length() - "/api/v1".length()));
        }
        return b;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractFieldPermissions(Map<String, Object> formDefinition) {
        Object fp = formDefinition.get("fieldPermissions");
        if (fp instanceof Map) {
            Map<String, Object> raw = (Map<String, Object>) fp;
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "READONLY");
            }
            return result;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMapField(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private TaskFormSnapshot extractSnapshot(Map<String, Object> allVariables, String snapshotKey) {
        Object snapshotObj = allVariables.get(snapshotKey);
        if (snapshotObj instanceof Map) {
            return mapToSnapshot((Map<String, Object>) snapshotObj);
        }
        return null;
    }

    // ========== Inner data class ==========

    /**
     * Task 信息（从 Flowable 获取）
     */
    public static class TaskInfo {
        public final String taskDefinitionKey;
        public final String processInstanceId;

        public TaskInfo(String taskDefinitionKey, String processInstanceId) {
            this.taskDefinitionKey = taskDefinitionKey;
            this.processInstanceId = processInstanceId;
        }
    }
}
