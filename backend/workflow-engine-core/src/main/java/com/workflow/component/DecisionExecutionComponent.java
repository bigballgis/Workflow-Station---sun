package com.workflow.component;

import java.util.List;
import java.util.Map;

/**
 * 决策表执行组件接口
 * 负责运行时评估已部署的 DMN 决策表
 */
public interface DecisionExecutionComponent {

    /**
     * 评估决策表
     *
     * @param decisionKey 决策键
     * @param variables   输入变量映射
     * @return 匹配规则的输出条目列表
     */
    List<Map<String, Object>> evaluate(String decisionKey, Map<String, Object> variables);
}
