package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 负载均衡结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadBalancingResult {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 选中的节点ID
     */
    private String selectedNodeId;
    
    /**
     * 选中的节点主机
     */
    private String selectedNodeHost;
    
    /**
     * 选中的节点端口
     */
    private int selectedNodePort;
    
    /**
     * 选中节点的负载分数
     */
    private double loadScore;
    
    /**
     * 总节点数
     */
    private int totalNodes;
    
    /**
     * 消息
     */
    private String message;
}
