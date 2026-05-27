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
     * 模拟流程执行（含多实例 mock collection 自动生成）
     */
    Map<String, Object> simulate(Long functionUnitId, String bpmnXml, Map<String, Object> variables);

    /**
     * Debug lookup live probe.
     */
    Map<String, Object> debugLookupProbe(Long functionUnitId, Map<String, Object> request);

    /**
     * Debug action runner (dry run).
     */
    Map<String, Object> debugRunAction(Long functionUnitId, Map<String, Object> request);
    
    /**
     * 解析BPMN XML获取流程结构
     */
    Map<String, Object> parseBpmnXml(String bpmnXml);
    
    /**
     * 验证多实例子流程配置
     * @param bpmnXml BPMN XML 内容
     * @param functionUnitId 功能单元 ID（用于验证子表归属）
     * @return 验证结果
     */
    ValidationResult validateMultiInstance(String bpmnXml, Long functionUnitId);

    /**
     * 校验 LAST_TASK_ASSIGNEE 锚点与用户任务顺序流入线条数（必须恰好 1 条）。
     */
    ValidationResult validateLastTaskAssigneeTopology(String bpmnXml);
}
