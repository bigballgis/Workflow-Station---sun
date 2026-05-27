package com.developer.component.impl;

import com.developer.component.ProcessDesignComponent;
import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.util.BpmnLastTaskAssigneeTopologyValidator;
import com.developer.util.XmlEncodingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 流程设计组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessDesignComponentImpl implements ProcessDesignComponent {
    private static final Pattern FLOW_NODE_ID_PATTERN = Pattern.compile(
            "<bpmn:(startEvent|endEvent|userTask|serviceTask|scriptTask|manualTask|sendTask|receiveTask|"
                    + "businessRuleTask|task|subProcess|exclusiveGateway|parallelGateway|inclusiveGateway|"
                    + "eventBasedGateway|complexGateway|intermediateCatchEvent|intermediateThrowEvent|"
                    + "boundaryEvent|callActivity)\\b[^>]*\\bid=\"([^\"]+)\"",
            Pattern.DOTALL);
    
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    
    @Override
    @Transactional
    public ProcessDefinition save(Long functionUnitId, String bpmnXml) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        ValidationResult lastTaskTopo = validateLastTaskAssigneeTopology(bpmnXml);
        if (!lastTaskTopo.isValid()) {
            String detail = lastTaskTopo.getErrors().stream()
                    .map(ValidationResult.ValidationError::getMessage)
                    .collect(Collectors.joining("; "));
            throw new DeveloperBusinessException("LAST_TASK_ANCHOR_TOPOLOGY", detail);
        }

        ProcessDefinition processDefinition = processDefinitionRepository
                .findByFunctionUnitId(functionUnitId)
                .orElse(ProcessDefinition.builder()
                        .functionUnit(functionUnit)
                        .functionUnitVersionId(functionUnitId)
                        .build());
        
        // 使用Base64编码存储XML，避免特殊字符转义问题
        String encodedXml = XmlEncodingUtil.encode(bpmnXml);
        processDefinition.setBpmnXml(encodedXml);
        
        return processDefinitionRepository.save(processDefinition);
    }
    
    @Override
    @Transactional(readOnly = true)
    public ProcessDefinition getByFunctionUnitId(Long functionUnitId) {
        Optional<ProcessDefinition> optional = processDefinitionRepository.findByFunctionUnitId(functionUnitId);
        
        // 如果流程定义不存在，返回 null 而不是抛出异常
        // 这允许前端创建新的流程定义
        if (optional.isEmpty()) {
            log.debug("ProcessDefinition not found for functionUnitId={}, returning null", functionUnitId);
            return null;
        }
        
        ProcessDefinition processDefinition = optional.get();
        
        // 智能解码：兼容旧数据（未编码）和新数据（Base64编码）
        String decodedXml = XmlEncodingUtil.smartDecode(processDefinition.getBpmnXml());
        processDefinition.setBpmnXml(decodedXml);
        
        return processDefinition;
    }
    
    @Override
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

        return result;
    }
    
    @Override
    public Map<String, Object> simulate(String bpmnXml, Map<String, Object> variables) {
        Map<String, Object> result = new HashMap<>();
        
        // 解析流程结构
        Map<String, Object> processStructure = parseBpmnXml(bpmnXml);
        
        result.put("processStructure", processStructure);
        result.put("variables", variables);
        result.put("status", "SIMULATED");
        result.put("steps", new ArrayList<>());

        return result;
    }
    
    @Override
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
    
    @Override
    public ValidationResult validateMultiInstance(String bpmnXml, Long functionUnitId) {
        ValidationResult result = new ValidationResult();
        
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            result.addError("EMPTY_BPMN", "BPMN XML cannot be empty", null);
            return result;
        }
        
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
                        
                        // 验证 4: assigneeField 存在于子表的 FieldDefinition 列表中
                        String assigneeField = userTaskProps.get("assigneeField");
                        if (assigneeField != null && !assigneeField.isEmpty()) {
                            boolean fieldExists = table.getFieldDefinitions().stream()
                                .anyMatch(fd -> fd.getFieldName().equals(assigneeField));
                            
                            if (!fieldExists) {
                                result.addError("ASSIGNEE_FIELD_NOT_FOUND", 
                                    "AssigneeField '" + assigneeField + "' not found in SubTable " + subTableId, 
                                    subProcessId);
                            }
                        } else {
                            result.addError("MISSING_ASSIGNEE_FIELD", 
                                "Multi-instance userTask is missing assigneeField configuration", 
                                userTaskId);
                        }
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

    @Override
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
