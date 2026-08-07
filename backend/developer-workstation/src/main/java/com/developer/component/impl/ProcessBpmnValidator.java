package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.TableType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.util.BpmnLastTaskAssigneeTopologyValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPMN XML 解析与校验协作类。
 *
 * <p>从 {@link ProcessDesignComponentImpl} 拆出，负责：基础结构/孤儿节点校验、BPMN 结构解析、
 * 多实例子流程配置校验，以及 LAST_TASK_ASSIGNEE 锚点拓扑校验。逻辑（正则、命名空间、节点结构、
 * 异常码/消息）逐字保留，行为零变化。</p>
 */
@Component
@Slf4j
public class ProcessBpmnValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern FLOW_NODE_ID_PATTERN = Pattern.compile(
            "<bpmn:(startEvent|endEvent|userTask|serviceTask|scriptTask|manualTask|sendTask|receiveTask|"
                    + "businessRuleTask|task|subProcess|exclusiveGateway|parallelGateway|inclusiveGateway|"
                    + "eventBasedGateway|complexGateway|intermediateCatchEvent|intermediateThrowEvent|"
                    + "boundaryEvent|callActivity)\\b[^>]*\\bid=\"([^\"]+)\"",
            Pattern.DOTALL);

    /** 任意命名空间前缀下的流程节点（不含 sequenceFlow：连线本身不构成「图里有东西」）。 */
    private static final Pattern ANY_FLOW_NODE_PATTERN = Pattern.compile(
            "<(?:\\w+:)?(startEvent|endEvent|userTask|serviceTask|scriptTask|manualTask|sendTask|receiveTask|"
                    + "businessRuleTask|task|subProcess|transaction|adHocSubProcess|exclusiveGateway|parallelGateway|"
                    + "inclusiveGateway|eventBasedGateway|complexGateway|intermediateCatchEvent|intermediateThrowEvent|"
                    + "boundaryEvent|callActivity)\\b");

    private static final Pattern BPMN_SHAPE_PATTERN = Pattern.compile("<(?:\\w+:)?BPMNShape\\b");

    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final I18nService i18nService;

    public ProcessBpmnValidator(
            TableDefinitionRepository tableDefinitionRepository,
            FormDefinitionRepository formDefinitionRepository,
            I18nService i18nService) {
        this.tableDefinitionRepository = tableDefinitionRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.i18nService = i18nService;
    }

    /**
     * 「这张图什么都没有」：既无流程节点也无 BPMNDI 图形（null/空白同样视为空）。
     *
     * <p>空图护栏的唯一后端实现点，供 {@link ProcessDesignComponentImpl#save} 判断
     * 「本次保存是否会把已有流程整体抹掉」；前端同一规则见
     * {@code frontend/developer-workstation/src/utils/bpmnDiagramContent.ts}，两处必须同步修改。</p>
     */
    public boolean isEmptyDiagram(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return true;
        }
        return !ANY_FLOW_NODE_PATTERN.matcher(bpmnXml).find()
                && !BPMN_SHAPE_PATTERN.matcher(bpmnXml).find();
    }

    public ValidationResult validate(String bpmnXml) {
        ValidationResult result = new ValidationResult();

        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            result.addError("EMPTY_BPMN", "BPMN XML cannot be empty", null);
            return result;
        }

        // Check for start event
        if (!bpmnXml.contains("startEvent")) {
            result.addError("MISSING_START_EVENT", "Process is missing a start event", null);
        }

        // Check for end event
        if (!bpmnXml.contains("endEvent")) {
            result.addError("MISSING_END_EVENT", "Process is missing an end event", null);
        }

        // Check basic XML structure
        if (!bpmnXml.contains("<bpmn:process") && !bpmnXml.contains("<process")) {
            result.addError("INVALID_BPMN_STRUCTURE", "Invalid BPMN structure", null);
        }

        // Check orphan nodes
        List<String> nodeIds = extractNodeIds(bpmnXml);
        List<String> connectedNodes = extractConnectedNodes(bpmnXml);

        for (String nodeId : nodeIds) {
            if (!connectedNodes.contains(nodeId) && !isStartOrEndEvent(bpmnXml, nodeId)) {
                result.addWarning("ORPHAN_NODE", "Node " + nodeId + " may be orphaned", nodeId);
            }
        }

        ValidationResult lastTaskTopo = validateLastTaskAssigneeTopology(bpmnXml);
        for (ValidationResult.ValidationError e : lastTaskTopo.getErrors()) {
            result.addError(e.getCode(), e.getMessage(), e.getElementId());
        }

        validateSendEmailTasks(bpmnXml, result);

        return result;
    }

    private void validateSendEmailTasks(String bpmnXml, ValidationResult result) {
        Pattern sendTaskPattern = Pattern.compile("<bpmn:sendTask[^>]*id=\"([^\"]+)\"[^>]*>");
        Matcher matcher = sendTaskPattern.matcher(bpmnXml);
        while (matcher.find()) {
            String taskId = matcher.group(1);
            int start = matcher.start();
            int end = bpmnXml.indexOf("</bpmn:sendTask>", start);
            if (end < 0) {
                end = Math.min(start + 2000, bpmnXml.length());
            }
            String block = bpmnXml.substring(start, end);
            String sendMode = extractCustomProperty(block, "sendMode");
            if (sendMode != null && !"email".equalsIgnoreCase(sendMode)) {
                continue;
            }
            String connectionId = extractCustomProperty(block, "connectionId");
            String emailTo = extractCustomProperty(block, "emailTo");
            String emailTemplateId = extractCustomProperty(block, "emailTemplateId");
            if (connectionId == null || connectionId.isBlank()) {
                result.addError("SEND_TASK_MISSING_CONNECTION",
                        i18nService.getMessage("email.send_task.missing_connection"), taskId);
            }
            if (emailTo == null || emailTo.isBlank()) {
                result.addError("SEND_TASK_MISSING_RECIPIENT",
                        i18nService.getMessage("email.send_task.missing_recipient"), taskId);
            }
            if (emailTemplateId == null || emailTemplateId.isBlank()) {
                result.addError("SEND_TASK_MISSING_TEMPLATE",
                        i18nService.getMessage("email.send_task.missing_template"), taskId);
            }
            validateSendEmailAttachments(block, taskId, result);
        }
    }

    /**
     * Attachments (optional) must reference MAIN/lookup upload fields — not legacy Base64 content.
     */
    private void validateSendEmailAttachments(String block, String taskId, ValidationResult result) {
        String raw = extractCustomProperty(block, "emailAttachments");
        if (raw == null || raw.isBlank()) {
            return;
        }
        String json = raw.replace("&quot;", "\"").replace("&#34;", "\"");
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!root.isArray() || !attachmentItemsValid(root)) {
                result.addError("SEND_TASK_INVALID_ATTACHMENTS",
                        i18nService.getMessage("email.send_task.invalid_attachments"), taskId);
            }
        } catch (Exception e) {
            result.addError("SEND_TASK_INVALID_ATTACHMENTS",
                    i18nService.getMessage("email.send_task.invalid_attachments"), taskId);
        }
    }

    private static boolean attachmentItemsValid(JsonNode root) {
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String source = item.path("source").asText("").trim();
            if ("main".equalsIgnoreCase(source)) {
                if (item.path("fieldName").asText("").isBlank()) {
                    return false;
                }
            } else if ("sub".equalsIgnoreCase(source)) {
                if (item.path("fieldName").asText("").isBlank()
                        || item.path("bindingId").isMissingNode()
                        || item.path("bindingId").asText("").isBlank()) {
                    return false;
                }
            } else if ("lookup".equalsIgnoreCase(source)) {
                if (item.path("lookupField").asText("").isBlank()
                        || item.path("targetField").asText("").isBlank()) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private String extractCustomProperty(String block, String propertyName) {
        Pattern pattern = Pattern.compile(
                "name=\"" + Pattern.quote(propertyName) + "\"\\s+value=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(block);
        if (matcher.find()) {
            return matcher.group(1);
        }
        pattern = Pattern.compile(
                "value=\"([^\"]*)\"\\s+name=\"" + Pattern.quote(propertyName) + "\"");
        matcher = pattern.matcher(block);
        return matcher.find() ? matcher.group(1) : null;
    }

    public Map<String, Object> parseBpmnXml(String bpmnXml) {
        Map<String, Object> structure = new HashMap<>();

        // 提取节点
        List<Map<String, String>> nodes = new ArrayList<>();
        Pattern nodePattern = Pattern.compile("<bpmn:(\\w+)\\s+id=\"([^\"]+)\"[^>]*name=\"([^\"]*)?\"");
        Matcher nodeMatcher = nodePattern.matcher(bpmnXml);

        while (nodeMatcher.find()) {
            Map<String, String> node = new HashMap<>();
            node.put("type", nodeMatcher.group(1));
            node.put("id", nodeMatcher.group(2));
            node.put("name", nodeMatcher.group(3) != null ? nodeMatcher.group(3) : "");
            nodes.add(node);
        }

        // 提取连接
        List<Map<String, String>> flows = new ArrayList<>();
        Pattern flowPattern = Pattern.compile("<bpmn:sequenceFlow\\s+id=\"([^\"]+)\"\\s+sourceRef=\"([^\"]+)\"\\s+targetRef=\"([^\"]+)\"");
        Matcher flowMatcher = flowPattern.matcher(bpmnXml);

        while (flowMatcher.find()) {
            Map<String, String> flow = new HashMap<>();
            flow.put("id", flowMatcher.group(1));
            flow.put("source", flowMatcher.group(2));
            flow.put("target", flowMatcher.group(3));
            flows.add(flow);
        }

        structure.put("nodes", nodes);
        structure.put("flows", flows);

        return structure;
    }

    private List<String> extractNodeIds(String bpmnXml) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = FLOW_NODE_ID_PATTERN.matcher(bpmnXml);

        while (matcher.find()) {
            ids.add(matcher.group(2));
        }

        return ids;
    }

    private List<String> extractConnectedNodes(String bpmnXml) {
        Set<String> connected = new HashSet<>();
        Pattern pattern = Pattern.compile("(sourceRef|targetRef)=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(bpmnXml);

        while (matcher.find()) {
            connected.add(matcher.group(2));
        }

        return new ArrayList<>(connected);
    }

    private boolean isStartOrEndEvent(String bpmnXml, String nodeId) {
        String pattern = String.format("(startEvent|endEvent)[^>]*id=\"%s\"", Pattern.quote(nodeId));
        return Pattern.compile(pattern).matcher(bpmnXml).find();
    }

    public ValidationResult validateMultiInstance(String bpmnXml, Long functionUnitId) {
        ValidationResult result = new ValidationResult();

        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            result.addError("EMPTY_BPMN", "BPMN XML cannot be empty", null);
            return result;
        }
        MiAssignmentFormGuard assignmentFormGuard = new MiAssignmentFormGuard();

        // 查找所有多实例子流程节点
        Pattern subProcessPattern = Pattern.compile(
            "<bpmn:subProcess[^>]*id=\"([^\"]+)\"[^>]*>.*?<bpmn:multiInstanceLoopCharacteristics",
            Pattern.DOTALL
        );
        Matcher subProcessMatcher = subProcessPattern.matcher(bpmnXml);

        while (subProcessMatcher.find()) {
            String subProcessId = subProcessMatcher.group(1);

            // 提取该子流程的完整内容
            int startPos = subProcessMatcher.start();
            int endPos = findMatchingSubProcessEnd(bpmnXml, startPos);
            if (endPos == -1) {
                result.addError("INVALID_SUBPROCESS_STRUCTURE",
                    "Invalid subProcess structure for " + subProcessId, subProcessId);
                continue;
            }

            String subProcessXml = bpmnXml.substring(startPos, endPos);

            // 验证 1: collection 变量名格式合法（字母、数字、下划线）
            // 支持 BpmnXmlGenerator 子元素写法，以及 Flowable 常见的 multiInstanceLoopCharacteristics 属性写法
            Optional<String> collectionVarOpt = extractMultiInstanceCollectionVariable(subProcessXml);
            if (collectionVarOpt.isPresent()) {
                String collectionVar = collectionVarOpt.get();
                if (!collectionVar.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                    result.addError("INVALID_COLLECTION_VARIABLE",
                        "Collection variable name '" + collectionVar + "' is invalid. Must contain only letters, numbers, and underscores.",
                        subProcessId);
                }
            } else {
                result.addError("MISSING_COLLECTION_VARIABLE",
                    "Multi-instance subProcess is missing flowable:collection configuration",
                    subProcessId);
            }

            // 验证 2: 子流程内部至少包含一个 userTask
            Pattern userTaskPattern = Pattern.compile("<bpmn:userTask[^>]*id=\"([^\"]+)\"");
            Matcher userTaskMatcher = userTaskPattern.matcher(subProcessXml);

            if (!userTaskMatcher.find()) {
                result.addError("MISSING_USER_TASK",
                    "Multi-instance subProcess must contain at least one userTask",
                    subProcessId);
                continue; // 没有 userTask，后续验证无意义
            }

            // 提取 userTask 的扩展属性
            String userTaskId = userTaskMatcher.group(1);
            Map<String, String> userTaskProps = extractUserTaskProperties(subProcessXml, userTaskId);

            // 验证 3: subTableId 属于当前 FunctionUnit 且 table_type=SUB
            String subTableIdStr = userTaskProps.get("subTableId");
            if (subTableIdStr != null && !subTableIdStr.isEmpty()) {
                try {
                    Long subTableId = Long.parseLong(subTableIdStr);
                    Optional<TableDefinition> tableOpt = tableDefinitionRepository.findByIdWithFields(subTableId);

                    if (tableOpt.isEmpty()) {
                        // Fallback: lookup by subTableName within the same FU
                        String subTableName = userTaskProps.get("subTableName");
                        if (subTableName != null && !subTableName.isEmpty()) {
                            tableOpt = tableDefinitionRepository.findByFunctionUnitIdAndTableName(functionUnitId, subTableName);
                            // Reload with JOIN FETCH to avoid LazyInitializationException on fieldDefinitions
                            if (tableOpt.isPresent()) {
                                tableOpt = tableDefinitionRepository.findByIdWithFields(tableOpt.get().getId());
                            }
                        }
                    }
                    if (tableOpt.isEmpty()) {
                        result.addError("SUBTABLE_NOT_FOUND",
                            "SubTable with id " + subTableId + " not found",
                            subProcessId);
                    } else {
                        TableDefinition table = tableOpt.get();

                        // 验证归属
                        if (!table.getFunctionUnit().getId().equals(functionUnitId)) {
                            result.addError("SUBTABLE_WRONG_FUNCTION_UNIT",
                                "SubTable " + subTableId + " does not belong to the current FunctionUnit",
                                subProcessId);
                        }

                        // 验证 table_type
                        if (table.getTableType() != TableType.SUB) {
                            result.addError("INVALID_TABLE_TYPE",
                                "Table " + subTableId + " is not a SUB table (table_type=" + table.getTableType() + ")",
                                subProcessId);
                        }

                        // 验证 4: user/role/both 分别校验所需字段；buField 可选但配置后必须存在。
                        validateAssignmentFields(
                                userTaskProps, table, subTableId, subProcessId, userTaskId, result);
                        assignmentFormGuard.validate(
                                userTaskProps,
                                userTaskId,
                                subProcessId,
                                result);
                    }
                } catch (NumberFormatException e) {
                    result.addError("INVALID_SUBTABLE_ID",
                        "Invalid subTableId format: " + subTableIdStr,
                        subProcessId);
                }
            } else {
                result.addError("MISSING_SUBTABLE_ID",
                    "Multi-instance userTask is missing subTableId configuration",
                    userTaskId);
            }

            // 验证 5: formId（如配置）属于当前 FunctionUnit
            String formIdStr = userTaskProps.get("formId");
            if (formIdStr != null && !formIdStr.isEmpty()) {
                try {
                    Long formId = Long.parseLong(formIdStr);
                    Optional<FormDefinition> formOpt = formDefinitionRepository.findById(formId);

                    if (formOpt.isEmpty()) {
                        // Fallback: lookup by formName within the same FU
                        String formName = userTaskProps.get("formName");
                        if (formName != null && !formName.isEmpty()) {
                            formOpt = formDefinitionRepository.findByFunctionUnitIdAndFormName(functionUnitId, formName);
                            // Reload to ensure entity is managed in current session
                            if (formOpt.isPresent()) {
                                formOpt = formDefinitionRepository.findById(formOpt.get().getId());
                            }
                        }
                    }
                    if (formOpt.isEmpty()) {
                        result.addError("FORM_NOT_FOUND",
                            "Form with id " + formId + " not found",
                            userTaskId);
                    } else {
                        FormDefinition form = formOpt.get();
                        if (!form.getFunctionUnit().getId().equals(functionUnitId)) {
                            result.addError("FORM_WRONG_FUNCTION_UNIT",
                                "Form " + formId + " does not belong to the current FunctionUnit",
                                userTaskId);
                        }
                    }
                } catch (NumberFormatException e) {
                    result.addError("INVALID_FORM_ID",
                        "Invalid formId format: " + formIdStr,
                        userTaskId);
                }
            }
        }

        return result;
    }

    private void validateAssignmentFields(
            Map<String, String> properties,
            TableDefinition table,
            Long subTableId,
            String subProcessId,
            String userTaskId,
            ValidationResult result) {
        String mode = Optional.ofNullable(properties.get("assigneeMode"))
                .map(String::trim)
                .orElse("user");
        boolean requiresUser = "user".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
        boolean requiresRole = "role".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
        if (requiresUser) {
            validateRequiredField(
                    properties.get("assigneeField"), "ASSIGNEE", table, subTableId,
                    subProcessId, userTaskId, result);
        }
        if (requiresRole) {
            validateRequiredField(
                    properties.get("roleField"), "ROLE", table, subTableId,
                    subProcessId, userTaskId, result);
            validateOptionalBuField(properties.get("buField"), table, subTableId, subProcessId, result);
        }
    }

    private void validateRequiredField(
            String fieldName,
            String fieldType,
            TableDefinition table,
            Long subTableId,
            String subProcessId,
            String userTaskId,
            ValidationResult result) {
        if (fieldName == null || fieldName.isBlank()) {
            result.addError(
                    "MISSING_" + fieldType + "_FIELD",
                    "Multi-instance userTask is missing " + fieldType.toLowerCase(java.util.Locale.ROOT)
                            + "Field configuration",
                    userTaskId);
            return;
        }
        boolean exists = table.getFieldDefinitions().stream()
                .anyMatch(field -> fieldName.equals(field.getFieldName()));
        if (!exists) {
            result.addError(
                    fieldType + "_FIELD_NOT_FOUND",
                    fieldType.substring(0, 1) + fieldType.substring(1).toLowerCase(java.util.Locale.ROOT)
                            + "Field '" + fieldName + "' not found in SubTable " + subTableId,
                    subProcessId);
        }
    }

    private void validateOptionalBuField(
            String buField,
            TableDefinition table,
            Long subTableId,
            String subProcessId,
            ValidationResult result) {
        if (buField == null || buField.isBlank()) {
            return;
        }
        boolean exists = table.getFieldDefinitions().stream()
                .anyMatch(field -> buField.equals(field.getFieldName()));
        if (!exists) {
            result.addError(
                    "BU_FIELD_NOT_FOUND",
                    "BuField '" + buField + "' not found in SubTable " + subTableId,
                    subProcessId);
        }
    }

    public ValidationResult validateLastTaskAssigneeTopology(String bpmnXml) {
        return BpmnLastTaskAssigneeTopologyValidator.validate(bpmnXml);
    }

    /**
     * 从子流程 XML 中提取多实例集合变量名（与 BpmnXmlGenerator / Flowable 属性写法兼容）。
     */
    private Optional<String> extractMultiInstanceCollectionVariable(String subProcessXml) {
        Matcher elementMatcher = Pattern.compile("<flowable:collection>([^<]+)</flowable:collection>")
                .matcher(subProcessXml);
        if (elementMatcher.find()) {
            return Optional.of(elementMatcher.group(1).trim());
        }
        Matcher attrMatcher = Pattern.compile("\\sflowable:collection=\"([^\"]+)\"").matcher(subProcessXml);
        if (attrMatcher.find()) {
            return Optional.of(attrMatcher.group(1).trim());
        }
        return Optional.empty();
    }

    /**
     * 查找匹配的 subProcess 结束标签位置
     */
    private int findMatchingSubProcessEnd(String bpmnXml, int startPos) {
        int depth = 0;
        int pos = startPos;

        while (pos < bpmnXml.length()) {
            if (bpmnXml.startsWith("<bpmn:subProcess", pos)) {
                depth++;
                pos += 16;
            } else if (bpmnXml.startsWith("</bpmn:subProcess>", pos)) {
                depth--;
                if (depth == 0) {
                    return pos + 18; // 包含结束标签
                }
                pos += 18;
            } else {
                pos++;
            }
        }

        return -1; // 未找到匹配的结束标签
    }

    /**
     * 提取 userTask 的扩展属性
     */
    private Map<String, String> extractUserTaskProperties(String subProcessXml, String userTaskId) {
        Map<String, String> properties = new HashMap<>();

        // 查找该 userTask 的扩展属性部分
        Pattern userTaskBlockPattern = Pattern.compile(
            "<bpmn:userTask[^>]*id=\"" + Pattern.quote(userTaskId) + "\"[^>]*>.*?</bpmn:userTask>",
            Pattern.DOTALL
        );
        Matcher userTaskBlockMatcher = userTaskBlockPattern.matcher(subProcessXml);

        if (userTaskBlockMatcher.find()) {
            String userTaskBlock = userTaskBlockMatcher.group();

            // 提取 custom:property 元素
            Pattern propertyPattern = Pattern.compile(
                "<custom:property[^>]*name=\"([^\"]+)\"[^>]*value=\"([^\"]+)\"[^>]*/>"
            );
            Matcher propertyMatcher = propertyPattern.matcher(userTaskBlock);

            while (propertyMatcher.find()) {
                String name = propertyMatcher.group(1);
                String value = propertyMatcher.group(2);
                properties.put(name, value);
            }
        }

        return properties;
    }
}
