package com.developer.component;

import com.developer.dto.ValidationResult;
import com.developer.entity.ProcessDefinition;

import java.util.Map;

/**
 * 流程设计组件接口
 */
public interface ProcessDesignComponent {
    
    /**
     * 保存流程定义（不允许用空图覆盖已存的非空流程）
     */
    default ProcessDefinition save(Long functionUnitId, String bpmnXml) {
        return save(functionUnitId, bpmnXml, false);
    }

    /**
     * 保存流程定义。
     *
     * @param allowEmpty 为 true 时允许「空图覆盖已存非空流程」——仅当用户在设计器里显式确认清空后传入。
     *                   为 false 时该覆盖会抛 {@code EMPTY_PROCESS_OVERWRITE_BLOCKED}（自动保存误触护栏）。
     */
    ProcessDefinition save(Long functionUnitId, String bpmnXml, boolean allowEmpty);

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
