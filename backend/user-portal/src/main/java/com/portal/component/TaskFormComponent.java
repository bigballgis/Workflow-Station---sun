package com.portal.component;

import com.portal.dto.*;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
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

        // Get field values from process variables (subset based on fieldPermissions keys)
        Map<String, Object> fieldValues = extractFieldSubset(allVariables, fieldPermissions.keySet());

        return TaskFormData.builder()
                .taskId(taskId)
                .taskDefinitionKey(taskInfo.taskDefinitionKey)
                .formName(formName)
                .configJson(configJson)
                .fieldPermissions(fieldPermissions)
                .fieldValues(fieldValues)
                .subTableBindings(Collections.emptyList())
                .processFormRef(processFormRef)
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
    @Transactional
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
    @Transactional
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

        // Get current process variables (old values)
        ProcessInstance processInstance = processInstanceRepository.findById(taskInfo.processInstanceId)
                .orElseThrow(() -> new PortalException("404",
                        "Process instance not found: " + taskInfo.processInstanceId));

        Map<String, Object> currentVariables = processInstance.getVariables() != null
                ? new HashMap<>(processInstance.getVariables())
                : new HashMap<>();

        // Detect concurrent modifications by comparing baseline with current values
        Set<String> concurrentFields = detectConcurrentModifications(
                baselineValues, currentVariables, editableData.keySet());

        if (!concurrentFields.isEmpty()) {
            log.warn("Concurrent modification detected on process {}, task {}, fields: {}, user: {}",
                    taskInfo.processInstanceId, taskId, concurrentFields, userId);

            // Record concurrent modification warnings in Change_History (best-effort)
            for (String field : concurrentFields) {
                changeHistoryComponent.recordConcurrentModificationWarning(
                        taskInfo.processInstanceId, field, "unknown", userId);
            }
        }

        // Update process variables field-by-field (last-write-wins)
        Map<String, Object> updatedVariables = new HashMap<>(currentVariables);
        updatedVariables.putAll(editableData);
        processInstance.setVariables(updatedVariables);
        processInstanceRepository.save(processInstance);

        log.info("Process variables updated for task: {}, fields: {}", taskId, editableData.keySet());

        // Record Change_History via ChangeHistoryComponent (best-effort)
        ChangeHistoryContext context = ChangeHistoryContext.builder()
                .processInstanceId(taskInfo.processInstanceId)
                .taskInstanceId(taskId)
                .stageId(taskInfo.taskDefinitionKey)
                .userId(userId)
                .build();

        changeHistoryComponent.recordFieldChanges(context, currentVariables, editableData);
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

        // Get Task Form field subset
        Map<String, Object> formDefinition = fetchTaskFormByStageId(taskInfo.taskDefinitionKey);
        Map<String, String> fieldPermissions = formDefinition != null
                ? extractFieldPermissions(formDefinition)
                : Collections.emptyMap();

        ProcessInstance processInstance = processInstanceRepository.findById(taskInfo.processInstanceId)
                .orElseThrow(() -> new PortalException("404",
                        "Process instance not found: " + taskInfo.processInstanceId));

        Map<String, Object> allVariables = processInstance.getVariables() != null
                ? processInstance.getVariables()
                : Collections.emptyMap();

        // Get current values for the Task Form's field subset
        Map<String, Object> fieldValues = extractFieldSubset(allVariables, fieldPermissions.keySet());

        // Create TaskFormSnapshot
        TaskFormSnapshot snapshot = TaskFormSnapshot.builder()
                .taskId(taskId)
                .taskDefinitionKey(taskInfo.taskDefinitionKey)
                .assignee(userId)
                .completedAt(Instant.now())
                .fieldValues(fieldValues)
                .build();

        // Store as process variable with key _snapshot_{taskId}
        String snapshotKey = "_snapshot_" + taskId;
        Map<String, Object> updatedVariables = new HashMap<>(
                processInstance.getVariables() != null ? processInstance.getVariables() : Collections.emptyMap());
        updatedVariables.put(snapshotKey, snapshotToMap(snapshot));
        processInstance.setVariables(updatedVariables);
        processInstanceRepository.save(processInstance);

        log.info("Task form snapshot captured for task: {}, fields: {}", taskId, fieldValues.keySet());
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
                .filter(entry -> "EDITABLE".equals(fieldPermissions.get(entry.getKey())))
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
     * TODO: 实际集成时从 Flowable TaskService 获取
     */
    protected TaskInfo getTaskInfo(String taskId) {
        // TODO: Replace with actual Flowable integration
        // org.flowable.task.api.Task task = taskService.createTaskQuery()
        //     .taskId(taskId).singleResult();
        // return new TaskInfo(task.getTaskDefinitionKey(), task.getProcessInstanceId());

        // For now, parse taskId or use a lookup mechanism
        // In tests, this will be overridden or mocked
        throw new PortalException("404", "Task not found: " + taskId
                + " (Flowable integration pending)");
    }

    /**
     * 根据 stageId (taskDefinitionKey) 获取 Task Form 定义
     * TODO: 实际集成时通过 REST 调用 developer-workstation 或使用缓存
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchTaskFormByStageId(String stageId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = developerWorkstationUrl + "/api/v1/form-stage-bindings?stageId=" + stageId;
            log.debug("Fetching Task Form definition from: {}", url);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("form")) {
                return (Map<String, Object>) response.get("form");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Task Form definition for stage {}: {}", stageId, e.getMessage());
        }
        return null;
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
