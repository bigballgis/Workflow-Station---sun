package com.developer.component.impl;

import com.developer.component.ProcessDesignComponent;
import com.developer.dto.ValidationResult;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.util.XmlEncodingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程设计组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessDesignComponentImpl implements ProcessDesignComponent {
    
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    
    @Override
    @Transactional
    public ProcessDefinition save(Long functionUnitId, String bpmnXml) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
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
            result.addError("EMPTY_BPMN", "BPMN XML不能为空", null);
            return result;
        }
        
        // 检查是否有开始事件
        if (!bpmnXml.contains("startEvent")) {
            result.addError("MISSING_START_EVENT", "流程缺少开始事件", null);
        }
        
        // 检查是否有结束事件
        if (!bpmnXml.contains("endEvent")) {
            result.addError("MISSING_END_EVENT", "流程缺少结束事件", null);
        }
        
        // 检查基本XML结构
        if (!bpmnXml.contains("<bpmn:process") && !bpmnXml.contains("<process")) {
            result.addError("INVALID_BPMN_STRUCTURE", "无效的BPMN结构", null);
        }
        
        // 检查孤立节点
        List<String> nodeIds = extractNodeIds(bpmnXml);
        List<String> connectedNodes = extractConnectedNodes(bpmnXml);
        
        for (String nodeId : nodeIds) {
            if (!connectedNodes.contains(nodeId) && !isStartOrEndEvent(bpmnXml, nodeId)) {
                result.addWarning("ORPHAN_NODE", "节点 " + nodeId + " 可能是孤立的", nodeId);
            }
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
        
        // TODO: 实现实际的流程模拟逻辑
        
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
        Pattern pattern = Pattern.compile("id=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(bpmnXml);
        
        while (matcher.find()) {
            ids.add(matcher.group(1));
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
        String pattern = String.format("(startEvent|endEvent)[^>]*id=\"%s\"", nodeId);
        return Pattern.compile(pattern).matcher(bpmnXml).find();
    }
}
