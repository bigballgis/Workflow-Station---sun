package com.developer.component;

import com.developer.dto.ValidationResult;
import com.developer.entity.ProcessDefinition;

import java.util.Map;

/**
 * 流程设计组件接口
 */
public interface ProcessDesignComponent {
    
    /**
     * 保存流程定义
     */
    ProcessDefinition save(Long functionUnitId, String bpmnXml);
    
    /**
     * 获取功能单元的流程定义
     */
    ProcessDefinition getByFunctionUnitId(Long functionUnitId);
    
    /**
     * 验证BPMN XML
     */
    ValidationResult validate(String bpmnXml);
    
    /**
     * 模拟流程执行
     */
    Map<String, Object> simulate(String bpmnXml, Map<String, Object> variables);
    
    /**
     * 解析BPMN XML获取流程结构
     */
    Map<String, Object> parseBpmnXml(String bpmnXml);
}
