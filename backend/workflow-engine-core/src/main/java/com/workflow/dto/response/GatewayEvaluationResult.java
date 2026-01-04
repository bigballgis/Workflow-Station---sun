package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 网关条件评估结果
 * 用于表示排他网关条件表达式的评估结果
 */
@Data
@Builder
public class GatewayEvaluationResult {
    
    /**
     * 网关ID
     */
    private String gatewayId;
    
    /**
     * 网关名称
     */
    private String gatewayName;
    
    /**
     * 网关类型
     */
    private String gatewayType;
    
    /**
     * 选中的流ID
     */
    private String selectedFlowId;
    
    /**
     * 选中的流名称
     */
    private String selectedFlowName;
    
    /**
     * 所有流的评估结果
     */
    private List<FlowEvaluation> flowEvaluations;
    
    /**
     * 评估时使用的变量
     */
    private Map<String, Object> variables;
    
    /**
     * 评估时间
     */
    private LocalDateTime evaluationTime;
    
    /**
     * 流评估信息
     */
    @Data
    @Builder
    public static class FlowEvaluation {
        
        /**
         * 流ID
         */
        private String flowId;
        
        /**
         * 流名称
         */
        private String flowName;
        
        /**
         * 条件表达式
         */
        private String conditionExpression;
        
        /**
         * 条件评估结果
         */
        private boolean conditionResult;
        
        /**
         * 评估消息
         */
        private String evaluationMessage;
    }
}