package com.workflow.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Multi-instance sub-process support: detection, sub-table data injection,
 * write-back on completion, and WebSocket update publishing.
 * Extracted from TaskManagerComponent.
 */
@Slf4j
@Component
@Transactional
public class TaskMultiInstanceService {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Autowired
    private BpmnActionParser bpmnActionParser;

    @Autowired
    private SubTableDataInjector subTableDataInjector;

    @Autowired
    private MultiInstanceDataResolver multiInstanceDataResolver;

    @Autowired(required = false)
    private com.workflow.messaging.SubTableUpdatePublisher updatePublisher;

    // ==================== Public Methods ====================

    /** Check if task is a multi-instance sub-task (multiInstance flag in extendedProperties). */
    boolean isMultiInstanceSubTask(ExtendedTaskInfo extendedTaskInfo) {
        String extendedProperties = extendedTaskInfo.getExtendedProperties();
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = new ObjectMapper().readValue(extendedProperties, Map.class);
            Object multiInstance = props.get("multiInstance");
            return multiInstance != null && Boolean.TRUE.equals(multiInstance);
        } catch (Exception e) {
            log.warn("Failed to parse extendedProperties: taskId={}", extendedTaskInfo.getTaskId(), e);
            return false;
        }
    }

    /** Used by TaskCompletionService.isReturnTargetBeforeMultiInstance to filter active MI tasks. */
    boolean isMultiInstanceTask(ExtendedTaskInfo task) {
        String extendedProperties = task.getExtendedProperties();
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return false;
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> properties = objectMapper.readValue(
                extendedProperties,
                new TypeReference<Map<String, Object>>() {}
            );
            Object multiInstance = properties.get("multiInstance");
            return multiInstance != null && Boolean.TRUE.equals(multiInstance);
        } catch (Exception e) {
            log.warn("Failed to parse extendedProperties: taskId={}", task.getTaskId(), e);
            return false;
        }
    }

    /**
     * Handle data write-back when multi-instance sub-task completes.
     * Supports nested mode (formData/rowVersion keys) and flat mode (portal top-level keys).
     */
    @SuppressWarnings("unchecked")
    void handleMultiInstanceSubTaskCompletion(String taskId, Map<String, Object> variables,
                                              ExtendedTaskInfo extendedTaskInfo) {
        try {
            if (variables == null || variables.isEmpty()) {
                log.warn("Multi-instance sub-task completed but no form data provided: taskId={}", taskId);
                return;
            }

            Object formDataObj = variables.get("formData");
            Object rowVersionObj = variables.get("rowVersion");

            Map<String, Object> formData;
            Long rowVersion;

            if (formDataObj instanceof Map<?, ?>) {
                formData = (Map<String, Object>) formDataObj;
                rowVersion = rowVersionObj instanceof Number n ? n.longValue() : 1L;
            } else {
                Map<String, Object> extProps = parseExtendedProps(extendedTaskInfo);
                String subTableName = extProps.get("subTableName") != null
                        ? String.valueOf(extProps.get("subTableName")) : null;
                Map<String, Object> rowKey = multiInstanceDataResolver.tryResolveSubTableRowKey(subTableName, extProps);

                if (rowKey == null || subTableName == null) {
                    log.warn("Multi-instance sub-task missing subTableRowKey/subTableName, skipping write-back: taskId={}", taskId);
                    return;
                }

                if (!multiInstanceDataResolver.subTableExists(subTableName)
                        && variables.containsKey("__subTables__")) {
                    log.info("Multi-instance sub-task uses variable-type sub-table, skipping physical table write-back: taskId={}, subTableName={}, rowKey={}",
                            taskId, subTableName, rowKey);
                    return;
                }

                Map<String, Object> currentRow = multiInstanceDataResolver.loadSubTableRow(subTableName, rowKey);
                rowVersion = currentRow.get("row_version") instanceof Number n ? n.longValue() : 0L;

                formData = new HashMap<>();
                Set<String> physicalCols = currentRow.keySet();
                for (Map.Entry<String, Object> e : variables.entrySet()) {
                    String k = e.getKey();
                    if (k == null || multiInstanceDataResolver.isSystemVariable(k)) {
                        continue;
                    }
                    if (k.startsWith("multiInstance_")
                            || "__subTables__".equals(k)
                            || "formData".equals(k)
                            || "rowVersion".equals(k)
                            || "subTableName".equals(k)
                            || "foreignKey".equals(k)
                            || "assigneeField".equals(k)
                            || "mainRecordId".equals(k)
                            || "currentItem".equals(k)
                            || "_currentItem".equals(k)) {
                        continue;
                    }
                    String col = multiInstanceDataResolver.resolveSubTablePhysicalColumnKey(subTableName, k, physicalCols);
                    if (col != null) {
                        formData.put(col, e.getValue());
                    }
                }
                log.info("Extracting sub-table column data from variables top-level keys: taskId={}, columns={}, rowVersion={}",
                        taskId, formData.keySet(), rowVersion);
            }

            log.info("Calling MultiInstanceDataResolver to write back data: taskId={}, rowVersion={}",
                taskId, rowVersion);

            multiInstanceDataResolver.writeBackSubTableRow(taskId, formData, rowVersion);

            log.info("Multi-instance sub-task data write-back succeeded: taskId={}", taskId);

            publishMultiInstanceWebSocketUpdate(taskId, extendedTaskInfo);

        } catch (MultiInstanceDataResolver.OptimisticLockException e) {
            log.error("Multi-instance sub-task data write-back failed (optimistic lock conflict): taskId={}", taskId, e);
            throw e;
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Multi-instance sub-task data write-back failed: taskId={}", taskId, e);
            throw new WorkflowBusinessException("MULTI_INSTANCE_DATA_WRITEBACK_ERROR",
                "Multi-instance sub-task data write-back failed: " + e.getMessage(), e);
        }
    }

    /**
     * Detect if next node after current task is a multi-instance sub-process;
     * if so, inject sub-table data into the process instance collection variable.
     */
    void detectAndInjectMultiInstanceData(String processInstanceId,
                                          String processDefinitionId,
                                          String taskDefinitionKey) {
        try {
            log.debug("Detecting if next node is multi-instance sub-process: processInstanceId={}, taskDefinitionKey={}",
                processInstanceId, taskDefinitionKey);

            org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            if (bpmnModel == null) {
                log.warn("Cannot get BPMN model: processDefinitionId={}", processDefinitionId);
                return;
            }

            org.flowable.bpmn.model.FlowElement currentElement = bpmnModel.getFlowElement(taskDefinitionKey);
            if (currentElement == null) {
                log.warn("Cannot find current task node: taskDefinitionKey={}", taskDefinitionKey);
                return;
            }

            if (!(currentElement instanceof org.flowable.bpmn.model.UserTask)) {
                log.debug("Current node is not a UserTask: taskDefinitionKey={}", taskDefinitionKey);
                return;
            }

            org.flowable.bpmn.model.UserTask userTask = (org.flowable.bpmn.model.UserTask) currentElement;

            List<org.flowable.bpmn.model.SequenceFlow> outgoingFlows = userTask.getOutgoingFlows();
            if (outgoingFlows == null || outgoingFlows.isEmpty()) {
                log.debug("Current task has no outgoing flows: taskDefinitionKey={}", taskDefinitionKey);
                return;
            }

            for (org.flowable.bpmn.model.SequenceFlow flow : outgoingFlows) {
                String targetRef = flow.getTargetRef();
                org.flowable.bpmn.model.FlowElement targetElement = bpmnModel.getFlowElement(targetRef);

                if (targetElement instanceof org.flowable.bpmn.model.SubProcess) {
                    org.flowable.bpmn.model.SubProcess subProcess =
                        (org.flowable.bpmn.model.SubProcess) targetElement;

                    org.flowable.bpmn.model.MultiInstanceLoopCharacteristics loopCharacteristics =
                        subProcess.getLoopCharacteristics();

                    if (loopCharacteristics != null) {
                        log.info("Detected multi-instance sub-process: subProcessId={}, processInstanceId={}",
                            subProcess.getId(), processInstanceId);

                        injectMultiInstanceSubTableData(processDefinitionId, processInstanceId, subProcess,
                                loopCharacteristics);

                        return;
                    }
                }
            }

            log.debug("Next node is not multi-instance sub-process: taskDefinitionKey={}", taskDefinitionKey);

        } catch (Exception e) {
            log.error("Failed to detect multi-instance sub-process: processInstanceId={}, taskDefinitionKey={}",
                processInstanceId, taskDefinitionKey, e);
            // Do not throw exception, avoid affecting task completion flow
        }
    }

    // ==================== Private Helpers ====================

    private void publishMultiInstanceWebSocketUpdate(String taskId, ExtendedTaskInfo extendedTaskInfo) {
        if (updatePublisher == null) {
            return;
        }
        try {
            String extendedProperties = extendedTaskInfo.getExtendedProperties();
            if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> props = new ObjectMapper().readValue(extendedProperties, Map.class);

            Map<String, Object> rowKey = multiInstanceDataResolver.tryResolveSubTableRowKey(
                    props.get("subTableName") != null ? String.valueOf(props.get("subTableName")).trim() : null,
                    props);
            Long rowId = toLong(props.get("subTableRowId"));
            if (rowKey == null && rowId == null) {
                log.warn("Cannot parse sub-table row key from extendedProperties: taskId={}", taskId);
                return;
            }

            String processInstanceId = extendedTaskInfo.getProcessInstanceId();
            String mainTaskId = findMainTaskIdForMultiInstance(processInstanceId);

            if (mainTaskId != null) {
                updatePublisher.publishUpdate(mainTaskId, rowId, rowKey, null, "COMPLETED");
                log.debug("WebSocket update notification published: mainTaskId={}, rowId={}, rowKey={}", mainTaskId, rowId, rowKey);
            }

        } catch (Exception e) {
            log.warn("Failed to publish WebSocket update notification: taskId={}", taskId, e);
        }
    }

    private String findMainTaskIdForMultiInstance(String processInstanceId) {
        try {
            List<HistoricActivityInstance> activities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime()
                .desc()
                .list();

            for (HistoricActivityInstance activity : activities) {
                String taskId = activity.getTaskId();
                if (taskId != null) {
                    Optional<ExtendedTaskInfo> extInfoOpt = extendedTaskInfoRepository
                        .findByTaskIdAndIsDeletedFalse(taskId);

                    if (extInfoOpt.isPresent()) {
                        ExtendedTaskInfo extInfo = extInfoOpt.get();
                        if (!isMultiInstanceSubTask(extInfo)) {
                            return taskId;
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to find main task ID: processInstanceId={}", processInstanceId, e);
            return null;
        }
    }

    private String resolveMultiInstanceCollectionVariableName(
            org.flowable.bpmn.model.MultiInstanceLoopCharacteristics loopCharacteristics) {
        if (loopCharacteristics == null) {
            return null;
        }
        String fromInput = trimToNull(loopCharacteristics.getInputDataItem());
        if (fromInput != null) {
            return fromInput;
        }
        Map<String, List<org.flowable.bpmn.model.ExtensionElement>> extensionElements =
                loopCharacteristics.getExtensionElements();
        if (extensionElements != null) {
            List<org.flowable.bpmn.model.ExtensionElement> collectionElements =
                    extensionElements.get("collection");
            if (collectionElements != null && !collectionElements.isEmpty()) {
                String text = trimToNull(collectionElements.get(0).getElementText());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void injectMultiInstanceSubTableData(String processDefinitionId,
                                                 String processInstanceId,
                                                 org.flowable.bpmn.model.SubProcess subProcess,
                                                 org.flowable.bpmn.model.MultiInstanceLoopCharacteristics loopCharacteristics) {
        try {
            String collectionVariableName = resolveMultiInstanceCollectionVariableName(loopCharacteristics);

            if (!StringUtils.hasText(collectionVariableName)) {
                log.warn("Multi-instance sub-process missing collection config (no flowable:collection / inputDataItem, and no extensionElements.collection): subProcessId={}",
                        subProcess.getId());
                return;
            }

            log.info("Multi-instance sub-process collection variable name: {}", collectionVariableName.trim());

            List<org.flowable.bpmn.model.FlowElement> flowElements =
                    (List<org.flowable.bpmn.model.FlowElement>) subProcess.getFlowElements();

            for (org.flowable.bpmn.model.FlowElement element : flowElements) {
                if (element instanceof org.flowable.bpmn.model.UserTask miUserTask) {
                    Map<String, Object> modelProps = extractSubTableConfig(miUserTask);
                    MiSubTableExtensionConfig cfg = resolveMiSubTableExtensionConfig(
                            miUserTask, processDefinitionId, modelProps);

                    if (StringUtils.hasText(cfg.subTableName()) && StringUtils.hasText(cfg.assigneeField())) {
                        String subTableName = cfg.subTableName().trim();
                        String assigneeField = cfg.assigneeField().trim();
                        String foreignKeyField = StringUtils.hasText(cfg.foreignKey())
                                ? cfg.foreignKey().trim()
                                : "main_record_id";

                        Long mainRecordId = parseLongFlexible(modelProps != null ? modelProps.get("mainRecordId") : null);
                        if (mainRecordId == null) {
                            mainRecordId = getMainRecordIdFromProcessVariables(processInstanceId);
                        }

                        String collectionVarTrimmed = collectionVariableName.trim();

                        try {
                            Object existingCollection = runtimeService.getVariable(processInstanceId, collectionVarTrimmed);
                            if (existingCollection instanceof java.util.Collection<?> ec && !ec.isEmpty()) {
                                log.info("Multi-instance collection '{}' already has {} elements, skipping SubTableDataInjector (JSON / user-portal path)",
                                        collectionVarTrimmed, ec.size());
                                return;
                            }
                        } catch (Exception e) {
                            log.debug("Failed to read multi-instance collection variable {}: {}", collectionVarTrimmed, e.getMessage());
                        }

                        if (!subTableDataInjector.physicalTableExistsInCurrentSchema(subTableName)) {
                            log.warn(
                                "Schema has no physical table '{}' and multi-instance collection '{}' is empty or not set; skip JDBC injection."
                                        + " For pure JSON sub-tables, write the collection variable before completing the predecessor task in portal (see TaskProcessComponent.injectMiCollectionFromBpmn).",
                                subTableName, collectionVarTrimmed);
                            return;
                        }

                        log.info("Preparing to inject sub-table data: subTableName={}, assigneeField={}, collectionVar={}",
                                subTableName, assigneeField, collectionVarTrimmed);

                        subTableDataInjector.injectSubTableData(
                                processInstanceId,
                                subTableName,
                                foreignKeyField,
                                mainRecordId,
                                assigneeField,
                                collectionVarTrimmed
                        );

                        log.info("Sub-table data injection succeeded: processInstanceId={}, subTableName={}",
                                processInstanceId, subTableName);

                        return;
                    }
                }
            }

            log.warn("No sub-table config found in multi-instance sub-process: subProcessId={}", subProcess.getId());

        } catch (Exception e) {
            log.error("Failed to inject multi-instance sub-table data: processInstanceId={}, subProcessId={}",
                    processInstanceId, subProcess.getId(), e);
            throw new WorkflowBusinessException("MULTI_INSTANCE_DATA_INJECTION_ERROR",
                    "Failed to inject multi-instance sub-table data: " + e.getMessage(), e);
        }
    }

    private record MiSubTableExtensionConfig(String subTableName, String assigneeField, String foreignKey) {}

    private MiSubTableExtensionConfig resolveMiSubTableExtensionConfig(
            org.flowable.bpmn.model.UserTask userTask,
            String processDefinitionId,
            Map<String, Object> modelProps) {
        Map<String, Object> fromModel = modelProps != null ? modelProps : Collections.emptyMap();

        String subTableName = mapStringValue(fromModel, "subTableName");
        String assigneeField = mapStringValue(fromModel, "assigneeField");
        String foreignKey = mapStringValue(fromModel, "foreignKey");
        if (!StringUtils.hasText(foreignKey)) {
            foreignKey = mapStringValue(fromModel, "foreignKeyField");
        }

        String utId = userTask.getId();
        if (bpmnActionParser != null
                && StringUtils.hasText(processDefinitionId)
                && StringUtils.hasText(utId)) {
            if (!StringUtils.hasText(subTableName)) {
                subTableName = trimToNull(
                        bpmnActionParser.getUserTaskExtensionPropertyValue(
                                processDefinitionId, utId, "subTableName"));
            }
            if (!StringUtils.hasText(assigneeField)) {
                assigneeField = trimToNull(
                        bpmnActionParser.getUserTaskExtensionPropertyValue(
                                processDefinitionId, utId, "assigneeField"));
            }
            if (!StringUtils.hasText(foreignKey)) {
                foreignKey = trimToNull(
                        bpmnActionParser.getUserTaskExtensionPropertyValue(
                                processDefinitionId, utId, "foreignKey"));
            }
            if (!StringUtils.hasText(foreignKey)) {
                foreignKey = trimToNull(
                        bpmnActionParser.getUserTaskExtensionPropertyValue(
                                processDefinitionId, utId, "foreignKeyField"));
            }
        }

        return new MiSubTableExtensionConfig(subTableName, assigneeField, foreignKey);
    }

    private Map<String, Object> extractSubTableConfig(org.flowable.bpmn.model.UserTask userTask) {
        Map<String, Object> config = new HashMap<>();

        Map<String, List<org.flowable.bpmn.model.ExtensionElement>> extensionElements =
            userTask.getExtensionElements();

        if (extensionElements == null || extensionElements.isEmpty()) {
            return config;
        }

        List<org.flowable.bpmn.model.ExtensionElement> propertiesElements =
            extensionElements.get("properties");

        if (propertiesElements == null || propertiesElements.isEmpty()) {
            return config;
        }

        for (org.flowable.bpmn.model.ExtensionElement propertiesElement : propertiesElements) {
            List<org.flowable.bpmn.model.ExtensionElement> propertyElements =
                propertiesElement.getChildElements().get("property");

            if (propertyElements != null) {
                for (org.flowable.bpmn.model.ExtensionElement propertyElement : propertyElements) {
                    String name = propertyElement.getAttributeValue(null, "name");
                    String value = propertyElement.getAttributeValue(null, "value");

                    if (name != null && value != null) {
                        config.put(name, value);
                    }
                }
            }
        }

        return config;
    }

    private Long getMainRecordIdFromProcessVariables(String processInstanceId) {
        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);

            Object mainRecordIdObj = variables.get("mainRecordId");
            if (mainRecordIdObj != null) {
                return ((Number) mainRecordIdObj).longValue();
            }

            org.flowable.engine.runtime.ProcessInstance processInstance =
                runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (processInstance != null && processInstance.getBusinessKey() != null) {
                try {
                    return Long.parseLong(processInstance.getBusinessKey());
                } catch (NumberFormatException e) {
                    log.warn("Cannot convert businessKey to Long: {}", processInstance.getBusinessKey());
                }
            }

            log.warn("Cannot get main table record ID from process variables: processInstanceId={}", processInstanceId);
            return null;

        } catch (Exception e) {
            log.error("Failed to get main table record ID: processInstanceId={}", processInstanceId, e);
            return null;
        }
    }

    private Map<String, Object> parseExtendedProps(ExtendedTaskInfo extendedTaskInfo) {
        String json = extendedTaskInfo.getExtendedProperties();
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse extendedProperties: taskId={}", extendedTaskInfo.getTaskId(), e);
            return Collections.emptyMap();
        }
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String mapStringValue(Map<String, Object> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return null;
        }
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        return trimToNull(String.valueOf(v));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Long parseLongFlexible(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
